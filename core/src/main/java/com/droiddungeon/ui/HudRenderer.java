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
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.crafting.CraftingRecipe;
import com.droiddungeon.crafting.CraftingSystem;
import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.inventory.ItemStack;
import com.droiddungeon.items.ItemDefinition;
import com.droiddungeon.items.ItemRegistry;
import com.droiddungeon.render.ClientAssets;

public final class HudRenderer {
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final SpriteBatch spriteBatch = new SpriteBatch();
    private final BitmapFont font;
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private final TextureRegion whiteRegion;

    private final float cellSize = 48f;
    private final float gap = 6f;
    private final float padding = 10f;
    private final float craftPanelGap = 26f;
    private final float craftPanelWidthBase = 380f;
    private final float craftIconSize = 44f;
    private final float craftIconGap = 8f;
    private final int craftIconsPerRow = 4;
    private final float craftDetailHeight = 126f;
    private final float craftDetailGap = 12f;
    private final float craftPanelPadding = 12f;
    private final float craftHeaderHeight = 24f;
    private final float craftButtonWidth = 82f;
    private final float craftButtonHeight = 32f;
    private final float healthBarHeight = 18f;
    private final float healthBarGap = 8f;

    private float lastOriginX;
    private float lastOriginY;
    private int lastRows;
    private float craftPanelX;
    private float craftPanelY;
    private float craftPanelWidth;
    private float craftPanelHeight;
    private boolean craftingVisible;
    private int iconRows;
    private int lastRecipeCount;
    private float healthBarY;

    private boolean tooltipVisible;
    private String tooltipText;
    private float tooltipX;
    private float tooltipY;
    private float tooltipPaddingX;
    private float tooltipPaddingY;
    private float tooltipTextWidth;
    private float tooltipTextHeight;

    public static record CraftingHit(int iconIndex, boolean insidePanel, boolean onCraftButton) {
        public static CraftingHit none() {
            return new CraftingHit(-1, false, false);
        }
    }

    public HudRenderer(ClientAssets assets) {
        font = assets.font(14);
        whiteRegion = assets.whiteRegion();
    }

    public void render(
            Viewport viewport,
            Inventory inventory,
            ItemRegistry itemRegistry,
            CraftingSystem craftingSystem,
            ItemStack cursorStack,
            boolean inventoryOpen,
            int selectedSlotIndex,
            int hoveredSlotIndex,
            int hoveredRecipeIndex,
            int selectedRecipeIndex,
            boolean craftButtonHovered,
            float deltaSeconds,
            com.droiddungeon.player.PlayerStats playerStats
    ) {
        viewport.apply();
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);

        int recipeCount = craftingSystem != null ? craftingSystem.getRecipes().size() : 0;
        cacheLayout(viewport, inventoryOpen, recipeCount);

        updateTooltipData(viewport, inventory, itemRegistry, hoveredSlotIndex);

        renderShapes(viewport, inventory, inventoryOpen, selectedSlotIndex, craftingSystem, hoveredRecipeIndex, selectedRecipeIndex, craftButtonHovered);
        renderHealth(viewport, playerStats);

        spriteBatch.begin();
        renderCraftingContents(inventory, craftingSystem, itemRegistry, hoveredRecipeIndex, selectedRecipeIndex, craftButtonHovered);
        renderSlotContents(inventory, itemRegistry);
        renderTooltipText();
        renderCursorStack(viewport, itemRegistry, cursorStack);
        spriteBatch.end();
    }

    private void cacheLayout(Viewport viewport, boolean inventoryOpen, int recipeCount) {
        float viewportWidth = viewport.getWorldWidth();
        float viewportHeight = viewport.getWorldHeight();
        float hotbarWidth = Inventory.HOTBAR_SLOTS * cellSize + (Inventory.HOTBAR_SLOTS - 1) * gap;

        lastRows = inventoryOpen ? 4 : 1;
        lastRecipeCount = Math.max(0, recipeCount);

        float gridHeight = lastRows * cellSize + (lastRows - 1) * gap;
        float topY = viewportHeight - padding;

        healthBarY = topY - healthBarHeight;
        float gridTopY = healthBarY - healthBarGap;

        lastOriginX = padding;
        lastOriginY = gridTopY - cellSize;
        if (!inventoryOpen) {
            craftingVisible = false;
            craftPanelHeight = 0f;
            craftPanelWidth = 0f;
            return;
        }

        float gridWidth = hotbarWidth;

        iconRows = Math.max(1, (int) Math.ceil(lastRecipeCount / (float) craftIconsPerRow));
        float iconsHeight = iconRows * (craftIconSize + craftIconGap) - craftIconGap;
        float bodyHeight = iconsHeight + craftDetailGap + craftDetailHeight;

        craftPanelHeight = Math.max(craftHeaderHeight + craftPanelPadding * 2f + bodyHeight, gridHeight + 30f);
        craftPanelWidth = craftPanelWidthBase;

        craftPanelX = lastOriginX + gridWidth + craftPanelGap;
        craftPanelY = topY - craftPanelHeight;
        craftingVisible = true;
    }

    private void renderShapes(
            Viewport viewport,
            Inventory inventory,
            boolean inventoryOpen,
            int selectedSlotIndex,
            CraftingSystem craftingSystem,
            int hoveredRecipeIndex,
            int selectedRecipeIndex,
            boolean craftButtonHovered
    ) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeType.Filled);
        if (inventoryOpen) {
            renderInventoryBackdropFilled(viewport.getWorldWidth(), viewport.getWorldHeight());
        }
        renderCraftingPanelFilled(craftingSystem, hoveredRecipeIndex, selectedRecipeIndex, craftButtonHovered);
        renderSlotGridFilled(inventory, selectedSlotIndex);
        renderTooltipBoxFilled();
        shapeRenderer.end();

        shapeRenderer.begin(ShapeType.Line);
        renderSlotGridOutline();
        renderCraftingOutline();
        shapeRenderer.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private float rowY(int row) {
        return lastOriginY - row * (cellSize + gap);
    }

    private void renderInventoryBackdropFilled(float viewportWidth, float viewportHeight) {
        shapeRenderer.setColor(0.05f, 0.05f, 0.06f, 0.3f);
        shapeRenderer.rect(0f, 0f, viewportWidth, viewportHeight);
    }

    private void renderCraftingPanelFilled(CraftingSystem craftingSystem, int hoveredRecipeIndex, int selectedRecipeIndex, boolean craftButtonHovered) {
        if (!craftingVisible || craftingSystem == null) {
            return;
        }

        shapeRenderer.setColor(0.07f, 0.07f, 0.09f, 0.92f);
        shapeRenderer.rect(craftPanelX, craftPanelY, craftPanelWidth, craftPanelHeight);

        float headerY = craftPanelY + craftPanelHeight - craftHeaderHeight - craftPanelPadding * 0.35f;
        shapeRenderer.setColor(0.13f, 0.13f, 0.16f, 1f);
        shapeRenderer.rect(craftPanelX, headerY, craftPanelWidth, craftHeaderHeight + craftPanelPadding * 0.35f);

        // Icons background
        for (int i = 0; i < craftingSystem.getRecipes().size(); i++) {
            boolean hovered = hoveredRecipeIndex == i;
            boolean selected = selectedRecipeIndex == i;
            boolean canCraft = craftingSystem.canCraft(i);

            Color base = canCraft ? new Color(0.12f, 0.18f, 0.14f, 0.95f) : new Color(0.12f, 0.12f, 0.14f, 0.9f);
            if (selected) {
                base = new Color(base.r + 0.06f, base.g + 0.06f, base.b + 0.08f, 1f);
            } else if (hovered) {
                base = new Color(base.r + 0.04f, base.g + 0.04f, base.b + 0.04f, 1f);
            }
            shapeRenderer.setColor(base);
            shapeRenderer.rect(iconX(i), iconY(i), craftIconSize, craftIconSize);
        }

        // Detail box
        float detailY = detailY();
        shapeRenderer.setColor(0.11f, 0.11f, 0.14f, 0.96f);
        shapeRenderer.rect(craftPanelX + craftPanelPadding, detailY, craftPanelWidth - craftPanelPadding * 2f, craftDetailHeight);

        // Craft button background (for hover pulse)
        float btnX = detailButtonX();
        float btnY = detailButtonY();
        if (craftButtonHovered) {
            shapeRenderer.setColor(0.34f, 0.82f, 0.45f, 0.12f);
            shapeRenderer.rect(btnX - 4f, btnY - 4f, craftButtonWidth + 8f, craftButtonHeight + 8f);
        }
    }

    private void renderSlotGridFilled(Inventory inventory, int selectedSlotIndex) {
        for (int row = 0; row < lastRows; row++) {
            for (int col = 0; col < Inventory.HOTBAR_SLOTS; col++) {
                float x = lastOriginX + col * (cellSize + gap);
                float y = rowY(row);

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

        if (selectedSlotIndex >= 0 && selectedSlotIndex < Inventory.TOTAL_SLOTS) {
            int selectedRow = selectedSlotIndex / Inventory.HOTBAR_SLOTS;
            int selectedCol = selectedSlotIndex % Inventory.HOTBAR_SLOTS;

            if (selectedRow >= 0 && selectedRow < lastRows) {
                float x = lastOriginX + selectedCol * (cellSize + gap);
                float y = rowY(selectedRow);

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
                float y = rowY(row);
                shapeRenderer.rect(x, y, cellSize, cellSize);
            }
        }
    }

    private void renderCraftingOutline() {
        if (!craftingVisible) {
            return;
        }
        shapeRenderer.setColor(0.28f, 0.28f, 0.33f, 1f);
        shapeRenderer.rect(craftPanelX, craftPanelY, craftPanelWidth, craftPanelHeight);
    }

    private float iconsAreaHeight() {
        return iconRows * (craftIconSize + craftIconGap) - craftIconGap;
    }

    private float iconsTopY() {
        float headerBottom = craftPanelY + craftPanelHeight - craftHeaderHeight - craftPanelPadding * 0.35f;
        return headerBottom - craftIconSize - craftPanelPadding * 0.5f;
    }

    private float iconX(int recipeIndex) {
        int col = recipeIndex % craftIconsPerRow;
        return craftPanelX + craftPanelPadding + col * (craftIconSize + craftIconGap);
    }

    private float iconY(int recipeIndex) {
        int row = recipeIndex / craftIconsPerRow;
        return iconsTopY() - row * (craftIconSize + craftIconGap);
    }

    private float detailY() {
        return craftPanelY + craftPanelPadding;
    }

    private float detailHeight() {
        return craftDetailHeight;
    }

    private float detailWidth() {
        return craftPanelWidth - craftPanelPadding * 2f;
    }

    private float detailButtonX() {
        return craftPanelX + craftPanelPadding + detailWidth() - craftButtonWidth;
    }

    private float detailButtonY() {
        return detailY() + 10f;
    }

    private void renderCraftingContents(
            Inventory inventory,
            CraftingSystem craftingSystem,
            ItemRegistry itemRegistry,
            int hoveredRecipeIndex,
            int selectedRecipeIndex,
            boolean craftButtonHovered
    ) {
        if (!craftingVisible || craftingSystem == null) {
            return;
        }

        font.setColor(Color.WHITE);
        glyphLayout.setText(font, "Crafting");
        float titleX = craftPanelX + craftPanelPadding;
        float titleY = craftPanelY + craftPanelHeight - craftPanelPadding * 0.5f;
        font.draw(spriteBatch, glyphLayout, titleX, titleY);

        glyphLayout.setText(font, "Choose an item to see recipe");
        font.setColor(new Color(0.82f, 0.84f, 0.9f, 0.8f));
        font.draw(spriteBatch, glyphLayout, titleX, titleY - glyphLayout.height - 2f);
        font.setColor(Color.WHITE);

        // Icons
        for (int i = 0; i < craftingSystem.getRecipes().size(); i++) {
            CraftingRecipe recipe = craftingSystem.getRecipes().get(i);
            ItemDefinition def = itemRegistry.get(recipe.resultItemId());
            TextureRegion icon = def != null ? def.icon() : null;
            if (hasIcon(icon)) {
                spriteBatch.draw(icon, iconX(i) + 4f, iconY(i) + 4f, craftIconSize - 8f, craftIconSize - 8f);
            }
            boolean canCraft = craftingSystem.canCraft(i);
            if (!canCraft) {
                spriteBatch.setColor(0f, 0f, 0f, 0.4f);
                spriteBatch.draw(whiteRegion, iconX(i), iconY(i), craftIconSize, craftIconSize);
                spriteBatch.setColor(Color.WHITE);
            }

            // Selection ring
            if (selectedRecipeIndex == i) {
                spriteBatch.setColor(1f, 0.84f, 0.35f, 0.9f);
                spriteBatch.draw(whiteRegion, iconX(i) - 2f, iconY(i) - 2f, craftIconSize + 4f, 3f);
                spriteBatch.draw(whiteRegion, iconX(i) - 2f, iconY(i) + craftIconSize - 1f, craftIconSize + 4f, 3f);
                spriteBatch.draw(whiteRegion, iconX(i) - 2f, iconY(i) - 2f, 3f, craftIconSize + 4f);
                spriteBatch.draw(whiteRegion, iconX(i) + craftIconSize - 1f, iconY(i) - 2f, 3f, craftIconSize + 4f);
                spriteBatch.setColor(Color.WHITE);
            } else if (hoveredRecipeIndex == i) {
                spriteBatch.setColor(1f, 1f, 1f, 0.18f);
                spriteBatch.draw(whiteRegion, iconX(i) - 2f, iconY(i) - 2f, craftIconSize + 4f, craftIconSize + 4f);
                spriteBatch.setColor(Color.WHITE);
            }
        }

        // Detail area
        int detailIndex = selectedRecipeIndex >= 0 && selectedRecipeIndex < craftingSystem.getRecipes().size()
                ? selectedRecipeIndex
                : (craftingSystem.getRecipes().isEmpty() ? -1 : 0);
        if (detailIndex == -1) {
            return;
        }
        CraftingRecipe recipe = craftingSystem.getRecipes().get(detailIndex);
        boolean hasIngredients = craftingSystem.hasAllIngredients(recipe);
        boolean canCraft = craftingSystem.canCraft(detailIndex);
        boolean blockedBySpace = hasIngredients && !canCraft;

        float detailX = craftPanelX + craftPanelPadding + 10f;
        float detailTop = detailY() + craftDetailHeight - 12f;

        font.setColor(Color.WHITE);
        font.draw(spriteBatch, recipe.displayName(), detailX, detailTop);
        font.setColor(new Color(0.82f, 0.84f, 0.9f, 0.8f));
        glyphLayout.setText(font, "Ingredients");
        font.draw(spriteBatch, glyphLayout, detailX, detailTop - glyphLayout.height - 4f);
        font.setColor(Color.WHITE);

        float ingSize = 36f;
        float ingGap = 12f;
        float ingX = detailX;
        float ingY = detailY() + 22f;
        for (var ingredient : recipe.ingredients()) {
            ItemDefinition def = itemRegistry.get(ingredient.itemId());
            TextureRegion icon = def != null ? def.icon() : null;
            int owned = countInInventory(inventory, ingredient.itemId());
            boolean enough = owned >= ingredient.count();

            if (hasIcon(icon)) {
                if (!enough) {
                    spriteBatch.setColor(1f, 0.6f, 0.55f, 1f);
                }
                spriteBatch.draw(icon, ingX, ingY, ingSize, ingSize);
                spriteBatch.setColor(Color.WHITE);
            }
            String needText = owned + "/" + ingredient.count();
            glyphLayout.setText(font, needText);
            font.setColor(enough ? new Color(0.82f, 0.86f, 0.9f, 0.95f) : new Color(1f, 0.65f, 0.65f, 1f));
            font.draw(spriteBatch, needText, ingX, ingY - 4f);
            font.setColor(Color.WHITE);

            ingX += ingSize + ingGap;
        }

        // Arrow and result
        float resultSize = 44f;
        float arrowX = detailButtonX() - resultSize - 26f;
        float arrowY = detailY() + craftDetailHeight * 0.5f + glyphLayout.height * 0.2f;
        glyphLayout.setText(font, "→");
        font.setColor(new Color(0.9f, 0.9f, 0.95f, 1f));
        font.draw(spriteBatch, glyphLayout, arrowX, arrowY);
        font.setColor(Color.WHITE);

        ItemDefinition resultDef = itemRegistry.get(recipe.resultItemId());
        TextureRegion resultIcon = resultDef != null ? resultDef.icon() : null;
        float resultX = detailButtonX() - resultSize - glyphLayout.width - 12f;
        float resultY = detailY() + (craftDetailHeight - resultSize) * 0.55f;
        if (resultIcon != null) {
            spriteBatch.draw(resultIcon, resultX, resultY, resultSize, resultSize);
        }
        if (recipe.resultCount() > 1) {
            String countText = Integer.toString(recipe.resultCount());
            glyphLayout.setText(font, countText);
            float textX = resultX + resultSize - glyphLayout.width + 2f;
            float textY = resultY + glyphLayout.height + 2f;
            drawCount(spriteBatch, countText, textX, textY, glyphLayout.width, glyphLayout.height);
        }

        // Craft button
        Color buttonColor;
        if (canCraft) {
            buttonColor = craftButtonHovered ? new Color(0.34f, 0.82f, 0.45f, 1f) : new Color(0.25f, 0.72f, 0.38f, 0.95f);
        } else if (blockedBySpace) {
            buttonColor = new Color(0.95f, 0.62f, 0.26f, 0.92f);
        } else {
            buttonColor = new Color(0.35f, 0.37f, 0.4f, 0.9f);
        }
        spriteBatch.setColor(buttonColor);
        float btnX = detailButtonX();
        float btnY = detailButtonY();
        spriteBatch.draw(whiteRegion, btnX, btnY, craftButtonWidth, craftButtonHeight);
        spriteBatch.setColor(1f, 1f, 1f, 0.2f);
        spriteBatch.draw(whiteRegion, btnX, btnY + craftButtonHeight - 3f, craftButtonWidth, 3f);
        spriteBatch.setColor(Color.WHITE);

        String buttonText;
        if (canCraft) {
            buttonText = "Craft";
        } else if (blockedBySpace) {
            buttonText = "No space";
        } else {
            buttonText = "Missing";
        }
        glyphLayout.setText(font, buttonText);
        float textX = btnX + (craftButtonWidth - glyphLayout.width) * 0.5f;
        float textY = btnY + (craftButtonHeight + glyphLayout.height) * 0.5f;
        font.setColor(Color.WHITE);
        font.draw(spriteBatch, glyphLayout, textX, textY);
    }

    private int countInInventory(Inventory inventory, String itemId) {
        int total = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if (stack != null && stack.itemId().equals(itemId)) {
                total += stack.count();
            }
        }
        return total;
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
                if (!hasIcon(icon)) {
                    continue;
                }
                float iconPadding = 8f;
                float drawSize = cellSize - iconPadding * 2f;
                float drawX = lastOriginX + col * (cellSize + gap) + iconPadding;
                float drawY = rowY(row) + iconPadding;
                spriteBatch.draw(icon, drawX, drawY, drawSize, drawSize);

                String countText = Integer.toString(stack.count());
                glyphLayout.setText(font, countText);
                float textX = drawX + drawSize - glyphLayout.width + 2f;
                float textY = drawY + glyphLayout.height + 2f;
                drawCount(spriteBatch, countText, textX, textY, glyphLayout.width, glyphLayout.height);

                int maxDurability = def.maxDurability();
                if (maxDurability > 0) {
                    float ratio = Math.max(0f, Math.min(1f, stack.durability() / (float) maxDurability));
                    float barPad = 6f;
                    float barHeight = 5f;
                    float barWidth = cellSize - barPad * 2f;
                    float barX = lastOriginX + col * (cellSize + gap) + barPad;
                    float barY = rowY(row) + 4f;
                    drawDurabilityBar(spriteBatch, barX, barY, barWidth, barHeight, ratio);
                }
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
        if (!hasIcon(icon)) {
            return;
        }
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

    private void drawDurabilityBar(SpriteBatch batch, float x, float y, float width, float height, float ratio) {
        // Background
        batch.setColor(0f, 0f, 0f, 0.65f);
        batch.draw(whiteRegion, x, y, width, height);
        // Fill
        float r = 0.9f - 0.7f * ratio;   // red to green
        float g = 0.25f + 0.55f * ratio;
        float b = 0.2f + 0.2f * ratio;
        batch.setColor(r, g, b, 1f);
        batch.draw(whiteRegion, x, y, width * ratio, height);
        batch.setColor(Color.WHITE);
    }

    private void renderHealth(Viewport viewport, com.droiddungeon.player.PlayerStats stats) {
        if (stats == null) {
            return;
        }
        float barWidth = Inventory.HOTBAR_SLOTS * cellSize + (Inventory.HOTBAR_SLOTS - 1) * gap;
        float barHeight = healthBarHeight;
        float x = lastOriginX;
        float y = healthBarY;

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

        shapeRenderer.setColor(restartHovered ? new Color(0.32f, 0.65f, 0.9f, 1f) : new Color(0.24f, 0.38f, 0.52f, 1f));
        shapeRenderer.rect(btn.x, btn.y, btn.width, btn.height);
        shapeRenderer.setColor(1f, 1f, 1f, 0.25f);
        shapeRenderer.rect(btn.x, btn.y, btn.width, 4f);
        shapeRenderer.end();

        spriteBatch.begin();
        String title = "Game Over";
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

    public int hitTestSlot(Viewport viewport, float screenX, float screenY, boolean inventoryOpen, int recipeCount) {
        cacheLayout(viewport, inventoryOpen, recipeCount);
        Vector2 world = new Vector2(screenX, screenY);
        viewport.unproject(world);

        for (int row = 0; row < lastRows; row++) {
            for (int col = 0; col < Inventory.HOTBAR_SLOTS; col++) {
                float x = lastOriginX + col * (cellSize + gap);
                float y = rowY(row);
                if (world.x >= x && world.x <= x + cellSize && world.y >= y && world.y <= y + cellSize) {
                    return row * Inventory.HOTBAR_SLOTS + col;
                }
            }
        }
        return -1;
    }

    public CraftingHit hitTestCrafting(Viewport viewport, float screenX, float screenY, boolean inventoryOpen, int recipeCount) {
        cacheLayout(viewport, inventoryOpen, recipeCount);
        if (!craftingVisible) {
            return CraftingHit.none();
        }
        Vector2 world = new Vector2(screenX, screenY);
        viewport.unproject(world);

        boolean insidePanel = world.x >= craftPanelX && world.x <= craftPanelX + craftPanelWidth
                && world.y >= craftPanelY && world.y <= craftPanelY + craftPanelHeight;
        if (!insidePanel) {
            return CraftingHit.none();
        }

        int iconIndex = -1;
        for (int i = 0; i < recipeCount; i++) {
            float x = iconX(i);
            float y = iconY(i);
            if (world.x >= x && world.x <= x + craftIconSize &&
                    world.y >= y && world.y <= y + craftIconSize) {
                iconIndex = i;
                break;
            }
        }

        boolean onButton = false;
        float btnX = detailButtonX();
        float btnY = detailButtonY();
        if (world.x >= btnX && world.x <= btnX + craftButtonWidth &&
                world.y >= btnY && world.y <= btnY + craftButtonHeight) {
            onButton = true;
        }

        return new CraftingHit(iconIndex, true, onButton);
    }

    private boolean hasIcon(TextureRegion icon) {
        return icon != null && icon.getTexture() != null && icon.getRegionWidth() > 0 && icon.getRegionHeight() > 0;
    }

    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
    }
}
