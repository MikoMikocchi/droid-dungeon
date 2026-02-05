package com.droiddungeon.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.systems.InventorySystem;
import com.droiddungeon.ui.HudRenderer;

/** Converts raw input into high-level, bindable actions and UI hit tests. */
public final class GameInputController {
  private final InputBindings bindings;
  private final HudRenderer hudRenderer;

  public GameInputController(InputBindings bindings, HudRenderer hudRenderer) {
    this.bindings = bindings;
    this.hudRenderer = hudRenderer;
  }

  public InputFrame collect(
      Viewport uiViewport, Viewport worldViewport, InventorySystem inventorySystem) {
    // hotbar selection / inventory toggle are handled inside the system for now
    inventorySystem.updateInput();

    boolean chestOpen = inventorySystem.isChestOpen();
    int recipeCount = chestOpen ? 0 : inventorySystem.getCraftingSystem().getRecipes().size();
    int slotUnderCursor =
        hudRenderer.hitTestSlot(
            uiViewport,
            Gdx.input.getX(),
            Gdx.input.getY(),
            inventorySystem.isInventoryOpen(),
            chestOpen,
            recipeCount,
            inventorySystem.getChestSlotCount());
    var craftHit =
        chestOpen
            ? HudRenderer.CraftingHit.none()
            : hudRenderer.hitTestCrafting(
                uiViewport,
                Gdx.input.getX(),
                Gdx.input.getY(),
                inventorySystem.isInventoryOpen(),
                recipeCount,
                chestOpen,
                inventorySystem.getChestSlotCount());
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
    boolean interactRequested = Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT) && !pointerOnUi;
    boolean debugToggleRequested = bindings.isJustPressed(InputAction.TOGGLE_DEBUG);

    MovementIntent movementIntent =
        new MovementIntent(
            Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A),
            Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D),
            Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W),
            Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S),
            Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A),
            Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)
                || Gdx.input.isKeyJustPressed(Input.Keys.D),
            Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W),
            Gdx.input.isKeyJustPressed(Input.Keys.DOWN)
                || Gdx.input.isKeyJustPressed(Input.Keys.S));

    var mouseWorld =
        worldViewport.unproject(
            new com.badlogic.gdx.math.Vector2(Gdx.input.getX(), Gdx.input.getY()));
    WeaponInput weaponInput =
        new WeaponInput(
            Gdx.input.isButtonJustPressed(Input.Buttons.LEFT),
            Gdx.input.isButtonPressed(Input.Buttons.LEFT),
            mouseWorld.x,
            mouseWorld.y);

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
        interactRequested,
        debugToggleRequested,
        movementIntent,
        weaponInput);
  }
}
