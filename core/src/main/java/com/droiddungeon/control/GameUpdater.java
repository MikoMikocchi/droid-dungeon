package com.droiddungeon.control;

import com.badlogic.gdx.math.Vector2;
import com.droiddungeon.config.GameConfig;
import com.droiddungeon.input.InputFrame;
import com.droiddungeon.input.HeldMovementController;
import com.droiddungeon.runtime.GameContext;
import com.droiddungeon.runtime.GameUpdateResult;
import com.droiddungeon.systems.CameraController;
import com.droiddungeon.systems.WeaponSystem;

/**
 * Runs per-frame world updates (movement, combat, AI).
 */
public final class GameUpdater {
    private final GameConfig config;
    private final CameraController cameraController;
    private final HeldMovementController movementController;

    public GameUpdater(GameConfig config, CameraController cameraController, HeldMovementController movementController) {
        this.config = config;
        this.cameraController = cameraController;
        this.movementController = movementController;
    }

    public GameUpdateResult update(float delta, boolean mapOpen, boolean dead, InputFrame input, GameContext ctx, float worldViewportTileSize, Vector2 mouseWorld) {
        float gridOriginX;
        float gridOriginY;
        WeaponSystem.WeaponState weaponState;

        if (!dead && !mapOpen) {
            if (input.dropRequested()) {
                ctx.inventorySystem().dropCurrentStack(ctx.player());
            }
            if (input.pickUpRequested()) {
                ctx.inventorySystem().pickUpItemsAtPlayer(ctx.player());
            }
            if (input.slotClicked()) {
                ctx.inventorySystem().onSlotClicked(input.slotUnderCursor());
            }

            if (!ctx.inventorySystem().isInventoryOpen()) {
                movementController.update(ctx.grid(), ctx.player(), ctx.enemySystem());
            }
            ctx.companionSystem().updateFollowerTrail(ctx.player().getGridX(), ctx.player().getGridY());
            ctx.player().update(delta, config.playerSpeedTilesPerSecond());
            ctx.companionSystem().updateRender(delta);

            cameraController.update(ctx.grid(), ctx.player(), delta);
            gridOriginX = cameraController.getGridOriginX();
            gridOriginY = cameraController.getGridOriginY();

            weaponState = ctx.weaponSystem().update(
                    delta,
                    ctx.player(),
                    ctx.inventorySystem().getEquippedStack(),
                    mouseWorld,
                    gridOriginX,
                    gridOriginY,
                    worldViewportTileSize,
                    ctx.inventorySystem().isInventoryOpen(),
                    input.pointerOnUi()
            );

            ctx.enemySystem().update(delta, ctx.player(), ctx.playerStats(), weaponState);
        } else {
            cameraController.update(ctx.grid(), ctx.player(), delta);
            gridOriginX = cameraController.getGridOriginX();
            gridOriginY = cameraController.getGridOriginY();
            weaponState = WeaponSystem.WeaponState.inactive();
        }

        return new GameUpdateResult(gridOriginX, gridOriginY, weaponState);
    }
}
