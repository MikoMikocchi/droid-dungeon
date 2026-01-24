package com.droiddungeon.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.droiddungeon.inventory.Inventory;

public final class HudRenderer {
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();

    private final float cellSize = 48f;
    private final float gap = 6f;
    private final float padding = 10f;

    private float lastOriginX;
    private float lastOriginY;
    private int lastRows;

    public void render(Stage stage, Inventory inventory, boolean inventoryOpen, int selectedSlotIndex, float deltaSeconds) {
        stage.getViewport().apply();
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);

        float viewportWidth = stage.getViewport().getWorldWidth();
        float viewportHeight = stage.getViewport().getWorldHeight();

        float hotbarWidth = Inventory.HOTBAR_SLOTS * cellSize + (Inventory.HOTBAR_SLOTS - 1) * gap;
        float originX = (viewportWidth - hotbarWidth) * 0.5f;
        float originY = padding;

        int rows = inventoryOpen ? 4 : 1;

        lastOriginX = originX;
        lastOriginY = originY;
        lastRows = rows;

        if (inventoryOpen) {
            renderInventoryBackdrop(viewportWidth, viewportHeight);
        }

        renderSlotGrid(originX, originY, rows, inventory, selectedSlotIndex);
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

    private void renderSlotGrid(float originX, float originY, int rows, Inventory inventory, int selectedSlotIndex) {
        // Filled cells.
        shapeRenderer.begin(ShapeType.Filled);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < Inventory.HOTBAR_SLOTS; col++) {
                float x = originX + col * (cellSize + gap);
                float y = originY + row * (cellSize + gap);

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
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < Inventory.HOTBAR_SLOTS; col++) {
                float x = originX + col * (cellSize + gap);
                float y = originY + row * (cellSize + gap);
                shapeRenderer.rect(x, y, cellSize, cellSize);
            }
        }
        shapeRenderer.end();

        // Selection highlight.
        if (selectedSlotIndex >= 0 && selectedSlotIndex < Inventory.TOTAL_SLOTS) {
            int selectedRow = selectedSlotIndex / Inventory.HOTBAR_SLOTS;
            int selectedCol = selectedSlotIndex % Inventory.HOTBAR_SLOTS;

            if (selectedRow >= 0 && selectedRow < rows) {
                float x = originX + selectedCol * (cellSize + gap);
                float y = originY + selectedRow * (cellSize + gap);

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

    public int hitTestSlot(Stage stage, float screenX, float screenY) {
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
    }
}
