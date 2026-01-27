package com.droiddungeon.grid;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Infinite, chunk-backed tile field. Chunks are generated lazily via {@link DungeonGenerator.ChunkGenerator}
 * and cached in memory. Coordinates are world-space tile indices (can be negative).
 */
public final class Grid {
    private final float tileSize;
    private final DungeonGenerator.ChunkGenerator chunkGenerator;
    private final Map<Long, DungeonGenerator.Chunk> chunks = new HashMap<>();

    // Bounds of generated tiles (inclusive) for debug visualisation.
    private int minGeneratedX = 0;
    private int maxGeneratedX = 0;
    private int minGeneratedY = 0;
    private int maxGeneratedY = 0;

    public Grid(float tileSize, DungeonGenerator.ChunkGenerator chunkGenerator) {
        if (tileSize <= 0f) {
            throw new IllegalArgumentException("tileSize must be positive");
        }
        this.tileSize = tileSize;
        this.chunkGenerator = Objects.requireNonNull(chunkGenerator, "chunkGenerator");
    }

    /**
     * Ensures a chunk is generated and cached.
     */
    public DungeonGenerator.Chunk ensureChunk(int chunkX, int chunkY) {
        long key = key(chunkX, chunkY);
        DungeonGenerator.Chunk chunk = chunks.get(key);
        if (chunk != null) {
            return chunk;
        }
        DungeonGenerator.Chunk generated = chunkGenerator.generate(chunkX, chunkY);
        chunks.put(key, generated);
        updateBounds(generated);
        return generated;
    }

    private void updateBounds(DungeonGenerator.Chunk chunk) {
        int originX = chunk.originX();
        int originY = chunk.originY();
        int endX = originX + chunkGenerator.chunkSize() - 1;
        int endY = originY + chunkGenerator.chunkSize() - 1;
        if (chunks.size() == 1) {
            minGeneratedX = originX;
            maxGeneratedX = endX;
            minGeneratedY = originY;
            maxGeneratedY = endY;
        } else {
            minGeneratedX = Math.min(minGeneratedX, originX);
            maxGeneratedX = Math.max(maxGeneratedX, endX);
            minGeneratedY = Math.min(minGeneratedY, originY);
            maxGeneratedY = Math.max(maxGeneratedY, endY);
        }
    }

    private long key(int chunkX, int chunkY) {
        return ((long) chunkX << 32) ^ (chunkY & 0xffffffffL);
    }

    private DungeonGenerator.TileCell cellAt(int x, int y) {
        int chunkX = Math.floorDiv(x, chunkGenerator.chunkSize());
        int chunkY = Math.floorDiv(y, chunkGenerator.chunkSize());
        DungeonGenerator.Chunk chunk = ensureChunk(chunkX, chunkY);
        return chunk.cellAt(x, y);
    }

    public TileMaterial getTileMaterial(int x, int y) {
        return cellAt(x, y).material;
    }

    public DungeonGenerator.RoomType getRoomType(int x, int y) {
        return cellAt(x, y).roomType;
    }

    public boolean isWalkable(int x, int y) {
        return getTileMaterial(x, y).isWalkable();
    }

    /**
     * Infinite world -> always true.
     */
    public boolean isInside(int x, int y) {
        return true;
    }

    public float getTileSize() {
        return tileSize;
    }

    public int getChunkSize() {
        return chunkGenerator.chunkSize();
    }

    public int getMinGeneratedX() {
        return minGeneratedX;
    }

    public int getMaxGeneratedX() {
        return maxGeneratedX;
    }

    public int getMinGeneratedY() {
        return minGeneratedY;
    }

    public int getMaxGeneratedY() {
        return maxGeneratedY;
    }
}

