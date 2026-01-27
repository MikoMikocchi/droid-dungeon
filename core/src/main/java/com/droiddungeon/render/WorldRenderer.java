package com.droiddungeon.render;

import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.utils.viewport.Viewport;
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
    private final Texture whiteTexture;
    private final TextureRegion whiteRegion;
    private final Texture playerTexture;
    private final TextureRegion playerRegion;
    private final Texture doroTexture;
    private final TextureRegion doroRegion;

    public WorldRenderer() {
        font = loadFont();
        font.setUseIntegerPositions(true);
        font.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        whiteTexture = new Texture(pixmap);
        whiteTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        whiteRegion = new TextureRegion(whiteTexture);
        pixmap.dispose();

        playerTexture = new Texture(Gdx.files.internal("characters/Player.png"));
        playerTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        playerRegion = new TextureRegion(playerTexture);

        doroTexture = new Texture(Gdx.files.internal("characters/Doro.png"));
        doroTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        doroRegion = new TextureRegion(doroTexture);
    }

    public void render(
            Viewport viewport,
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

        float gridOriginX = (viewport.getWorldWidth() - grid.getWorldWidth()) * 0.5f;
        float gridOriginY = (viewport.getWorldHeight() - grid.getWorldHeight()) * 0.5f;
        float tileSize = grid.getTileSize();

        renderTileFill(grid, gridOriginX, gridOriginY, tileSize);
        renderGridLines(grid, gridOriginX, gridOriginY, tileSize);
        renderGroundItems(groundItems, itemRegistry, gridOriginX, gridOriginY, tileSize);
        renderCompanionDoro(gridOriginX, gridOriginY, tileSize, companionX, companionY);
        renderPlayer(player, gridOriginX, gridOriginY, tileSize);
    }

    private void renderCompanionDoro(float gridOriginX, float gridOriginY, float tileSize, float companionX, float companionY) {
        float centerX = gridOriginX + (companionX + 0.5f) * tileSize;
        float centerY = gridOriginY + (companionY + 0.5f) * tileSize;
        float drawSize = tileSize * 0.9f;
        float drawX = centerX - drawSize * 0.5f;
        float drawY = centerY - drawSize * 0.5f;

        spriteBatch.begin();
        spriteBatch.setColor(Color.WHITE);
        spriteBatch.draw(doroRegion, drawX, drawY, drawSize, drawSize);
        spriteBatch.end();
    }

    private void renderTileFill(Grid grid, float gridOriginX, float gridOriginY, float tileSize) {
        shapeRenderer.begin(ShapeType.Filled);
        for (int y = 0; y < grid.getRows(); y++) {
            for (int x = 0; x < grid.getColumns(); x++) {
                TileMaterial material = grid.getTileMaterial(x, y);
                shapeRenderer.setColor(material.lightColor());
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
                float textX = drawX + drawSize - glyphLayout.width - 2f;
                float textY = drawY + glyphLayout.height + 2f;
                drawCount(spriteBatch, countText, textX, textY, glyphLayout.width, glyphLayout.height);
            }
        }
        spriteBatch.end();
    }

    private void renderPlayer(Player player, float gridOriginX, float gridOriginY, float tileSize) {
        float centerX = gridOriginX + (player.getRenderX() + 0.5f) * tileSize;
        float centerY = gridOriginY + (player.getRenderY() + 0.5f) * tileSize;
        float drawSize = tileSize * 0.9f;
        float drawX = centerX - drawSize * 0.5f;
        float drawY = centerY - drawSize * 0.5f;

        spriteBatch.begin();
        spriteBatch.setColor(Color.WHITE);
        spriteBatch.draw(playerRegion, drawX, drawY, drawSize, drawSize);
        spriteBatch.end();
    }

    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
        font.dispose();
        whiteTexture.dispose();
        playerTexture.dispose();
        doroTexture.dispose();
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

    private BitmapFont loadFont() {
        FreeTypeFontGenerator generator = null;
        try {
            generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/minecraft_font.ttf"));
            FreeTypeFontParameter params = new FreeTypeFontParameter();
            params.size = 13;
            params.borderWidth = 0.9f;
            params.borderColor = new Color(0f, 0f, 0f, 0.6f);
            params.minFilter = TextureFilter.Nearest;
            params.magFilter = TextureFilter.Nearest;
            params.color = Color.WHITE;
            return generator.generateFont(params);
        } catch (Exception e) {
            Gdx.app.error("WorldRenderer", "Failed to load custom font, falling back to default", e);
            BitmapFont fallback = new BitmapFont();
            fallback.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            return fallback;
        } finally {
            if (generator != null) {
                generator.dispose();
            }
        }
    }
}
