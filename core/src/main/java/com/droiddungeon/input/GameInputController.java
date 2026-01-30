package com.droiddungeon.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.systems.InventorySystem;
import com.droiddungeon.ui.HudRenderer;

/**
 * Converts raw input into high-level, bindable actions and UI hit tests.
 */
public final class GameInputController {
    private final InputBindings bindings;
    private final HudRenderer hudRenderer;

    public GameInputController(InputBindings bindings, HudRenderer hudRenderer) {
        this.bindings = bindings;
        this.hudRenderer = hudRenderer;
    }

    public InputFrame collect(Viewport uiViewport, InventorySystem inventorySystem) {
        // hotbar selection / inventory toggle are handled inside the system for now
        inventorySystem.updateInput();

        int recipeCount = inventorySystem.getCraftingSystem().getRecipes().size();
        int slotUnderCursor = hudRenderer.hitTestSlot(
                uiViewport,
                Gdx.input.getX(),
                Gdx.input.getY(),
                inventorySystem.isInventoryOpen(),
                recipeCount
        );
        var craftHit = hudRenderer.hitTestCrafting(
                uiViewport,
                Gdx.input.getX(),
                Gdx.input.getY(),
                inventorySystem.isInventoryOpen(),
                recipeCount
        );
        int hoveredSlot = inventorySystem.isInventoryOpen() ? slotUnderCursor : -1;
        int hoveredRecipe = inventorySystem.isInventoryOpen() ? craftHit.iconIndex() : -1;
        boolean slotClicked = Gdx.input.justTouched() && slotUnderCursor != -1;
        boolean recipeSelectClicked = Gdx.input.justTouched() && craftHit.iconIndex() != -1;
        boolean craftClicked = Gdx.input.justTouched() && craftHit.onCraftButton();
        boolean pointerOnUi = slotUnderCursor != -1 || craftHit.insidePanel();

        boolean dropRequested = bindings.isJustPressed(InputAction.DROP_ITEM);
        boolean pickUpRequested = bindings.isJustPressed(InputAction.PICK_UP_ITEM);
        boolean mapToggleRequested = bindings.isJustPressed(InputAction.TOGGLE_MAP);
        boolean mapCloseRequested = bindings.isJustPressed(InputAction.CLOSE_MAP);
        boolean restartRequested = bindings.isJustPressed(InputAction.RESTART_RUN);
        // Hold left mouse to mine; keep reporting while pressed for hold-to-break behavior.
        boolean mineRequested = Gdx.input.isButtonPressed(Input.Buttons.LEFT) && !pointerOnUi;
        boolean debugToggleRequested = bindings.isJustPressed(InputAction.TOGGLE_DEBUG);

        return new InputFrame(
                slotUnderCursor,
                hoveredSlot,
                hoveredRecipe,
                recipeSelectClicked ? craftHit.iconIndex() : -1,
                craftHit.onCraftButton(),
                craftClicked,
                pointerOnUi,
                slotClicked,
                dropRequested,
                pickUpRequested,
                mapToggleRequested,
                mapCloseRequested,
                restartRequested,
                mineRequested,
                debugToggleRequested
        );
    }
}
