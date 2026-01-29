package com.droiddungeon.ui;

import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.enemies.Enemy;
import com.droiddungeon.grid.DungeonGenerator.Room;
import com.droiddungeon.grid.DungeonGenerator.RoomType;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.grid.BlockMaterial;

/**
 * Gameplay minimap shown in the HUD (not debug-only).
 */
public final class MinimapRenderer {
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final Rectangle lastBounds = new Rectangle();

    private final Color playerColor = new Color(0.98f, 0.86f, 0.3f, 1f);
    private final Color companionColor = new Color(0.35f, 0.85f, 0.95f, 1f);
    private final Color enemyColor = new Color(0.86f, 0.32f, 0.32f, 1f);
    private final Color deathColor = new Color(0.9f, 0.25f, 0.25f, 1f);
    private final Color markerColor = new Color(0.32f, 0.7f, 0.95f, 1f);
    private final Color fogColor = new Color(0f, 0f, 0f, 0.9f);

    public void render(Viewport viewport, Grid grid, Player player, float companionX, float companionY, List<Enemy> enemies, List<MapMarker> markers, MapMarker tracked, java.util.function.BiPredicate<Integer, Integer> explored) {
        if (grid == null || player == null) {
            return;
        }
        viewport.apply();
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);

        float viewportWidth = viewport.getWorldWidth();
        float viewportHeight = viewport.getWorldHeight();

        int radius = 18; // tiles shown from center in each direction
        int windowSize = radius * 2 + 1;

        float margin = 12f;
        float maxWidth = 300f;
        float maxHeight = 220f;
        float tile = Math.min(maxWidth / windowSize, maxHeight / windowSize);
        tile = Math.max(2f, tile);

        float pad = 6f;
        float mapWidth = tile * windowSize;
        float mapHeight = tile * windowSize;
        float originX = viewportWidth - margin - mapWidth;
        float originY = viewportHeight - margin - mapHeight;
        lastBounds.set(originX - pad, originY - pad, mapWidth + pad * 2f, mapHeight + pad * 2f);

        int minX = player.getGridX() - radius;
        int minY = player.getGridY() - radius;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.55f);
        shapeRenderer.rect(originX - pad, originY - pad, mapWidth + pad * 2f, mapHeight + pad * 2f);
        shapeRenderer.end();

        // Scissor to keep corners inside frame
        GL20 gl = Gdx.graphics.getGL20();
        gl.glEnable(GL20.GL_SCISSOR_TEST);
        int sx = Math.round((originX - pad) * Gdx.graphics.getBackBufferWidth() / viewportWidth);
        int sy = Math.round((originY - pad) * Gdx.graphics.getBackBufferHeight() / viewportHeight);
        int sw = Math.round((mapWidth + pad * 2f) * Gdx.graphics.getBackBufferWidth() / viewportWidth);
        int sh = Math.round((mapHeight + pad * 2f) * Gdx.graphics.getBackBufferHeight() / viewportHeight);
        gl.glScissor(sx, sy, sw, sh);

        // Tiles
        shapeRenderer.begin(ShapeType.Filled);
        for (int y = 0; y < windowSize; y++) {
            for (int x = 0; x < windowSize; x++) {
                int worldX = minX + x;
                int worldY = minY + y;
                float drawX = originX + x * tile;
                float drawY = originY + y * tile;
                if (explored != null && !explored.test(worldX, worldY)) {
                    shapeRenderer.setColor(fogColor);
                    shapeRenderer.rect(drawX, drawY, tile, tile);
                } else {
                    Color base = grid.getTileMaterial(worldX, worldY).darkColor();
                    shapeRenderer.setColor(base);
                    shapeRenderer.rect(drawX, drawY, tile, tile);
                    BlockMaterial block = grid.getBlockMaterial(worldX, worldY);
                    if (block != null) {
                        Color blockColor = block.floorMaterial().darkColor().cpy().mul(0.85f);
                        shapeRenderer.setColor(blockColor);
                        shapeRenderer.rect(drawX, drawY, tile, tile);
                    }
                }
            }
        }
        shapeRenderer.end();

        // Room corners + markers + enemies + entities
        shapeRenderer.begin(ShapeType.Filled);
        List<Room> rooms = grid.getRoomsInArea(minX, minY, minX + windowSize, minY + windowSize);
        float cornerThickness = Math.max(1.2f, tile * 0.18f);
        float segment = tile * 1.1f;
        for (Room room : rooms) {
            if (explored != null && !roomExplored(room, explored)) {
                continue;
            }
            Color tint = room.type == RoomType.SAFE ? new Color(0.30f, 0.55f, 0.95f, 1f) : new Color(0.82f, 0.25f, 0.25f, 1f);
            shapeRenderer.setColor(tint);
            float rx0 = originX + (room.x - minX) * tile - cornerThickness;
            float ry0 = originY + (room.y - minY) * tile - cornerThickness;
            float rx1 = rx0 + room.width * tile + cornerThickness * 2f;
            float ry1 = ry0 + room.height * tile + cornerThickness * 2f;
            // TL
            shapeRenderer.rect(rx0, ry1 - cornerThickness, segment, cornerThickness);
            shapeRenderer.rect(rx0, ry1 - segment, cornerThickness, segment);
            // TR
            shapeRenderer.rect(rx1 - segment, ry1 - cornerThickness, segment, cornerThickness);
            shapeRenderer.rect(rx1 - cornerThickness, ry1 - segment, cornerThickness, segment);
            // BL
            shapeRenderer.rect(rx0, ry0, segment, cornerThickness);
            shapeRenderer.rect(rx0, ry0, cornerThickness, segment);
            // BR
            shapeRenderer.rect(rx1 - segment, ry0, segment, cornerThickness);
            shapeRenderer.rect(rx1 - cornerThickness, ry0, cornerThickness, segment);
        }

        float playerX = originX + (radius + 0.5f) * tile;
        float playerY = originY + (radius + 0.5f) * tile;
        float markerSize = Math.max(3f, tile * 0.55f);
        float halfMarker = markerSize * 0.5f;
        shapeRenderer.setColor(playerColor);
        shapeRenderer.rect(playerX - halfMarker, playerY - halfMarker, markerSize, markerSize);

        float companionLocalX = companionX - player.getRenderX();
        float companionLocalY = companionY - player.getRenderY();
        if (Math.abs(companionLocalX) <= radius && Math.abs(companionLocalY) <= radius) {
            float companionWorldX = originX + (radius + companionLocalX + 0.5f) * tile;
            float companionWorldY = originY + (radius + companionLocalY + 0.5f) * tile;
            float companionSize = Math.max(3f, tile * 0.45f);
            float halfCompanion = companionSize * 0.5f;
            shapeRenderer.setColor(companionColor);
            shapeRenderer.rect(companionWorldX - halfCompanion, companionWorldY - halfCompanion, companionSize, companionSize);
        }

        if (markers != null) {
            for (MapMarker marker : markers) {
                float dx = marker.x() - player.getRenderX();
                float dy = marker.y() - player.getRenderY();
                if (Math.abs(dx) > radius || Math.abs(dy) > radius) {
                    continue;
                }
                if (explored != null && !explored.test((int) marker.x(), (int) marker.y())) {
                    continue;
                }
                float mx = originX + (radius + dx + 0.5f) * tile;
                float my = originY + (radius + dy + 0.5f) * tile;
                Color c = marker.type() == MapMarker.Type.DEATH ? deathColor : markerColor;
                shapeRenderer.setColor(c);
                shapeRenderer.rect(mx - 2f, my - 2f, 4f, 4f);
                if (marker.tracked()) {
                    shapeRenderer.setColor(Color.WHITE);
                    shapeRenderer.rect(mx - 3f, my - 0.8f, 6f, 1.6f);
                    shapeRenderer.rect(mx - 0.8f, my - 3f, 1.6f, 6f);
                }
            }
        }

        if (enemies != null) {
            for (Enemy enemy : enemies) {
                float dx = enemy.getRenderX() - player.getRenderX();
                float dy = enemy.getRenderY() - player.getRenderY();
                if (Math.abs(dx) > radius || Math.abs(dy) > radius) {
                    continue;
                }
                if (explored != null && !explored.test(enemy.getGridX(), enemy.getGridY())) {
                    continue;
                }
                float ex = originX + (radius + dx + 0.5f) * tile;
                float ey = originY + (radius + dy + 0.5f) * tile;
                float size = Math.max(3f, tile * 0.5f);
                float half = size * 0.5f;
                shapeRenderer.setColor(enemyColor);
                shapeRenderer.rect(ex - half, ey - half, size, size);
            }
        }
        shapeRenderer.end();

        shapeRenderer.begin(ShapeType.Line);
        shapeRenderer.setColor(0.4f, 0.4f, 0.44f, 1f);
        shapeRenderer.rect(originX - pad, originY - pad, mapWidth + pad * 2f, mapHeight + pad * 2f);
        shapeRenderer.end();

        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public Rectangle getLastBounds() {
        return lastBounds;
    }

    public void dispose() {
        shapeRenderer.dispose();
    }

    private boolean roomExplored(Room room, java.util.function.BiPredicate<Integer, Integer> explored) {
        if (explored == null) return true;
        return explored.test(room.x, room.y)
                || explored.test(room.x + room.width - 1, room.y)
                || explored.test(room.x, room.y + room.height - 1)
                || explored.test(room.x + room.width - 1, room.y + room.height - 1);
    }
}
