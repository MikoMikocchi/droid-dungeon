package com.droiddungeon.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
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

    private boolean debugVisible;
    private String debugTextCached;
    private float debugX;
    private float debugY;
    private float debugPaddingX;
    private float debugPaddingY;
    private float debugTextWidth;
    private float debugTextHeight;

    public HudRenderer() {
        font = RenderAssets.font(14);
        whiteRegion = RenderAssets.whiteRegion();
    }

    public void render(
            Stage stage,
            Inventory inventory,
            ItemRegistry itemRegistry,
            ItemStack cursorStack,
            boolean inventoryOpen,
            int selectedSlotIndex,
            int hoveredSlotIndex,
            float deltaSeconds,
            String debugText,
            Grid grid,
            Player player,
            float companionX,
            float companionY
    ) {
        stage.getViewport().apply();
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        spriteBatch.setProjectionMatrix(stage.getCamera().combined);

        cacheLayout(stage, inventoryOpen);

        updateTooltipData(stage, inventory, itemRegistry, hoveredSlotIndex);
        updateDebugData(stage, debugText);

        renderShapes(stage, inventory, inventoryOpen, selectedSlotIndex, grid, player, companionX, companionY);

        spriteBatch.begin();
        renderSlotContents(inventory, itemRegistry);
        renderTooltipText();
        renderCursorStack(stage, itemRegistry, cursorStack);
        renderDebugText();
        spriteBatch.end();
    }

    private void cacheLayout(Stage stage, boolean inventoryOpen) {
        float viewportWidth = stage.getViewport().getWorldWidth();
        float hotbarWidth = Inventory.HOTBAR_SLOTS * cellSize + (Inventory.HOTBAR_SLOTS - 1) * gap;
        lastOriginX = (viewportWidth - hotbarWidth) * 0.5f;
        lastOriginY = padding;
        lastRows = inventoryOpen ? 4 : 1;
    }

    private void renderShapes(
            Stage stage,
            Inventory inventory,
            boolean inventoryOpen,
            int selectedSlotIndex,
            Grid grid,
            Player player,
            float companionX,
            float companionY
    ) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeType.Filled);
        if (inventoryOpen) {
            renderInventoryBackdropFilled(stage.getViewport().getWorldWidth(), stage.getViewport().getWorldHeight());
        }
        renderSlotGridFilled(inventory, selectedSlotIndex);
        renderTooltipBoxFilled();
        renderDebugBoxFilled();
        renderMinimapFilled(stage, grid, player, companionX, companionY);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeType.Line);
        renderSlotGridOutline();
        renderMinimapOutline(stage, grid, player, companionX, companionY);
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

        spriteBatch.draw(icon, drawX, drawY, drawSize, drawSize);
        String countText = Integer.toString(cursorStack.count());
        glyphLayout.setText(font, countText);
        float textX = drawX + drawSize - glyphLayout.width + 2f;
        float textY = drawY + glyphLayout.height + 2f;
        drawCount(spriteBatch, countText, textX, textY, glyphLayout.width, glyphLayout.height);
    }

    private void updateTooltipData(Stage stage, Inventory inventory, ItemRegistry itemRegistry, int hoveredSlotIndex) {
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

        Vector2 world = stage.getViewport().unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
        tooltipText = def.displayName();
        glyphLayout.setText(font, tooltipText);

        tooltipPaddingX = 8f;
        tooltipPaddingY = 6f;
        tooltipTextWidth = glyphLayout.width;
        tooltipTextHeight = glyphLayout.height;
        float boxWidth = tooltipTextWidth + tooltipPaddingX * 2f;
        float boxHeight = tooltipTextHeight + tooltipPaddingY * 2f;

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

    private void updateDebugData(Stage stage, String text) {
        debugVisible = false;
        debugTextCached = null;
        if (text == null || text.isEmpty()) {
            return;
        }
        debugTextCached = text;
        glyphLayout.setText(font, debugTextCached);

        debugPaddingX = 8f;
        debugPaddingY = 6f;
        debugTextWidth = glyphLayout.width;
        debugTextHeight = glyphLayout.height;
        float margin = 10f;
        float boxWidth = debugTextWidth + debugPaddingX * 2f;
        float boxHeight = debugTextHeight + debugPaddingY * 2f;

        debugX = margin;
        debugY = stage.getViewport().getWorldHeight() - margin - boxHeight;
        debugVisible = true;
    }

    private void renderDebugBoxFilled() {
        if (!debugVisible) {
            return;
        }
        float boxWidth = debugTextWidth + debugPaddingX * 2f;
        float boxHeight = debugTextHeight + debugPaddingY * 2f;
        shapeRenderer.setColor(0f, 0f, 0f, 0.7f);
        shapeRenderer.rect(debugX, debugY, boxWidth, boxHeight);
        shapeRenderer.setColor(0.35f, 0.35f, 0.4f, 0.9f);
        shapeRenderer.rect(debugX, debugY, boxWidth, 2f);
        shapeRenderer.rect(debugX, debugY, 2f, boxHeight);
        shapeRenderer.rect(debugX + boxWidth - 2f, debugY, 2f, boxHeight);
        shapeRenderer.rect(debugX, debugY + boxHeight - 2f, boxWidth, 2f);
    }

    private void renderDebugText() {
        if (!debugVisible) {
            return;
        }
        font.setColor(Color.WHITE);
        font.draw(spriteBatch, debugTextCached, debugX + debugPaddingX, debugY + debugPaddingY + debugTextHeight);
    }

    private void renderMinimapFilled(Stage stage, Grid grid, Player player, float companionX, float companionY) {
        if (grid == null || player == null) {
            return;
        }

        float viewportWidth = stage.getViewport().getWorldWidth();
        float viewportHeight = stage.getViewport().getWorldHeight();

        // Keep the minimap compact and anchored to the top-right corner.
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

        // Background
        shapeRenderer.setColor(0f, 0f, 0f, 0.55f);
        shapeRenderer.rect(originX - pad, originY - pad, mapWidth + pad * 2f, mapHeight + pad * 2f);

        // Tiles
        for (int y = 0; y < grid.getRows(); y++) {
            for (int x = 0; x < grid.getColumns(); x++) {
                float drawX = originX + x * tile;
                float drawY = originY + y * tile;
                Color color = grid.getTileMaterial(x, y).darkColor();
                // Slightly desaturate and brighten for readability at small scale.
                float r = 0.18f + color.r * 0.75f;
                float g = 0.18f + color.g * 0.75f;
                float b = 0.18f + color.b * 0.75f;
                shapeRenderer.setColor(r, g, b, 0.95f);
                shapeRenderer.rect(drawX, drawY, tile, tile);
            }
        }

        // Player marker
        float playerX = originX + (player.getRenderX() + 0.5f) * tile;
        float playerY = originY + (player.getRenderY() + 0.5f) * tile;
        float markerSize = Math.max(3f, tile * 0.55f);
        float halfMarker = markerSize * 0.5f;
        shapeRenderer.setColor(0.98f, 0.86f, 0.3f, 1f);
        shapeRenderer.rect(playerX - halfMarker, playerY - halfMarker, markerSize, markerSize);

        // Companion marker
        float companionWorldX = originX + (companionX + 0.5f) * tile;
        float companionWorldY = originY + (companionY + 0.5f) * tile;
        float companionSize = Math.max(3f, tile * 0.45f);
        float halfCompanion = companionSize * 0.5f;
        shapeRenderer.setColor(0.35f, 0.85f, 0.95f, 1f);
        shapeRenderer.rect(companionWorldX - halfCompanion, companionWorldY - halfCompanion, companionSize, companionSize);
    }

    private void renderMinimapOutline(Stage stage, Grid grid, Player player, float companionX, float companionY) {
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

        shapeRenderer.setColor(0.4f, 0.4f, 0.44f, 1f);
        shapeRenderer.rect(originX - pad, originY - pad, mapWidth + pad * 2f, mapHeight + pad * 2f);
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
    }
}
