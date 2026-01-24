package com.droiddungeon.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
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
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.inventory.ItemStack;
import com.droiddungeon.items.ItemDefinition;
import com.droiddungeon.items.ItemRegistry;

public final class HudRenderer {
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final SpriteBatch spriteBatch = new SpriteBatch();
    private final BitmapFont font;
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private final Texture whiteTexture;
    private final TextureRegion whiteRegion;

    private final float cellSize = 48f;
    private final float gap = 6f;
    private final float padding = 10f;

    private float lastOriginX;
    private float lastOriginY;
    private int lastRows;

    public HudRenderer() {
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
    }

    public void render(
            Stage stage,
            Inventory inventory,
            ItemRegistry itemRegistry,
            ItemStack cursorStack,
            boolean inventoryOpen,
            int selectedSlotIndex,
            int hoveredSlotIndex,
            float deltaSeconds
    ) {
        stage.getViewport().apply();
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        spriteBatch.setProjectionMatrix(stage.getCamera().combined);

        cacheLayout(stage, inventoryOpen);

        if (inventoryOpen) {
            renderInventoryBackdrop(stage.getViewport().getWorldWidth(), stage.getViewport().getWorldHeight());
        }

        renderSlotGrid(inventory, selectedSlotIndex);
        renderSlotContents(inventory, itemRegistry);
        renderTooltip(stage, inventory, itemRegistry, hoveredSlotIndex);
        renderCursorStack(stage, itemRegistry, cursorStack);
    }

    private void cacheLayout(Stage stage, boolean inventoryOpen) {
        float viewportWidth = stage.getViewport().getWorldWidth();
        float hotbarWidth = Inventory.HOTBAR_SLOTS * cellSize + (Inventory.HOTBAR_SLOTS - 1) * gap;
        lastOriginX = (viewportWidth - hotbarWidth) * 0.5f;
        lastOriginY = padding;
        lastRows = inventoryOpen ? 4 : 1;
    }

    private void renderInventoryBackdrop(float viewportWidth, float viewportHeight) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0.05f, 0.05f, 0.06f, 0.3f);
        shapeRenderer.rect(0f, 0f, viewportWidth, viewportHeight);
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void renderSlotGrid(Inventory inventory, int selectedSlotIndex) {
        // Filled cells.
        shapeRenderer.begin(ShapeType.Filled);
        for (int row = 0; row < lastRows; row++) {
            for (int col = 0; col < Inventory.HOTBAR_SLOTS; col++) {
                float x = lastOriginX + col * (cellSize + gap);
                float y = lastOriginY + row * (cellSize + gap);

                boolean isHotbar = row == 0;
                if (isHotbar) {
                    shapeRenderer.setColor(0.14f, 0.14f, 0.16f, 1f);
                } else {
                    shapeRenderer.setColor(0.11f, 0.11f, 0.13f, 1f);
                }

                int slotIndex = row * Inventory.HOTBAR_SLOTS + col;
                if (inventory.get(slotIndex) != null) {
                    shapeRenderer.setColor(0.16f, 0.16f, 0.18f, 1f);
                }

                shapeRenderer.rect(x, y, cellSize, cellSize);
            }
        }
        shapeRenderer.end();

        // Outlines.
        shapeRenderer.begin(ShapeType.Line);
        shapeRenderer.setColor(0.30f, 0.30f, 0.34f, 1f);
        for (int row = 0; row < lastRows; row++) {
            for (int col = 0; col < Inventory.HOTBAR_SLOTS; col++) {
                float x = lastOriginX + col * (cellSize + gap);
                float y = lastOriginY + row * (cellSize + gap);
                shapeRenderer.rect(x, y, cellSize, cellSize);
            }
        }
        shapeRenderer.end();

        // Selection highlight.
        if (selectedSlotIndex >= 0 && selectedSlotIndex < Inventory.TOTAL_SLOTS) {
            int selectedRow = selectedSlotIndex / Inventory.HOTBAR_SLOTS;
            int selectedCol = selectedSlotIndex % Inventory.HOTBAR_SLOTS;

            if (selectedRow >= 0 && selectedRow < lastRows) {
                float x = lastOriginX + selectedCol * (cellSize + gap);
                float y = lastOriginY + selectedRow * (cellSize + gap);

                float thickness = 3f;
                shapeRenderer.begin(ShapeType.Filled);
                shapeRenderer.setColor(1f, 0.84f, 0.35f, 1f);
                shapeRenderer.rect(x - thickness, y - thickness, cellSize + thickness * 2f, thickness);
                shapeRenderer.rect(x - thickness, y + cellSize, cellSize + thickness * 2f, thickness);
                shapeRenderer.rect(x - thickness, y, thickness, cellSize);
                shapeRenderer.rect(x + cellSize, y, thickness, cellSize);
                shapeRenderer.end();
            }
        }
    }

    private void renderSlotContents(Inventory inventory, ItemRegistry itemRegistry) {
        spriteBatch.begin();
        for (int row = 0; row < lastRows; row++) {
            for (int col = 0; col < Inventory.HOTBAR_SLOTS; col++) {
                int slotIndex = row * Inventory.HOTBAR_SLOTS + col;
                ItemStack stack = inventory.get(slotIndex);
                if (stack == null) {
                    continue;
                }
                ItemDefinition def = itemRegistry.get(stack.itemId());
                if (def == null) {
                    continue;
                }
                TextureRegion icon = def.icon();
                float iconPadding = 8f;
                float drawSize = cellSize - iconPadding * 2f;
                float drawX = lastOriginX + col * (cellSize + gap) + iconPadding;
                float drawY = lastOriginY + row * (cellSize + gap) + iconPadding;
                spriteBatch.draw(icon, drawX, drawY, drawSize, drawSize);

                String countText = Integer.toString(stack.count());
                glyphLayout.setText(font, countText);
                float textX = drawX + drawSize - glyphLayout.width + 2f;
                float textY = drawY + glyphLayout.height + 2f;
                drawCount(spriteBatch, countText, textX, textY, glyphLayout.width, glyphLayout.height);
            }
        }
        spriteBatch.end();
    }

    private void renderCursorStack(Stage stage, ItemRegistry registry, ItemStack cursorStack) {
        if (cursorStack == null) {
            return;
        }
        ItemDefinition def = registry.get(cursorStack.itemId());
        if (def == null) {
            return;
        }

        Vector2 screen = new Vector2(Gdx.input.getX(), Gdx.input.getY());
        Vector2 world = stage.getViewport().unproject(screen);
        TextureRegion icon = def.icon();
        float iconPadding = 6f;
        float drawSize = cellSize - iconPadding * 2f;
        float drawX = world.x - drawSize * 0.5f;
        float drawY = world.y - drawSize * 0.5f;

        spriteBatch.begin();
        spriteBatch.draw(icon, drawX, drawY, drawSize, drawSize);
        String countText = Integer.toString(cursorStack.count());
        glyphLayout.setText(font, countText);
        float textX = drawX + drawSize - glyphLayout.width + 2f;
        float textY = drawY + glyphLayout.height + 2f;
        drawCount(spriteBatch, countText, textX, textY, glyphLayout.width, glyphLayout.height);
        spriteBatch.end();
    }

    private void renderTooltip(Stage stage, Inventory inventory, ItemRegistry itemRegistry, int hoveredSlotIndex) {
        if (hoveredSlotIndex < 0 || hoveredSlotIndex >= Inventory.TOTAL_SLOTS) {
            return;
        }
        ItemStack stack = inventory.get(hoveredSlotIndex);
        if (stack == null) {
            return;
        }
        ItemDefinition def = itemRegistry.get(stack.itemId());
        if (def == null) {
            return;
        }

        Vector2 world = stage.getViewport().unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
        String text = def.displayName();
        glyphLayout.setText(font, text);

        float paddingX = 8f;
        float paddingY = 6f;
        float boxWidth = glyphLayout.width + paddingX * 2f;
        float boxHeight = glyphLayout.height + paddingY * 2f;

        float x = world.x + 16f;
        float y = world.y + 20f;

        float viewportWidth = stage.getViewport().getWorldWidth();
        float viewportHeight = stage.getViewport().getWorldHeight();
        if (x + boxWidth > viewportWidth - 4f) {
            x = viewportWidth - boxWidth - 4f;
        }
        if (y + boxHeight > viewportHeight - 4f) {
            y = viewportHeight - boxHeight - 4f;
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0.05f, 0.05f, 0.08f, 0.9f);
        shapeRenderer.rect(x, y, boxWidth, boxHeight);
        shapeRenderer.setColor(0.35f, 0.35f, 0.4f, 0.9f);
        shapeRenderer.rect(x, y, boxWidth, 2f);
        shapeRenderer.rect(x, y, 2f, boxHeight);
        shapeRenderer.rect(x + boxWidth - 2f, y, 2f, boxHeight);
        shapeRenderer.rect(x, y + boxHeight - 2f, boxWidth, 2f);
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        spriteBatch.begin();
        font.setColor(Color.WHITE);
        font.draw(spriteBatch, glyphLayout, x + paddingX, y + paddingY + glyphLayout.height);
        spriteBatch.end();
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
            params.size = 14;
            params.borderWidth = 0.9f;
            params.borderColor = new Color(0f, 0f, 0f, 0.6f);
            params.minFilter = TextureFilter.Nearest;
            params.magFilter = TextureFilter.Nearest;
            params.color = Color.WHITE;
            return generator.generateFont(params);
        } catch (Exception e) {
            Gdx.app.error("HudRenderer", "Failed to load custom font, falling back to default", e);
            BitmapFont fallback = new BitmapFont();
            fallback.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            return fallback;
        } finally {
            if (generator != null) {
                generator.dispose();
            }
        }
    }

    public int hitTestSlot(Stage stage, float screenX, float screenY, boolean inventoryOpen) {
        cacheLayout(stage, inventoryOpen);
        Vector2 world = new Vector2(screenX, screenY);
        stage.getViewport().unproject(world);

        for (int row = 0; row < lastRows; row++) {
            for (int col = 0; col < Inventory.HOTBAR_SLOTS; col++) {
                float x = lastOriginX + col * (cellSize + gap);
                float y = lastOriginY + row * (cellSize + gap);
                if (world.x >= x && world.x <= x + cellSize && world.y >= y && world.y <= y + cellSize) {
                    return row * Inventory.HOTBAR_SLOTS + col;
                }
            }
        }
        return -1;
    }

    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
        font.dispose();
        whiteTexture.dispose();
    }
}
