package com.droiddungeon.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.inventory.ItemStack;
import com.droiddungeon.items.ItemDefinition;
import com.droiddungeon.items.ItemRegistry;
import com.droiddungeon.render.RenderAssets;

public final class HudRenderer {
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final SpriteBatch spriteBatch = new SpriteBatch();
    private final BitmapFont font;
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private final TextureRegion whiteRegion;

    private final float cellSize = 48f;
    private final float gap = 6f;
    private final float padding = 10f;

    private float lastOriginX;
    private float lastOriginY;
    private int lastRows;

    private boolean tooltipVisible;
    private String tooltipText;
    private float tooltipX;
    private float tooltipY;
    private float tooltipPaddingX;
    private float tooltipPaddingY;
    private float tooltipTextWidth;
    private float tooltipTextHeight;

    public HudRenderer() {
        font = RenderAssets.font(14);
        whiteRegion = RenderAssets.whiteRegion();
    }

    public void render(
            Viewport viewport,
            Inventory inventory,
            ItemRegistry itemRegistry,
            ItemStack cursorStack,
            boolean inventoryOpen,
            int selectedSlotIndex,
            int hoveredSlotIndex,
            float deltaSeconds,
            com.droiddungeon.player.PlayerStats playerStats
    ) {
        viewport.apply();
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);

        cacheLayout(viewport, inventoryOpen);

        updateTooltipData(viewport, inventory, itemRegistry, hoveredSlotIndex);

        renderShapes(viewport, inventory, inventoryOpen, selectedSlotIndex);
        renderHealth(viewport, playerStats);

        spriteBatch.begin();
        renderSlotContents(inventory, itemRegistry);
        renderTooltipText();
        renderCursorStack(viewport, itemRegistry, cursorStack);
        spriteBatch.end();
    }

    private void cacheLayout(Viewport viewport, boolean inventoryOpen) {
        float viewportWidth = viewport.getWorldWidth();
        float hotbarWidth = Inventory.HOTBAR_SLOTS * cellSize + (Inventory.HOTBAR_SLOTS - 1) * gap;
        lastOriginX = (viewportWidth - hotbarWidth) * 0.5f;
        lastOriginY = padding;
        lastRows = inventoryOpen ? 4 : 1;
    }

    private void renderShapes(
            Viewport viewport,
            Inventory inventory,
            boolean inventoryOpen,
            int selectedSlotIndex
    ) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeType.Filled);
        if (inventoryOpen) {
            renderInventoryBackdropFilled(viewport.getWorldWidth(), viewport.getWorldHeight());
        }
        renderSlotGridFilled(inventory, selectedSlotIndex);
        renderTooltipBoxFilled();
        shapeRenderer.end();

        shapeRenderer.begin(ShapeType.Line);
        renderSlotGridOutline();
        shapeRenderer.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void renderInventoryBackdropFilled(float viewportWidth, float viewportHeight) {
        shapeRenderer.setColor(0.05f, 0.05f, 0.06f, 0.3f);
        shapeRenderer.rect(0f, 0f, viewportWidth, viewportHeight);
    }

    private void renderSlotGridFilled(Inventory inventory, int selectedSlotIndex) {
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

        // Selection highlight.
        if (selectedSlotIndex >= 0 && selectedSlotIndex < Inventory.TOTAL_SLOTS) {
            int selectedRow = selectedSlotIndex / Inventory.HOTBAR_SLOTS;
            int selectedCol = selectedSlotIndex % Inventory.HOTBAR_SLOTS;

            if (selectedRow >= 0 && selectedRow < lastRows) {
                float x = lastOriginX + selectedCol * (cellSize + gap);
                float y = lastOriginY + selectedRow * (cellSize + gap);

                float thickness = 3f;
                shapeRenderer.setColor(1f, 0.84f, 0.35f, 1f);
                shapeRenderer.rect(x - thickness, y - thickness, cellSize + thickness * 2f, thickness);
                shapeRenderer.rect(x - thickness, y + cellSize, cellSize + thickness * 2f, thickness);
                shapeRenderer.rect(x - thickness, y, thickness, cellSize);
                shapeRenderer.rect(x + cellSize, y, thickness, cellSize);
            }
        }
    }

    private void renderSlotGridOutline() {
        shapeRenderer.setColor(0.30f, 0.30f, 0.34f, 1f);
        for (int row = 0; row < lastRows; row++) {
            for (int col = 0; col < Inventory.HOTBAR_SLOTS; col++) {
                float x = lastOriginX + col * (cellSize + gap);
                float y = lastOriginY + row * (cellSize + gap);
                shapeRenderer.rect(x, y, cellSize, cellSize);
            }
        }
    }

    private void renderSlotContents(Inventory inventory, ItemRegistry itemRegistry) {
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
    }

    private void renderCursorStack(Viewport viewport, ItemRegistry registry, ItemStack cursorStack) {
        if (cursorStack == null) {
            return;
        }
        ItemDefinition def = registry.get(cursorStack.itemId());
        if (def == null) {
            return;
        }

        Vector2 screen = new Vector2(Gdx.input.getX(), Gdx.input.getY());
        Vector2 world = viewport.unproject(screen);
        TextureRegion icon = def.icon();
        float iconPadding = 6f;
        float drawSize = cellSize - iconPadding * 2f;
        float drawX = world.x - drawSize * 0.5f;
        float drawY = world.y - drawSize * 0.5f;

        spriteBatch.draw(icon, drawX, drawY, drawSize, drawSize);
        String countText = Integer.toString(cursorStack.count());
        glyphLayout.setText(font, countText);
        float textX = drawX + drawSize - glyphLayout.width + 2f;
        float textY = drawY + glyphLayout.height + 2f;
        drawCount(spriteBatch, countText, textX, textY, glyphLayout.width, glyphLayout.height);
    }

    private void updateTooltipData(Viewport viewport, Inventory inventory, ItemRegistry itemRegistry, int hoveredSlotIndex) {
        tooltipVisible = false;
        tooltipText = null;

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

        Vector2 world = viewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
        if (def.maxDurability() > 0) {
            int current = Math.min(stack.durability(), def.maxDurability());
            tooltipText = def.displayName() + "\nDurability: " + current + "/" + def.maxDurability();
        } else {
            tooltipText = def.displayName();
        }
        glyphLayout.setText(font, tooltipText);

        tooltipPaddingX = 8f;
        tooltipPaddingY = 6f;
        tooltipTextWidth = glyphLayout.width;
        tooltipTextHeight = glyphLayout.height;
        float boxWidth = tooltipTextWidth + tooltipPaddingX * 2f;
        float boxHeight = tooltipTextHeight + tooltipPaddingY * 2f;

        float x = world.x + 16f;
        float y = world.y + 20f;

        float viewportWidth = viewport.getWorldWidth();
        float viewportHeight = viewport.getWorldHeight();
        if (x + boxWidth > viewportWidth - 4f) {
            x = viewportWidth - boxWidth - 4f;
        }
        if (y + boxHeight > viewportHeight - 4f) {
            y = viewportHeight - boxHeight - 4f;
        }

        tooltipX = x;
        tooltipY = y;
        tooltipVisible = true;
    }

    private void renderTooltipBoxFilled() {
        if (!tooltipVisible) {
            return;
        }
        float boxWidth = tooltipTextWidth + tooltipPaddingX * 2f;
        float boxHeight = tooltipTextHeight + tooltipPaddingY * 2f;

        shapeRenderer.setColor(0.05f, 0.05f, 0.08f, 0.9f);
        shapeRenderer.rect(tooltipX, tooltipY, boxWidth, boxHeight);
        shapeRenderer.setColor(0.35f, 0.35f, 0.4f, 0.9f);
        shapeRenderer.rect(tooltipX, tooltipY, boxWidth, 2f);
        shapeRenderer.rect(tooltipX, tooltipY, 2f, boxHeight);
        shapeRenderer.rect(tooltipX + boxWidth - 2f, tooltipY, 2f, boxHeight);
        shapeRenderer.rect(tooltipX, tooltipY + boxHeight - 2f, boxWidth, 2f);
    }

    private void renderTooltipText() {
        if (!tooltipVisible) {
            return;
        }
        font.setColor(Color.WHITE);
        font.draw(spriteBatch, tooltipText, tooltipX + tooltipPaddingX, tooltipY + tooltipPaddingY + tooltipTextHeight);
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

    private void renderHealth(Viewport viewport, com.droiddungeon.player.PlayerStats stats) {
        if (stats == null) {
            return;
        }
        float barWidth = Inventory.HOTBAR_SLOTS * cellSize + (Inventory.HOTBAR_SLOTS - 1) * gap;
        float barHeight = 18f;
        float x = lastOriginX;
        float y = lastOriginY + lastRows * (cellSize + gap) + 10f;

        float ratio = Math.max(0f, Math.min(1f, stats.getHealthRatio()));

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.55f);
        shapeRenderer.rect(x - 3f, y - 3f, barWidth + 6f, barHeight + 6f);

        shapeRenderer.setColor(0.14f, 0.14f, 0.16f, 1f);
        shapeRenderer.rect(x, y, barWidth, barHeight);

        Color fill = ratio > 0.5f ? new Color(0.25f, 0.85f, 0.35f, 1f) : new Color(0.92f, 0.35f, 0.2f, 1f);
        shapeRenderer.setColor(fill);
        shapeRenderer.rect(x, y, barWidth * ratio, barHeight);
        shapeRenderer.end();

        spriteBatch.begin();
        String text = "HP: " + Math.round(stats.getHealth()) + "/" + (int) stats.getMaxHealth();
        glyphLayout.setText(font, text);
        float textX = x + (barWidth - glyphLayout.width) * 0.5f;
        float textY = y + (barHeight + glyphLayout.height) * 0.5f;
        font.setColor(Color.WHITE);
        font.draw(spriteBatch, text, textX, textY);
        spriteBatch.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public Rectangle getRestartButtonBounds(Viewport viewport) {
        float w = 220f;
        float h = 52f;
        float x = (viewport.getWorldWidth() - w) * 0.5f;
        float y = (viewport.getWorldHeight() - h) * 0.32f;
        return new Rectangle(x, y, w, h);
    }

    public void renderDeathOverlay(Viewport viewport, boolean restartHovered) {
        viewport.apply();
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);

        Rectangle btn = getRestartButtonBounds(viewport);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.55f);
        shapeRenderer.rect(0f, 0f, viewport.getWorldWidth(), viewport.getWorldHeight());

        // Button
        shapeRenderer.setColor(restartHovered ? new Color(0.32f, 0.65f, 0.9f, 1f) : new Color(0.24f, 0.38f, 0.52f, 1f));
        shapeRenderer.rect(btn.x, btn.y, btn.width, btn.height);
        shapeRenderer.setColor(1f, 1f, 1f, 0.25f);
        shapeRenderer.rect(btn.x, btn.y, btn.width, 4f);
        shapeRenderer.end();

        spriteBatch.begin();
        String title = "YOU DIED";
        glyphLayout.setText(font, title);
        float titleX = (viewport.getWorldWidth() - glyphLayout.width) * 0.5f;
        float titleY = btn.y + btn.height + glyphLayout.height + 28f;
        font.setColor(Color.WHITE);
        font.draw(spriteBatch, glyphLayout, titleX, titleY);

        String buttonText = "Restart";
        glyphLayout.setText(font, buttonText);
        float textX = btn.x + (btn.width - glyphLayout.width) * 0.5f;
        float textY = btn.y + (btn.height + glyphLayout.height) * 0.5f;
        font.setColor(Color.WHITE);
        font.draw(spriteBatch, glyphLayout, textX, textY);
        spriteBatch.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public int hitTestSlot(Viewport viewport, float screenX, float screenY, boolean inventoryOpen) {
        cacheLayout(viewport, inventoryOpen);
        Vector2 world = new Vector2(screenX, screenY);
        viewport.unproject(world);

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
    }
}
