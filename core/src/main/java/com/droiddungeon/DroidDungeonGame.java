package com.droiddungeon;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.input.HeldMovementController;
import com.droiddungeon.render.WorldRenderer;

public class DroidDungeonGame extends ApplicationAdapter {
    private Stage stage;
    private WorldRenderer worldRenderer;
    private HeldMovementController movementController;

    private Grid grid;
    private Player player;

    @Override
    public void create() {
        stage = new Stage(new ScreenViewport());

        grid = new Grid(20, 12, 48f);
        player = new Player(grid.getColumns() / 2, grid.getRows() / 2);

        worldRenderer = new WorldRenderer();
        movementController = new HeldMovementController();

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

        float delta = Gdx.graphics.getDeltaTime();
        stage.act(delta);

        movementController.update(grid, player);
        player.update(delta, 10f);

        worldRenderer.render(stage, grid, player);

        stage.draw();
    }

    @Override
    public void dispose() {
        stage.dispose();
        worldRenderer.dispose();
    }
}
