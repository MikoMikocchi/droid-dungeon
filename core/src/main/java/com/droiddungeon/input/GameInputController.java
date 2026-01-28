package com.droiddungeon.input;

import com.badlogic.gdx.Gdx;
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

        int slotUnderCursor = hudRenderer.hitTestSlot(
                uiViewport,
                Gdx.input.getX(),
                Gdx.input.getY(),
                inventorySystem.isInventoryOpen()
        );
        int hoveredSlot = inventorySystem.isInventoryOpen() ? slotUnderCursor : -1;
        boolean slotClicked = Gdx.input.justTouched() && slotUnderCursor != -1;
        boolean pointerOnUi = slotUnderCursor != -1;

        boolean dropRequested = bindings.isJustPressed(InputAction.DROP_ITEM);
        boolean pickUpRequested = bindings.isJustPressed(InputAction.PICK_UP_ITEM);
        boolean mapToggleRequested = bindings.isJustPressed(InputAction.TOGGLE_MAP);
        boolean mapCloseRequested = bindings.isJustPressed(InputAction.CLOSE_MAP);
        boolean restartRequested = bindings.isJustPressed(InputAction.RESTART_RUN);

        return new InputFrame(
                slotUnderCursor,
                hoveredSlot,
                pointerOnUi,
                slotClicked,
                dropRequested,
                pickUpRequested,
                mapToggleRequested,
                mapCloseRequested,
                restartRequested
        );
    }
}
