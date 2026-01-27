package com.droiddungeon.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.scenes.scene2d.Stage;
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
    private final TextureRegion whiteRegion;

    public DebugOverlay() {
        font = RenderAssets.font(14);
        whiteRegion = RenderAssets.whiteRegion();
    }

    public void render(Stage stage, Grid grid, Player player, float companionX, float companionY, String debugText) {
        stage.getViewport().apply();
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        spriteBatch.setProjectionMatrix(stage.getCamera().combined);

        renderMinimap(stage, grid, player, companionX, companionY);
        renderDebugBox(stage, debugText);
    }

    private void renderMinimap(Stage stage, Grid grid, Player player, float companionX, float companionY) {
        if (grid == null || player == null) {
            return;
        }

        float viewportWidth = stage.getViewport().getWorldWidth();
        float viewportHeight = stage.getViewport().getWorldHeight();

        float margin = 12f;
        float maxWidth = 240f;
        float maxHeight = 170f;
        float tile = Math.min(maxWidth / grid.getColumns(), maxHeight / grid.getRows());
        tile = Math.max(2f, tile);

        float mapWidth = tile * grid.getColumns();
        float mapHeight = tile * grid.getRows();
        float originX = viewportWidth - margin - mapWidth;
        float originY = viewportHeight - margin - mapHeight;
        float pad = 6f;

        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.55f);
        shapeRenderer.rect(originX - pad, originY - pad, mapWidth + pad * 2f, mapHeight + pad * 2f);

        for (int y = 0; y < grid.getRows(); y++) {
            for (int x = 0; x < grid.getColumns(); x++) {
                float drawX = originX + x * tile;
                float drawY = originY + y * tile;
                Color color = grid.getTileMaterial(x, y).darkColor();
                float r = 0.18f + color.r * 0.75f;
                float g = 0.18f + color.g * 0.75f;
                float b = 0.18f + color.b * 0.75f;
                shapeRenderer.setColor(r, g, b, 0.95f);
                shapeRenderer.rect(drawX, drawY, tile, tile);
            }
        }

        float playerX = originX + (player.getRenderX() + 0.5f) * tile;
        float playerY = originY + (player.getRenderY() + 0.5f) * tile;
        float markerSize = Math.max(3f, tile * 0.55f);
        float halfMarker = markerSize * 0.5f;
        shapeRenderer.setColor(0.98f, 0.86f, 0.3f, 1f);
        shapeRenderer.rect(playerX - halfMarker, playerY - halfMarker, markerSize, markerSize);

        float companionWorldX = originX + (companionX + 0.5f) * tile;
        float companionWorldY = originY + (companionY + 0.5f) * tile;
        float companionSize = Math.max(3f, tile * 0.45f);
        float halfCompanion = companionSize * 0.5f;
        shapeRenderer.setColor(0.35f, 0.85f, 0.95f, 1f);
        shapeRenderer.rect(companionWorldX - halfCompanion, companionWorldY - halfCompanion, companionSize, companionSize);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeType.Line);
        shapeRenderer.setColor(0.4f, 0.4f, 0.44f, 1f);
        shapeRenderer.rect(originX - pad, originY - pad, mapWidth + pad * 2f, mapHeight + pad * 2f);
        shapeRenderer.end();

        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
    }

    private void renderDebugBox(Stage stage, String text) {
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
        float y = stage.getViewport().getWorldHeight() - margin - boxHeight;

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
