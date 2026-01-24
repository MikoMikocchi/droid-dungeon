package com.droiddungeon;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.input.HeldMovementController;
import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.render.WorldRenderer;
import com.droiddungeon.ui.HudRenderer;

public class DroidDungeonGame extends ApplicationAdapter {
    private Stage stage;
    private WorldRenderer worldRenderer;
    private HudRenderer hudRenderer;
    private HeldMovementController movementController;

    private Grid grid;
    private Player player;

    private Inventory inventory;
    private boolean inventoryOpen;
    private int selectedSlotIndex;

    @Override
    public void create() {
        stage = new Stage(new ScreenViewport());

        grid = new Grid(20, 12, 48f);
        player = new Player(grid.getColumns() / 2, grid.getRows() / 2);

        worldRenderer = new WorldRenderer();
        hudRenderer = new HudRenderer();
        movementController = new HeldMovementController();

        inventory = new Inventory();
        inventoryOpen = false;
        selectedSlotIndex = 0;

        // Input is handled by polling (for held-key movement)
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            inventoryOpen = !inventoryOpen;
        }

        int hotbarKeySlot = pollHotbarNumberKey();
        if (hotbarKeySlot != -1) {
            selectedSlotIndex = hotbarKeySlot;
        }

        float delta = Gdx.graphics.getDeltaTime();
        stage.act(delta);

        if (Gdx.input.justTouched()) {
            int clicked = hudRenderer.hitTestSlot(stage, Gdx.input.getX(), Gdx.input.getY());
            if (clicked != -1) {
                selectedSlotIndex = clicked;
            }
        }

        if (!inventoryOpen) {
            movementController.update(grid, player);
        }
        player.update(delta, 10f);

        worldRenderer.render(stage, grid, player);
        hudRenderer.render(stage, inventory, inventoryOpen, selectedSlotIndex, delta);

        stage.draw();
    }

    @Override
    public void dispose() {
        stage.dispose();
        worldRenderer.dispose();
        hudRenderer.dispose();
    }

    private static int pollHotbarNumberKey() {
        // 1..9 -> slots 0..8, 0 -> slot 9.
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_1)) return 0;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_2)) return 1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_3)) return 2;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_4)) return 3;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_5) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_5)) return 4;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_6) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_6)) return 5;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_7) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_7)) return 6;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_8) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_8)) return 7;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_9) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_9)) return 8;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_0) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_0)) return 9;
        return -1;
    }
}
