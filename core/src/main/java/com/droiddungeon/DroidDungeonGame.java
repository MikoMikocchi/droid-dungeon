package com.droiddungeon;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.GridInputController;
import com.droiddungeon.grid.Player;

public class DroidDungeonGame extends ApplicationAdapter {
    private Stage stage;
    private ShapeRenderer shapeRenderer;

    private Grid grid;
    private Player player;

    @Override
    public void create() {
        stage = new Stage(new ScreenViewport());

        grid = new Grid(20, 12, 48f);
        player = new Player(grid.getColumns() / 2, grid.getRows() / 2);

        shapeRenderer = new ShapeRenderer();

        InputMultiplexer multiplexer = new InputMultiplexer(
                stage,
                new GridInputController(grid, player, stage)
        );
        Gdx.input.setInputProcessor(multiplexer);
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(Gdx.graphics.getDeltaTime());

        stage.getViewport().apply();
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);

        float gridOriginX = (stage.getViewport().getWorldWidth() - grid.getWorldWidth()) * 0.5f;
        float gridOriginY = (stage.getViewport().getWorldHeight() - grid.getWorldHeight()) * 0.5f;
        float tileSize = grid.getTileSize();

        // Tile fill (subtle checker pattern)
        shapeRenderer.begin(ShapeType.Filled);
        for (int y = 0; y < grid.getRows(); y++) {
            for (int x = 0; x < grid.getColumns(); x++) {
                if (((x + y) & 1) == 0) {
                    shapeRenderer.setColor(0.06f, 0.06f, 0.07f, 1f);
                } else {
                    shapeRenderer.setColor(0.04f, 0.04f, 0.05f, 1f);
                }
                shapeRenderer.rect(
                        gridOriginX + x * tileSize,
                        gridOriginY + y * tileSize,
                        tileSize,
                        tileSize
                );
            }
        }
        shapeRenderer.end();

        // Grid lines
        shapeRenderer.begin(ShapeType.Line);
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 1f);

        for (int x = 0; x <= grid.getColumns(); x++) {
            float worldX = gridOriginX + x * tileSize;
            shapeRenderer.line(worldX, gridOriginY, worldX, gridOriginY + grid.getWorldHeight());
        }
        for (int y = 0; y <= grid.getRows(); y++) {
            float worldY = gridOriginY + y * tileSize;
            shapeRenderer.line(gridOriginX, worldY, gridOriginX + grid.getWorldWidth(), worldY);
        }
        shapeRenderer.end();

        // Player (circle that occupies 1 tile)
        float centerX = gridOriginX + (player.getGridX() + 0.5f) * tileSize;
        float centerY = gridOriginY + (player.getGridY() + 0.5f) * tileSize;
        float radius = tileSize * 0.42f;

        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0.15f, 0.75f, 1f, 1f);
        shapeRenderer.circle(centerX, centerY, radius, MathUtils.clamp((int) (radius * 2.5f), 16, 64));
        shapeRenderer.end();

        stage.draw();
    }

    @Override
    public void dispose() {
        stage.dispose();
        shapeRenderer.dispose();
    }
}
