package com.droiddungeon.input;

import com.droiddungeon.entity.EntityWorld;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.input.MovementIntent;

public final class HeldMovementController {
    private Direction preferredDir = Direction.RIGHT;

    public void update(Grid grid, Player player, EntityWorld entityWorld, MovementIntent input) {
        if (player.isMoving()) {
            return;
        }

        boolean leftHeld = input.leftHeld();
        boolean rightHeld = input.rightHeld();
        boolean upHeld = input.upHeld();
        boolean downHeld = input.downHeld();

        boolean leftPressed = input.leftJustPressed();
        boolean rightPressed = input.rightJustPressed();
        boolean upPressed = input.upJustPressed();
        boolean downPressed = input.downJustPressed();

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

        int oldX = player.getGridX();
        int oldY = player.getGridY();
        int targetX = oldX + chosen.dx;
        int targetY = oldY + chosen.dy;
        if (entityWorld != null && entityWorld.isBlocked(targetX, targetY)) {
            return;
        }
        player.tryMoveBy(chosen.dx, chosen.dy, grid);
        if (entityWorld != null && (player.getGridX() != oldX || player.getGridY() != oldY)) {
            entityWorld.move(player, oldX, oldY, player.getGridX(), player.getGridY());
        }
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
