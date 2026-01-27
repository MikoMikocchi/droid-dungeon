package com.droiddungeon.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.droiddungeon.grid.DungeonGenerator.RoomType;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.render.RenderAssets;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Renders debug text and minimap overlay.
 */
public final class DebugOverlay {
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final SpriteBatch spriteBatch = new SpriteBatch();
    private final BitmapFont font;
    private final GlyphLayout glyphLayout = new GlyphLayout();

    public DebugOverlay() {
        font = RenderAssets.font(14);
        RenderAssets.whiteRegion();
    }

    public void render(Viewport viewport, Grid grid, Player player, float companionX, float companionY, String debugText) {
        viewport.apply();
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);

        renderMinimap(viewport, grid, player, companionX, companionY);
        renderDebugBox(viewport, debugText);
    }

    private void renderMinimap(Viewport viewport, Grid grid, Player player, float companionX, float companionY) {
        if (grid == null || player == null) {
            return;
        }

        float viewportWidth = viewport.getWorldWidth();
        float viewportHeight = viewport.getWorldHeight();

        int radius = 18; // tiles shown from center in each direction
        int windowSize = radius * 2 + 1;

        float margin = 12f;
        float maxWidth = 240f;
        float maxHeight = 170f;
        float tile = Math.min(maxWidth / windowSize, maxHeight / windowSize);
        tile = Math.max(2f, tile);

        float mapWidth = tile * windowSize;
        float mapHeight = tile * windowSize;
        float originX = viewportWidth - margin - mapWidth;
        float originY = viewportHeight - margin - mapHeight;
        float pad = 6f;

        int minX = player.getGridX() - radius;
        int minY = player.getGridY() - radius;

        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.55f);
        shapeRenderer.rect(originX - pad, originY - pad, mapWidth + pad * 2f, mapHeight + pad * 2f);

        for (int y = 0; y < windowSize; y++) {
            for (int x = 0; x < windowSize; x++) {
                int worldX = minX + x;
                int worldY = minY + y;
                float drawX = originX + x * tile;
                float drawY = originY + y * tile;
                Color base = grid.getTileMaterial(worldX, worldY).darkColor();
                com.droiddungeon.grid.DungeonGenerator.RoomType roomType = grid.getRoomType(worldX, worldY);
                Color tint = colorForRoom(base, roomType);
                shapeRenderer.setColor(tint);
                shapeRenderer.rect(drawX, drawY, tile, tile);
            }
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
        shapeRenderer.end();

        shapeRenderer.begin(ShapeType.Line);
        shapeRenderer.setColor(0.4f, 0.4f, 0.44f, 1f);
        shapeRenderer.rect(originX - pad, originY - pad, mapWidth + pad * 2f, mapHeight + pad * 2f);
        shapeRenderer.end();

        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
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

    private Color colorForRoom(Color base, RoomType roomType) {
        if (roomType == RoomType.SAFE) {
            return new Color(0.32f, 0.55f, 0.95f, 0.95f);
        }
        if (roomType == RoomType.DANGER) {
            return new Color(0.82f, 0.26f, 0.26f, 0.95f);
        }
        return new Color(base.r * 0.75f + 0.18f, base.g * 0.75f + 0.18f, base.b * 0.75f + 0.18f, 0.95f);
    }
}
