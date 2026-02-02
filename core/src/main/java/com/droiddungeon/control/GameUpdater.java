package com.droiddungeon.control;

import com.badlogic.gdx.math.Vector2;
import com.droiddungeon.config.GameConfig;
import com.droiddungeon.grid.Player;
import com.droiddungeon.input.HeldMovementController;
import com.droiddungeon.input.InputFrame;
import com.droiddungeon.player.PlayerStats;
import com.droiddungeon.runtime.GameContext;
import com.droiddungeon.runtime.GameUpdateResult;
import com.droiddungeon.systems.CameraController;
import com.droiddungeon.systems.WeaponSystem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Runs per-frame world updates (movement, combat, AI). */
public final class GameUpdater {
  private final GameConfig config;
  private final CameraController cameraController;
  private final HeldMovementController movementController;

  public GameUpdater(
      GameConfig config,
      CameraController cameraController,
      HeldMovementController movementController) {
    this.config = config;
    this.cameraController = cameraController;
    this.movementController = movementController;
  }

  public GameUpdateResult update(
      float delta,
      boolean dead,
      InputFrame input,
      GameContext ctx,
      float worldViewportTileSize,
      boolean mapOpen,
      boolean simulateEnemies) {
    float gridOriginX;
    float gridOriginY;
    WeaponSystem.WeaponState weaponState;

    if (!dead) {
      if (input.dropRequested()) {
        ctx.inventorySystem().dropCurrentStack(ctx.player());
      }
      if (input.pickUpRequested()) {
        ctx.inventorySystem().pickUpItemsAtPlayer(ctx.player());
      }
      if (input.slotClicked()) {
        ctx.inventorySystem().onSlotClicked(input.slotUnderCursor());
      }
      if (input.recipeSelectClickIndex() != -1) {
        ctx.inventorySystem().selectRecipe(input.recipeSelectClickIndex());
      }
      if (input.craftButtonClicked()) {
        ctx.inventorySystem().craftSelectedRecipe();
      }

      if (!ctx.inventorySystem().isInventoryOpen() && !mapOpen) {
        movementController.update(
            ctx.grid(), ctx.player(), ctx.entityWorld(), input.movementIntent());
      }
      ctx.companionSystem().updateFollowerTrail(ctx.player().getGridX(), ctx.player().getGridY());
      ctx.player().update(delta, config.playerSpeedTilesPerSecond());
      ctx.companionSystem().updateRender(delta);

      if (cameraController != null) {
        cameraController.update(ctx.grid(), ctx.player(), delta);
        gridOriginX = cameraController.getGridOriginX();
        gridOriginY = cameraController.getGridOriginY();
      } else {
        gridOriginX = 0f;
        gridOriginY = 0f;
      }

      weaponState =
          ctx.weaponSystem()
              .update(
                  delta,
                  ctx.player(),
                  ctx.inventorySystem().getEquippedStack(),
                  input.weaponInput(),
                  gridOriginX,
                  gridOriginY,
                  worldViewportTileSize,
                  ctx.inventorySystem().isInventoryOpen(),
                  input.pointerOnUi());

      boolean canInteract =
          !ctx.inventorySystem().isInventoryOpen() && !mapOpen && !input.pointerOnUi();
      ctx.miningSystem()
          .update(
              delta,
              ctx.player(),
              ctx.inventorySystem().getEquippedStack(),
              input.weaponInput() != null
                  ? new Vector2(input.weaponInput().aimWorldX(), input.weaponInput().aimWorldY())
                  : new Vector2(),
              worldViewportTileSize,
              canInteract,
              canInteract && input.mineRequested());

      if (simulateEnemies) {
        // singleplayer/simulation mode: update enemies using single-player context
        List<Player> players = List.of(ctx.player());
        Map<Integer, PlayerStats> stats = new HashMap<>();
        stats.put(ctx.player().id(), ctx.playerStats());
        ctx.enemySystem().update(delta, players, stats);
      }
    } else {
      if (cameraController != null) {
        cameraController.update(ctx.grid(), ctx.player(), delta);
        gridOriginX = cameraController.getGridOriginX();
        gridOriginY = cameraController.getGridOriginY();
      } else {
        gridOriginX = 0f;
        gridOriginY = 0f;
      }
      weaponState = WeaponSystem.WeaponState.inactive();
      ctx.miningSystem()
          .update(
              delta,
              ctx.player(),
              ctx.inventorySystem().getEquippedStack(),
              input.weaponInput() != null
                  ? new Vector2(input.weaponInput().aimWorldX(), input.weaponInput().aimWorldY())
                  : new Vector2(),
              worldViewportTileSize,
              false,
              false);
    }

    return new GameUpdateResult(gridOriginX, gridOriginY, weaponState, 0, -1L);
  }
}
