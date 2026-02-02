package com.droiddungeon.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.droiddungeon.config.GameConfig;
import com.droiddungeon.control.GameUpdater;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.input.HeldMovementController;
import com.droiddungeon.input.InputFrame;
import com.droiddungeon.items.GroundItem;
import com.droiddungeon.items.ItemRegistry;
import com.droiddungeon.net.dto.PlayerSnapshotDto;
import com.droiddungeon.player.PlayerStats;
import com.droiddungeon.runtime.GameUpdateResult;
import com.droiddungeon.systems.CameraController;
import com.droiddungeon.systems.EnemySystem;
import com.droiddungeon.systems.MiningSystem;

/**
 * Minimal headless loop for server tick: does not rely on Gdx.graphics/Gdx.input. Adapts input DTOs
 * (InputFrame) to GameUpdater and provides a state snapshot.
 */
public final class ServerGameLoop {
  private final long worldSeed;
  private final GameUpdater updater;
  private final EnemySystem enemySystem;
  private final HeldMovementController movementController;
  private final Grid grid;
  private final PlayerSessionStore sessionStore;
  private final SnapshotFacade snapshotFacade;

  public ServerGameLoop(GameConfig config, ItemRegistry itemRegistry, long worldSeed) {
    this.worldSeed = worldSeed;
    this.movementController = new HeldMovementController();

    WorldInitializer.WorldState worldState =
        new WorldInitializer().initialize(config, itemRegistry, worldSeed);
    this.grid = worldState.grid();
    this.enemySystem = worldState.enemySystem();
    this.sessionStore =
        new PlayerSessionStore(worldState.contextFactory(), worldState.entityWorld());
    this.snapshotFacade = new SnapshotFacade(sessionStore, worldState.groundStore());

    // camera and viewport are not needed for the server, passing null
    CameraController cameraController = null;
    this.updater = new GameUpdater(config, cameraController, movementController);
  }

  public long worldSeed() {
    return worldSeed;
  }

  /**
   * Register a player session on server: creates per-player state and inserts player entity.
   *
   * @return last processed tick if a saved state exists, otherwise -1
   */
  public long registerPlayer(String playerId) {
    return sessionStore.registerPlayer(playerId);
  }

  /** Unregister player and clean up entities */
  public void unregisterPlayer(String playerId) {
    sessionStore.unregisterPlayer(playerId);
  }

  /** Perform one player-specific simulation step (does not update global AI; see updateGlobal). */
  public GameUpdateResult tickForPlayer(String playerId, InputFrame input, float deltaSeconds) {
    var s = sessionStore.getSession(playerId);
    if (s == null) return null;
    return updater.update(
        deltaSeconds,
        false,
        input,
        s.context,
        s.context.grid().getTileSize(),
        false,
        false // don't simulate enemies per-player
        );
  }

  /** Perform global per-tick updates (AI, spawns, etc) after player inputs processed. */
  public void updateGlobal(float deltaSeconds) {
    // collect players for AI usage
    List<Player> players = new ArrayList<>();
    Map<Integer, PlayerStats> stats = new HashMap<>();
    for (var s : sessionStore.sessions()) {
      players.add(s.player);
      stats.put(s.player.id(), s.stats);
    }
    enemySystem.update(deltaSeconds, players, stats);
  }

  public PlayerSnapshotDto playerSnapshotFor(String playerId, long lastProcessedTick) {
    return snapshotFacade.playerSnapshotFor(playerId, lastProcessedTick);
  }

  public Grid grid() {
    return grid;
  }

  public List<GroundItem> getGroundItems() {
    return snapshotFacade.getGroundItems();
  }

  public MiningSystem.MiningTarget getPlayerMiningTarget(String playerId) {
    return snapshotFacade.getPlayerMiningTarget(playerId);
  }

  public EnemySystem enemySystem() {
    return enemySystem;
  }

  /** Persist current session state for reconnects (in-memory only). */
  public void savePlayerState(String playerId, long lastProcessedTick) {
    sessionStore.savePlayerState(playerId, lastProcessedTick);
  }
}
