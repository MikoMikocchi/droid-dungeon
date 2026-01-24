package com.droiddungeon.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
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
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout glyphLayout = new GlyphLayout();

    private final float cellSize = 48f;
    private final float gap = 6f;
    private final float padding = 10f;

    private float lastOriginX;
    private float lastOriginY;
    private int lastRows;

    public HudRenderer() {
        font.getData().setScale(0.9f);
    }

    public void render(
            Stage stage,
            Inventory inventory,
            ItemRegistry itemRegistry,
            ItemStack cursorStack,
            boolean inventoryOpen,
            int selectedSlotIndex,
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
                font.draw(spriteBatch, glyphLayout, textX, textY);
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
        font.draw(spriteBatch, glyphLayout, textX, textY);
        spriteBatch.end();
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
    }
}
