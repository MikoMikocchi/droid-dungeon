package com.droiddungeon.server;

import com.droiddungeon.config.GameConfig;
import com.droiddungeon.control.GameUpdater;
import com.droiddungeon.entity.EntityWorld;
import com.droiddungeon.grid.DungeonGenerator;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.input.HeldMovementController;
import com.droiddungeon.input.InputFrame;
import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.items.GroundItem;
import com.droiddungeon.items.GroundItemStore;
import com.droiddungeon.items.ItemRegistry;
import com.droiddungeon.net.dto.PlayerSnapshotDto;
import com.droiddungeon.player.PlayerStats;
import com.droiddungeon.runtime.GameContext;
import com.droiddungeon.runtime.GameContextFactory;
import com.droiddungeon.runtime.GameUpdateResult;
import com.droiddungeon.systems.CameraController;
import com.droiddungeon.systems.CompanionSystem;
import com.droiddungeon.systems.EnemySystem;
import com.droiddungeon.systems.InventorySystem;
import com.droiddungeon.systems.MiningSystem;
import com.droiddungeon.systems.WeaponSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal headless loop for server tick: does not rely on Gdx.graphics/Gdx.input. Adapts input DTOs
 * (InputFrame) to GameUpdater and provides a state snapshot.
 */
public final class ServerGameLoop {
  private final GameConfig config;
  private final long worldSeed;
  private final GameUpdater updater;
  private final GameContextFactory contextFactory;
  private final EnemySystem enemySystem;
  private final HeldMovementController movementController;

  // world-shared state
  private final Grid grid;
  private final EntityWorld entityWorld;
  private final ItemRegistry itemRegistry;
  private final GroundItemStore groundStore;

  // per-player sessions
  private final Map<String, GameContextFactory.PlayerSession> sessions = new HashMap<>();

  public ServerGameLoop(GameConfig config, ItemRegistry itemRegistry, long worldSeed) {
    this.config = config;
    this.worldSeed = worldSeed;
    this.movementController = new HeldMovementController();

    DungeonGenerator.DungeonLayout layout =
        DungeonGenerator.generateInfinite(config.tileSize(), worldSeed);
    this.grid = layout.grid();
    int spawnX = layout.spawnX();
    int spawnY = layout.spawnY();

    this.entityWorld = new EntityWorld();
    this.itemRegistry = itemRegistry;
    this.groundStore = new GroundItemStore(entityWorld, itemRegistry);

    // enemy system is world-shared
    this.enemySystem = new EnemySystem(grid, worldSeed, entityWorld, groundStore);

    this.contextFactory =
        new GameContextFactory(
            config,
            grid,
            spawnX,
            spawnY,
            worldSeed,
            itemRegistry,
            entityWorld,
            enemySystem,
            groundStore);

    // camera and viewport are not needed for the server, passing null
    CameraController cameraController = null;
    this.updater = new GameUpdater(config, cameraController, movementController);
  }

  public long worldSeed() {
    return worldSeed;
  }

  /** Register a player session on server: creates per-player state and inserts player entity. */
  public void registerPlayer(String playerId) {
    if (sessions.containsKey(playerId)) return;
    var sf = contextFactory.createPlayerSession(playerId);
    sessions.put(playerId, sf);
    entityWorld.add(sf.player);
    entityWorld.add(sf.companion);
  }

  /** Unregister player and clean up entities */
  public void unregisterPlayer(String playerId) {
    var s = sessions.remove(playerId);
    if (s == null) return;
    entityWorld.remove(s.player);
    entityWorld.remove(s.companion);
  }

  /** Perform one player-specific simulation step (does not update global AI; see updateGlobal). */
  public GameUpdateResult tickForPlayer(String playerId, InputFrame input, float deltaSeconds) {
    var s = sessions.get(playerId);
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
    for (var s : sessions.values()) {
      players.add(s.player);
      stats.put(s.player.id(), s.stats);
    }
    enemySystem.update(deltaSeconds, players, stats);
  }

  public PlayerSnapshotDto playerSnapshotFor(String playerId, long lastProcessedTick) {
    var s = sessions.get(playerId);
    if (s == null) return null;
    return new PlayerSnapshotDto(
        playerId,
        s.player.getRenderX(),
        s.player.getRenderY(),
        s.player.getGridX(),
        s.player.getGridY(),
        s.stats.getHealth(),
        lastProcessedTick);
  }

  public Grid grid() {
    return grid;
  }

  public List<GroundItem> getGroundItems() {
    return groundStore.getGroundItems();
  }

  public MiningSystem.MiningTarget getPlayerMiningTarget(String playerId) {
    var s = sessions.get(playerId);
    if (s == null) return null;
    return s.mining.getTarget();
  }

  public EnemySystem enemySystem() {
    return enemySystem;
  }

  private static final class PlayerSession {
    private final String id;
    private final Player player;
    private final PlayerStats stats;
    private final CompanionSystem companion;
    private final Inventory inventory;
    private final InventorySystem inventorySystem;
    private final WeaponSystem weaponSystem;
    private final MiningSystem mining;
    private final GameContext ctx;

    PlayerSession(
        String id,
        Player player,
        PlayerStats stats,
        CompanionSystem companion,
        Inventory inventory,
        InventorySystem inventorySystem,
        WeaponSystem weaponSystem,
        MiningSystem mining,
        GameContext ctx) {
      this.id = id;
      this.player = player;
      this.stats = stats;
      this.companion = companion;
      this.inventory = inventory;
      this.inventorySystem = inventorySystem;
      this.weaponSystem = weaponSystem;
      this.mining = mining;
      this.ctx = ctx;
    }

    public String id() {
      return id;
    }

    public Player player() {
      return player;
    }

    public PlayerStats stats() {
      return stats;
    }

    public CompanionSystem companion() {
      return companion;
    }

    public InventorySystem inventorySystem() {
      return inventorySystem;
    }

    public WeaponSystem weaponSystem() {
      return weaponSystem;
    }

    public MiningSystem mining() {
      return mining;
    }

    public GameContext context() {
      return ctx;
    }
  }
}
