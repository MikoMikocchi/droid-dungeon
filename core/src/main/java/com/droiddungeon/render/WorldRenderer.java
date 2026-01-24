package com.droiddungeon.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.items.GroundItem;
import com.droiddungeon.items.ItemDefinition;
import com.droiddungeon.items.ItemRegistry;

import java.util.List;

public final class WorldRenderer {
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final SpriteBatch spriteBatch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout glyphLayout = new GlyphLayout();

    public WorldRenderer() {
        font.getData().setScale(0.8f);
    }

    public void render(Stage stage, Grid grid, Player player, List<GroundItem> groundItems, ItemRegistry itemRegistry) {
        stage.getViewport().apply();
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        spriteBatch.setProjectionMatrix(stage.getCamera().combined);

        float gridOriginX = (stage.getViewport().getWorldWidth() - grid.getWorldWidth()) * 0.5f;
        float gridOriginY = (stage.getViewport().getWorldHeight() - grid.getWorldHeight()) * 0.5f;
        float tileSize = grid.getTileSize();

        renderTileFill(grid, gridOriginX, gridOriginY, tileSize);
        renderGridLines(grid, gridOriginX, gridOriginY, tileSize);
        renderGroundItems(groundItems, itemRegistry, gridOriginX, gridOriginY, tileSize);
        renderPlayer(player, gridOriginX, gridOriginY, tileSize);
    }

    private void renderTileFill(Grid grid, float gridOriginX, float gridOriginY, float tileSize) {
        shapeRenderer.begin(ShapeType.Filled);
        for (int y = 0; y < grid.getRows(); y++) {
            for (int x = 0; x < grid.getColumns(); x++) {
                if (((x + y) & 1) == 0) {
                    shapeRenderer.setColor(0.06f, 0.06f, 0.07f, 1f);
                } else {
                    shapeRenderer.setColor(0.04f, 0.04f, 0.05f, 1f);
                }
                shapeRenderer.rect(
                        gridOriginX + x * tileSize,
                        gridOriginY + y * tileSize,
                        tileSize,
                        tileSize
                );
            }
        }
        shapeRenderer.end();
    }

    private void renderGridLines(Grid grid, float gridOriginX, float gridOriginY, float tileSize) {
        shapeRenderer.begin(ShapeType.Line);
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 1f);

        for (int x = 0; x <= grid.getColumns(); x++) {
            float worldX = gridOriginX + x * tileSize;
            shapeRenderer.line(worldX, gridOriginY, worldX, gridOriginY + grid.getWorldHeight());
        }
        for (int y = 0; y <= grid.getRows(); y++) {
            float worldY = gridOriginY + y * tileSize;
            shapeRenderer.line(gridOriginX, worldY, gridOriginX + grid.getWorldWidth(), worldY);
        }
        shapeRenderer.end();
    }

    private void renderGroundItems(
            List<GroundItem> groundItems,
            ItemRegistry itemRegistry,
            float gridOriginX,
            float gridOriginY,
            float tileSize
    ) {
        if (groundItems == null || groundItems.isEmpty()) {
            return;
        }
        spriteBatch.begin();
        for (GroundItem groundItem : groundItems) {
            ItemDefinition def = itemRegistry.get(groundItem.getStack().itemId());
            if (def == null) {
                continue;
            }
            TextureRegion icon = def.icon();
            float drawSize = tileSize * 0.68f;
            float drawX = gridOriginX + groundItem.getGridX() * tileSize + (tileSize - drawSize) * 0.5f;
            float drawY = gridOriginY + groundItem.getGridY() * tileSize + (tileSize - drawSize) * 0.5f;
            spriteBatch.draw(icon, drawX, drawY, drawSize, drawSize);

            if (groundItem.getStack().count() > 1) {
                String countText = Integer.toString(groundItem.getStack().count());
                glyphLayout.setText(font, countText);
                float textX = drawX + drawSize - glyphLayout.width - 3f;
                float textY = drawY + glyphLayout.height + 2f;
                font.draw(spriteBatch, glyphLayout, textX, textY);
            }
        }
        spriteBatch.end();
    }

    private void renderPlayer(Player player, float gridOriginX, float gridOriginY, float tileSize) {
        float centerX = gridOriginX + (player.getRenderX() + 0.5f) * tileSize;
        float centerY = gridOriginY + (player.getRenderY() + 0.5f) * tileSize;
        float radius = tileSize * 0.42f;

        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0.15f, 0.75f, 1f, 1f);
        shapeRenderer.circle(centerX, centerY, radius, MathUtils.clamp((int) (radius * 2.5f), 16, 64));
        shapeRenderer.end();
    }

    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
        font.dispose();
    }
}
