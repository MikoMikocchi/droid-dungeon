package com.droiddungeon.render;

import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.grid.DungeonGenerator.RoomType;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.grid.TileMaterial;
import com.droiddungeon.items.GroundItem;
import com.droiddungeon.items.ItemDefinition;
import com.droiddungeon.items.ItemRegistry;

public final class WorldRenderer {
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final SpriteBatch spriteBatch = new SpriteBatch();
    private final BitmapFont font;
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private final TextureRegion whiteRegion;
    private final TextureRegion playerRegion;
    private final TextureRegion doroRegion;
    private final Color tempColor = new Color();
    private static final Color SAFE_TINT = new Color(0.30f, 0.55f, 0.95f, 1f);
    private static final Color DANGER_TINT = new Color(0.82f, 0.25f, 0.25f, 1f);

    public WorldRenderer() {
        font = RenderAssets.font(13);
        whiteRegion = RenderAssets.whiteRegion();
        playerRegion = RenderAssets.playerRegion();
        doroRegion = RenderAssets.doroRegion();
    }

    public void render(
            Viewport viewport,
            float gridOriginX,
            float gridOriginY,
            Grid grid,
            Player player,
            List<GroundItem> groundItems,
            ItemRegistry itemRegistry,
            float companionX,
            float companionY
    ) {
        viewport.apply(false);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);

        float tileSize = grid.getTileSize();
        VisibleWindow visible = VisibleWindow.from(viewport, tileSize);

        renderTileFill(grid, tileSize, visible);
        renderGridLines(tileSize, visible);

        spriteBatch.begin();
        spriteBatch.setColor(Color.WHITE);
        renderGroundItems(groundItems, itemRegistry, tileSize, gridOriginX, gridOriginY);
        renderCompanionDoro(gridOriginX, gridOriginY, tileSize, companionX, companionY);
        renderPlayer(player, gridOriginX, gridOriginY, tileSize);
        spriteBatch.end();
    }

    private void renderCompanionDoro(float gridOriginX, float gridOriginY, float tileSize, float companionX, float companionY) {
        float centerX = gridOriginX + (companionX + 0.5f) * tileSize;
        float centerY = gridOriginY + (companionY + 0.5f) * tileSize;
        float drawSize = tileSize * 0.9f;
        float drawX = centerX - drawSize * 0.5f;
        float drawY = centerY - drawSize * 0.5f;

        spriteBatch.draw(doroRegion, drawX, drawY, drawSize, drawSize);
    }

    private void renderTileFill(Grid grid, float tileSize, VisibleWindow visible) {
        shapeRenderer.begin(ShapeType.Filled);
        for (int y = visible.minTileY; y <= visible.maxTileY; y++) {
            for (int x = visible.minTileX; x <= visible.maxTileX; x++) {
                TileMaterial material = grid.getTileMaterial(x, y);
                com.droiddungeon.grid.DungeonGenerator.RoomType roomType = grid.getRoomType(x, y);
                shapeRenderer.setColor(colorFor(material, roomType, x + y));
                shapeRenderer.rect(
                        x * tileSize,
                        y * tileSize,
                        tileSize,
                        tileSize
                );
            }
        }
        shapeRenderer.end();
    }

    private void renderGridLines(float tileSize, VisibleWindow visible) {
        shapeRenderer.begin(ShapeType.Line);
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.65f);

        for (int x = visible.minTileX; x <= visible.maxTileX + 1; x++) {
            float worldX = x * tileSize;
            shapeRenderer.line(worldX, visible.minTileY * tileSize, worldX, (visible.maxTileY + 1) * tileSize);
        }
        for (int y = visible.minTileY; y <= visible.maxTileY + 1; y++) {
            float worldY = y * tileSize;
            shapeRenderer.line(visible.minTileX * tileSize, worldY, (visible.maxTileX + 1) * tileSize, worldY);
        }
        shapeRenderer.end();
    }

    private void renderGroundItems(
            List<GroundItem> groundItems,
            ItemRegistry itemRegistry,
            float tileSize,
            float gridOriginX,
            float gridOriginY
    ) {
        if (groundItems == null || groundItems.isEmpty()) {
            return;
        }
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
                float textX = drawX + drawSize - glyphLayout.width - 2f;
                float textY = drawY + glyphLayout.height + 2f;
                drawCount(spriteBatch, countText, textX, textY, glyphLayout.width, glyphLayout.height);
            }
        }
    }

    private void renderPlayer(Player player, float gridOriginX, float gridOriginY, float tileSize) {
        float centerX = gridOriginX + (player.getRenderX() + 0.5f) * tileSize;
        float centerY = gridOriginY + (player.getRenderY() + 0.5f) * tileSize;
        float drawSize = tileSize * 0.9f;
        float drawX = centerX - drawSize * 0.5f;
        float drawY = centerY - drawSize * 0.5f;

        spriteBatch.draw(playerRegion, drawX, drawY, drawSize, drawSize);
    }

    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
    }

    private void drawCount(SpriteBatch batch, String text, float x, float y, float textWidth, float textHeight) {
        float bgPadX = 3f;
        float bgPadY = 1.5f;
        float bgX = x - bgPadX;
        float bgY = y - textHeight - bgPadY * 0.5f;
        float bgWidth = textWidth + bgPadX * 2f;
        float bgHeight = textHeight + bgPadY * 2f;

        batch.setColor(0f, 0f, 0f, 0.65f);
        batch.draw(whiteRegion, Math.round(bgX), Math.round(bgY), Math.round(bgWidth), Math.round(bgHeight));
        batch.setColor(Color.WHITE);

        font.setColor(Color.WHITE);
        font.draw(batch, text, Math.round(x), Math.round(y));
    }

    private Color colorFor(TileMaterial material, RoomType roomType, int parity) {
        Color base = material.colorForParity(parity);
        if (roomType == RoomType.SAFE) {
            float t = 0.45f;
            return tempColor.set(
                    SAFE_TINT.r * (1f - t) + base.r * t,
                    SAFE_TINT.g * (1f - t) + base.g * t,
                    SAFE_TINT.b * (1f - t) + base.b * t,
                    1f
            );
        }
        if (roomType == RoomType.DANGER) {
            float t = 0.35f;
            return tempColor.set(
                    DANGER_TINT.r * (1f - t) + base.r * t,
                    DANGER_TINT.g * (1f - t) + base.g * t,
                    DANGER_TINT.b * (1f - t) + base.b * t,
                    1f
            );
        }
        return tempColor.set(base);
    }

    private record VisibleWindow(int minTileX, int minTileY, int maxTileX, int maxTileY) {
        static VisibleWindow from(Viewport viewport, float tileSize) {
            com.badlogic.gdx.graphics.OrthographicCamera cam = (com.badlogic.gdx.graphics.OrthographicCamera) viewport.getCamera();
            float halfW = cam.viewportWidth * cam.zoom * 0.5f;
            float halfH = cam.viewportHeight * cam.zoom * 0.5f;
            float minWorldX = cam.position.x - halfW;
            float maxWorldX = cam.position.x + halfW;
            float minWorldY = cam.position.y - halfH;
            float maxWorldY = cam.position.y + halfH;

            int minTileX = (int) Math.floor(minWorldX / tileSize) - 2;
            int maxTileX = (int) Math.ceil(maxWorldX / tileSize) + 2;
            int minTileY = (int) Math.floor(minWorldY / tileSize) - 2;
            int maxTileY = (int) Math.ceil(maxWorldY / tileSize) + 2;
            return new VisibleWindow(minTileX, minTileY, maxTileX, maxTileY);
        }
    }
}
