package com.droiddungeon.render.lighting;

import com.droiddungeon.grid.DungeonGenerator.Room;
import com.droiddungeon.grid.Grid;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Manages light sources in the game world.
 * Handles automatic light placement in rooms, torch positioning on walls, etc.
 */
public class LightingSystem {
    private final LightRenderer renderer;
    private final float tileSize;
    private final Random random;

    // Light generation settings
    private float torchSpacing = 6f;  // Minimum tiles between torches
    private float roomLightDensity = 0.15f;  // Chance per valid position
    private boolean autoPlaceLights = true;

    // Track which chunks have had lights generated
    private final java.util.Set<Long> processedChunks = new java.util.HashSet<>();

    public LightingSystem(float tileSize, long worldSeed) {
        this.renderer = new LightRenderer();
        this.tileSize = tileSize;
        this.random = new Random(worldSeed ^ 0x4C494748L);
    }

    /**
     * Update the lighting system.
     * @param delta time since last frame
     * @param playerX player world X
     * @param playerY player world Y
     */
    public void update(float delta, float playerX, float playerY) {
        // Update player light position
        renderer.setPlayerLight(playerX, playerY, tileSize);

        // Update light animations
        renderer.update(delta);
    }

    /**
     * Generate lights for rooms in the visible area.
     */
    public void generateLightsForArea(Grid grid, int minX, int minY, int maxX, int maxY) {
        if (!autoPlaceLights) return;

        List<Room> rooms = grid.getRoomsInArea(minX, minY, maxX, maxY);

        for (Room room : rooms) {
            // Check if we've already processed this room's chunk
            long chunkKey = getChunkKey(room.x, room.y);
            if (processedChunks.contains(chunkKey)) {
                continue;
            }
            processedChunks.add(chunkKey);

            // Generate lights for this room
            generateRoomLights(grid, room);
        }
    }

    private long getChunkKey(int x, int y) {
        int chunkX = x / 32;  // Approximate chunk coordinate
        int chunkY = y / 32;
        return ((long) chunkX << 32) | (chunkY & 0xFFFFFFFFL);
    }

    /**
     * Generate appropriate lights for a room based on its type.
     */
    private void generateRoomLights(Grid grid, Room room) {
        // Seed random for reproducible light placement
        random.setSeed(room.x * 73856093L ^ room.y * 19349663L);

        switch (room.type) {
            case SAFE:
                // Safe rooms get more lighting
                generateSafeRoomLights(grid, room);
                break;
            case DANGER:
                // Danger rooms are darker with fewer lights
                generateDangerRoomLights(grid, room);
                break;
            default:
                // Default lighting
                generateDefaultLights(grid, room);
                break;
        }
    }

    /**
     * Generate lights for safe rooms - well lit with lanterns.
     */
    private void generateSafeRoomLights(Grid grid, Room room) {
        // Place a central light source
        float centerX = (room.x + room.width * 0.5f) * tileSize;
        float centerY = (room.y + room.height * 0.5f) * tileSize;

        Light centerLight = LightType.LANTERN.createLight(centerX, centerY, tileSize);
        centerLight.setRadius(centerLight.getRadius() * 1.3f);  // Larger for safe rooms
        renderer.addLight(centerLight);

        // Add wall torches if room is large enough
        if (room.width >= 6 && room.height >= 6) {
            addWallTorches(grid, room, 0.4f);  // 40% coverage
        }
    }

    /**
     * Generate lights for danger rooms - sparse, moody lighting.
     */
    private void generateDangerRoomLights(Grid grid, Room room) {
        // Fewer lights in danger rooms
        float centerX = (room.x + room.width * 0.5f) * tileSize;
        float centerY = (room.y + room.height * 0.5f) * tileSize;

        // Small chance for a central fire
        if (random.nextFloat() < 0.25f && room.width >= 5 && room.height >= 5) {
            Light fire = LightType.CAMPFIRE.createLight(centerX, centerY, tileSize);
            fire.setRadius(fire.getRadius() * 0.8f);
            renderer.addLight(fire);
        }

        // Sparse wall torches
        if (room.width >= 5 || room.height >= 5) {
            addWallTorches(grid, room, 0.15f);  // 15% coverage
        }
    }

    /**
     * Generate default lighting.
     */
    private void generateDefaultLights(Grid grid, Room room) {
        // Simple torch placement
        if (room.width >= 4 && room.height >= 4) {
            addWallTorches(grid, room, 0.25f);
        }
    }

    /**
     * Add torches along walls of a room.
     * @param coverage 0-1 how many potential positions get torches
     */
    private void addWallTorches(Grid grid, Room room, float coverage) {
        List<int[]> wallPositions = findWallAdjacentPositions(grid, room);

        // Shuffle for varied placement
        java.util.Collections.shuffle(wallPositions, random);

        int torchCount = Math.max(1, (int) (wallPositions.size() * coverage));
        int placed = 0;

        List<float[]> placedPositions = new ArrayList<>();

        for (int[] pos : wallPositions) {
            if (placed >= torchCount) break;

            float worldX = (pos[0] + 0.5f) * tileSize;
            float worldY = (pos[1] + 0.5f) * tileSize;

            // Check spacing from other torches
            boolean tooClose = false;
            for (float[] existing : placedPositions) {
                float dx = worldX - existing[0];
                float dy = worldY - existing[1];
                if (dx * dx + dy * dy < torchSpacing * torchSpacing * tileSize * tileSize) {
                    tooClose = true;
                    break;
                }
            }

            if (!tooClose) {
                Light torch = LightType.TORCH.createLight(worldX, worldY, tileSize);
                // Slight random variation
                torch.setFlickerSpeed(torch.getFlickerSpeed() * (0.8f + random.nextFloat() * 0.4f));
                torch.setIntensity(torch.getIntensity() * (0.85f + random.nextFloat() * 0.15f));
                renderer.addLight(torch);

                placedPositions.add(new float[]{worldX, worldY});
                placed++;
            }
        }
    }

    /**
     * Find floor positions adjacent to walls (good spots for torches).
     */
    private List<int[]> findWallAdjacentPositions(Grid grid, Room room) {
        List<int[]> positions = new ArrayList<>();

        // Check positions along edges of room interior
        for (int x = room.x + 1; x < room.x + room.width - 1; x++) {
            for (int y = room.y + 1; y < room.y + room.height - 1; y++) {
                if (!grid.hasBlock(x, y)) {
                    // Check if adjacent to a wall
                    if (grid.hasBlock(x - 1, y) || grid.hasBlock(x + 1, y) ||
                        grid.hasBlock(x, y - 1) || grid.hasBlock(x, y + 1)) {
                        positions.add(new int[]{x, y});
                    }
                }
            }
        }

        return positions;
    }

    /**
     * Manually add a light at a specific position.
     */
    public Light addLight(LightType type, float worldX, float worldY) {
        Light light = type.createLight(worldX, worldY, tileSize);
        renderer.addLight(light);
        return light;
    }

    /**
     * Add a custom light.
     */
    public void addLight(Light light) {
        renderer.addLight(light);
    }

    /**
     * Remove a light.
     */
    public void removeLight(Light light) {
        renderer.removeLight(light);
    }

    /**
     * Get the renderer for direct access.
     */
    public LightRenderer getRenderer() {
        return renderer;
    }

    /**
     * Reset processed chunks (for world regeneration).
     */
    public void resetGeneratedLights() {
        processedChunks.clear();
        renderer.clearLights();
    }

    // Configuration

    public void setTorchSpacing(float tiles) {
        this.torchSpacing = Math.max(2f, tiles);
    }

    public void setRoomLightDensity(float density) {
        this.roomLightDensity = Math.max(0f, Math.min(1f, density));
    }

    public void setAutoPlaceLights(boolean auto) {
        this.autoPlaceLights = auto;
    }

    public void dispose() {
        renderer.dispose();
        processedChunks.clear();
    }
}
