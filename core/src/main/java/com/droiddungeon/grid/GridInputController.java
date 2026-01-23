package com.droiddungeon.grid;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

public final class GridInputController extends InputAdapter {
    private final Grid grid;
    private final Player player;

    public GridInputController(Grid grid, Player player) {
        this.grid = grid;
        this.player = player;
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

    private boolean moveBy(int dx, int dy) {
        if (player.isMoving()) {
            return false;
        }
        int beforeX = player.getGridX();
        int beforeY = player.getGridY();
        player.tryMoveBy(dx, dy, grid);
        return beforeX != player.getGridX() || beforeY != player.getGridY();
    }
}
