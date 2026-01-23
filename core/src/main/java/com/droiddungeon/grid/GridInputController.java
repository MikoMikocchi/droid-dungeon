package com.droiddungeon.grid;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;

public final class GridInputController extends InputAdapter {
    private final Grid grid;
    private final Player player;
    private final Stage stage;

    public GridInputController(Grid grid, Player player, Stage stage) {
        this.grid = grid;
        this.player = player;
        this.stage = stage;
    }

    @Override
    public boolean keyDown(int keycode) {
        return switch (keycode) {
            case Input.Keys.LEFT, Input.Keys.A -> moveBy(-1, 0);
            case Input.Keys.RIGHT, Input.Keys.D -> moveBy(1, 0);
            case Input.Keys.UP, Input.Keys.W -> moveBy(0, 1);
            case Input.Keys.DOWN, Input.Keys.S -> moveBy(0, -1);
            default -> false;
        };
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button != Input.Buttons.LEFT) {
            return false;
        }

        Vector2 pos = new Vector2(screenX, screenY);
        stage.screenToStageCoordinates(pos);

        float tileSize = grid.getTileSize();
        float gridOriginX = (stage.getViewport().getWorldWidth() - grid.getWorldWidth()) * 0.5f;
        float gridOriginY = (stage.getViewport().getWorldHeight() - grid.getWorldHeight()) * 0.5f;

        int targetX = (int) Math.floor((pos.x - gridOriginX) / tileSize);
        int targetY = (int) Math.floor((pos.y - gridOriginY) / tileSize);

        if (!grid.isInside(targetX, targetY)) {
            return false;
        }

        int dx = Math.abs(targetX - player.getGridX());
        int dy = Math.abs(targetY - player.getGridY());
        if (dx + dy != 1) {
            return false;
        }

        player.moveTo(targetX, targetY, grid);
        return true;
    }

    private boolean moveBy(int dx, int dy) {
        int beforeX = player.getGridX();
        int beforeY = player.getGridY();
        player.tryMoveBy(dx, dy, grid);
        return beforeX != player.getGridX() || beforeY != player.getGridY();
    }
}
