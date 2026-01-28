package com.droiddungeon.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.grid.DungeonGenerator.RoomType;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.render.RenderAssets;

/**
 * Renders debug text and minimap overlay.
 */
public final class DebugOverlay {
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final SpriteBatch spriteBatch = new SpriteBatch();
    private final BitmapFont font;
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private final Rectangle lastMinimapBounds = new Rectangle();

    public DebugOverlay() {
        font = RenderAssets.font(14);
        RenderAssets.whiteRegion();
    }

    public void render(Viewport viewport, Grid grid, Player player, float companionX, float companionY, String debugText, java.util.List<MapMarker> markers, MapMarker tracked) {
        viewport.apply();
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);

        renderMinimap(viewport, grid, player, companionX, companionY, markers, tracked);
        renderDebugBox(viewport, debugText);
    }

    private void renderMinimap(Viewport viewport, Grid grid, Player player, float companionX, float companionY, java.util.List<MapMarker> markers, MapMarker tracked) {
        if (grid == null || player == null) {
            return;
        }

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
        lastMinimapBounds.set(originX - pad, originY - pad, mapWidth + pad * 2f, mapHeight + pad * 2f);

        int minX = player.getGridX() - radius;
        int minY = player.getGridY() - radius;

        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.55f);
        shapeRenderer.rect(originX - pad, originY - pad, mapWidth + pad * 2f, mapHeight + pad * 2f);
        shapeRenderer.end();

        // Scissor mask to keep corners inside frame
        GL20 gl = Gdx.graphics.getGL20();
        gl.glEnable(GL20.GL_SCISSOR_TEST);
        int sx = Math.round((originX - pad) * Gdx.graphics.getBackBufferWidth() / viewportWidth);
        int sy = Math.round((originY - pad) * Gdx.graphics.getBackBufferHeight() / viewportHeight);
        int sw = Math.round((mapWidth + pad * 2f) * Gdx.graphics.getBackBufferWidth() / viewportWidth);
        int sh = Math.round((mapHeight + pad * 2f) * Gdx.graphics.getBackBufferHeight() / viewportHeight);
        gl.glScissor(sx, sy, sw, sh);

        shapeRenderer.begin(ShapeType.Filled);
        // Base tile fill inside scissor
        for (int y = 0; y < windowSize; y++) {
            for (int x = 0; x < windowSize; x++) {
                int worldX = minX + x;
                int worldY = minY + y;
                float drawX = originX + x * tile;
                float drawY = originY + y * tile;
                Color base = grid.getTileMaterial(worldX, worldY).darkColor();
                shapeRenderer.setColor(base);
                shapeRenderer.rect(drawX, drawY, tile, tile);
            }
        }
        shapeRenderer.end();

        // Room corners and markers on minimap
        shapeRenderer.begin(ShapeType.Filled);
        java.util.List<com.droiddungeon.grid.DungeonGenerator.Room> rooms = grid.getRoomsInArea(minX, minY, minX + windowSize, minY + windowSize);
        float cornerThickness = Math.max(1.2f, tile * 0.18f);
        float segment = tile * 1.1f;
        for (com.droiddungeon.grid.DungeonGenerator.Room room : rooms) {
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
        shapeRenderer.setColor(0.98f, 0.86f, 0.3f, 1f);
        shapeRenderer.rect(playerX - halfMarker, playerY - halfMarker, markerSize, markerSize);

        float companionLocalX = companionX - player.getRenderX();
        float companionLocalY = companionY - player.getRenderY();
        if (Math.abs(companionLocalX) <= radius && Math.abs(companionLocalY) <= radius) {
            float companionWorldX = originX + (radius + companionLocalX + 0.5f) * tile;
            float companionWorldY = originY + (radius + companionLocalY + 0.5f) * tile;
            float companionSize = Math.max(3f, tile * 0.45f);
            float halfCompanion = companionSize * 0.5f;
            shapeRenderer.setColor(0.35f, 0.85f, 0.95f, 1f);
            shapeRenderer.rect(companionWorldX - halfCompanion, companionWorldY - halfCompanion, companionSize, companionSize);
        }
        // Markers
        if (markers != null) {
            for (MapMarker marker : markers) {
                float dx = marker.x() - player.getRenderX();
                float dy = marker.y() - player.getRenderY();
                if (Math.abs(dx) > radius || Math.abs(dy) > radius) {
                    continue;
                }
                float mx = originX + (radius + dx + 0.5f) * tile;
                float my = originY + (radius + dy + 0.5f) * tile;
                Color c = marker.type() == MapMarker.Type.DEATH ? new Color(0.9f, 0.25f, 0.25f, 1f) : new Color(0.32f, 0.7f, 0.95f, 1f);
                shapeRenderer.setColor(c);
                shapeRenderer.rect(mx - 2f, my - 2f, 4f, 4f);
                if (marker.tracked()) {
                    shapeRenderer.setColor(Color.WHITE);
                    shapeRenderer.rect(mx - 3f, my - 0.8f, 6f, 1.6f);
                    shapeRenderer.rect(mx - 0.8f, my - 3f, 1.6f, 6f);
                }
            }
        }
        shapeRenderer.end();

        shapeRenderer.begin(ShapeType.Line);
        shapeRenderer.setColor(0.4f, 0.4f, 0.44f, 1f);
        shapeRenderer.rect(originX - pad, originY - pad, mapWidth + pad * 2f, mapHeight + pad * 2f);
        shapeRenderer.end();

        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
    }

    public Rectangle getLastMinimapBounds() {
        return lastMinimapBounds;
    }

    private void renderDebugBox(Viewport viewport, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        glyphLayout.setText(font, text);

        float paddingX = 8f;
        float paddingY = 6f;
        float margin = 10f;
        float boxWidth = glyphLayout.width + paddingX * 2f;
        float boxHeight = glyphLayout.height + paddingY * 2f;

        float x = margin;
        float y = viewport.getWorldHeight() - margin - boxHeight;

        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.7f);
        shapeRenderer.rect(x, y, boxWidth, boxHeight);
        shapeRenderer.setColor(0.35f, 0.35f, 0.4f, 0.9f);
        shapeRenderer.rect(x, y, boxWidth, 2f);
        shapeRenderer.rect(x, y, 2f, boxHeight);
        shapeRenderer.rect(x + boxWidth - 2f, y, 2f, boxHeight);
        shapeRenderer.rect(x, y + boxHeight - 2f, boxWidth, 2f);
        shapeRenderer.end();

        spriteBatch.begin();
        font.setColor(Color.WHITE);
        font.draw(spriteBatch, glyphLayout, x + paddingX, y + paddingY + glyphLayout.height);
        spriteBatch.end();

        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
    }

    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
    }
}
