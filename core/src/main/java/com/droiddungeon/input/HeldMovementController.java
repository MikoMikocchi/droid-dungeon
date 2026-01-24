package com.droiddungeon.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;

public final class HeldMovementController {
    private Direction preferredDir = Direction.RIGHT;

    public void update(Grid grid, Player player) {
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
            preferredDir = Direction.LEFT;
        }
        if (rightPressed) {
            preferredDir = Direction.RIGHT;
        }
        if (upPressed) {
            preferredDir = Direction.UP;
        }
        if (downPressed) {
            preferredDir = Direction.DOWN;
        }

        Direction chosen = chooseDirection(preferredDir, leftHeld, rightHeld, upHeld, downHeld);
        if (chosen == null) {
            return;
        }

        player.tryMoveBy(chosen.dx, chosen.dy, grid);
    }

    private static Direction chooseDirection(
            Direction preferredDir,
            boolean leftHeld,
            boolean rightHeld,
            boolean upHeld,
            boolean downHeld
    ) {
        if (isHeld(preferredDir, leftHeld, rightHeld, upHeld, downHeld)) {
            return preferredDir;
        }
        if (leftHeld && !rightHeld) {
            return Direction.LEFT;
        }
        if (rightHeld && !leftHeld) {
            return Direction.RIGHT;
        }
        if (upHeld && !downHeld) {
            return Direction.UP;
        }
        if (downHeld && !upHeld) {
            return Direction.DOWN;
        }
        if (leftHeld || rightHeld || upHeld || downHeld) {
            return leftHeld ? Direction.LEFT : (rightHeld ? Direction.RIGHT : (upHeld ? Direction.UP : Direction.DOWN));
        }
        return null;
    }

    private static boolean isHeld(Direction dir, boolean leftHeld, boolean rightHeld, boolean upHeld, boolean downHeld) {
        return switch (dir) {
            case LEFT -> leftHeld;
            case RIGHT -> rightHeld;
            case UP -> upHeld;
            case DOWN -> downHeld;
        };
    }
}
