package com.droiddungeon;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;

public class DroidDungeonGame extends ApplicationAdapter {
    private enum MoveDir {
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    private Stage stage;
    private ShapeRenderer shapeRenderer;

    private Grid grid;
    private Player player;

    private MoveDir preferredDir = MoveDir.RIGHT;

    @Override
    public void create() {
        stage = new Stage(new ScreenViewport());

        grid = new Grid(20, 12, 48f);
        player = new Player(grid.getColumns() / 2, grid.getRows() / 2);

        shapeRenderer = new ShapeRenderer();

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

        handleHeldMovement();
        player.update(delta, 10f);

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
        float centerX = gridOriginX + (player.getRenderX() + 0.5f) * tileSize;
        float centerY = gridOriginY + (player.getRenderY() + 0.5f) * tileSize;
        float radius = tileSize * 0.42f;

        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0.15f, 0.75f, 1f, 1f);
        shapeRenderer.circle(centerX, centerY, radius, MathUtils.clamp((int) (radius * 2.5f), 16, 64));
        shapeRenderer.end();

        stage.draw();
    }

    private void handleHeldMovement() {
        if (player.isMoving()) {
            return;
        }

        boolean leftHeld = Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A);
        boolean rightHeld = Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D);
        boolean upHeld = Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W);
        boolean downHeld = Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S);

        boolean leftPressed = Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A);
        boolean rightPressed = Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D);
        boolean upPressed = Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W);
        boolean downPressed = Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S);

        if (leftPressed) {
            preferredDir = MoveDir.LEFT;
        }
        if (rightPressed) {
            preferredDir = MoveDir.RIGHT;
        }
        if (upPressed) {
            preferredDir = MoveDir.UP;
        }
        if (downPressed) {
            preferredDir = MoveDir.DOWN;
        }

        MoveDir chosen = null;
        if (isHeld(preferredDir, leftHeld, rightHeld, upHeld, downHeld)) {
            chosen = preferredDir;
        } else if (leftHeld && !rightHeld) {
            chosen = MoveDir.LEFT;
        } else if (rightHeld && !leftHeld) {
            chosen = MoveDir.RIGHT;
        } else if (upHeld && !downHeld) {
            chosen = MoveDir.UP;
        } else if (downHeld && !upHeld) {
            chosen = MoveDir.DOWN;
        } else if (leftHeld || rightHeld || upHeld || downHeld) {
            chosen = leftHeld ? MoveDir.LEFT : (rightHeld ? MoveDir.RIGHT : (upHeld ? MoveDir.UP : MoveDir.DOWN));
        }

        if (chosen == null) {
            return;
        }

        int dx;
        int dy;
        switch (chosen) {
            case LEFT -> {
                dx = -1;
                dy = 0;
            }
            case RIGHT -> {
                dx = 1;
                dy = 0;
            }
            case UP -> {
                dx = 0;
                dy = 1;
            }
            case DOWN -> {
                dx = 0;
                dy = -1;
            }
            default -> {
                dx = 0;
                dy = 0;
            }
        }

        if (dx != 0 || dy != 0) {
            player.tryMoveBy(dx, dy, grid);
        }
    }

    private static boolean isHeld(MoveDir dir, boolean leftHeld, boolean rightHeld, boolean upHeld, boolean downHeld) {
        return switch (dir) {
            case LEFT -> leftHeld;
            case RIGHT -> rightHeld;
            case UP -> upHeld;
            case DOWN -> downHeld;
        };
    }

    @Override
    public void dispose() {
        stage.dispose();
        shapeRenderer.dispose();
    }
}
