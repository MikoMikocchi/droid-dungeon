package com.droiddungeon.server;

import com.droiddungeon.config.GameConfig;
import com.droiddungeon.control.GameUpdater;
import com.droiddungeon.entity.EntityWorld;
import com.droiddungeon.grid.DungeonGenerator;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.input.HeldMovementController;
import com.droiddungeon.input.InputFrame;
import com.droiddungeon.items.ItemRegistry;
import com.droiddungeon.runtime.GameContextFactory;
import com.droiddungeon.runtime.GameUpdateResult;
import com.droiddungeon.systems.CameraController;
import com.droiddungeon.systems.EnemySystem;

/**
 * Minimal headless loop for server tick: does not rely on Gdx.graphics/Gdx.input.
 * Adapts input DTOs (InputFrame) to GameUpdater and provides a state snapshot.
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
    private final com.droiddungeon.items.GroundItemStore groundStore;

    // per-player sessions
    private final java.util.Map<String, com.droiddungeon.runtime.GameContextFactory.PlayerSession> sessions = new java.util.HashMap<>();

    public ServerGameLoop(GameConfig config, ItemRegistry itemRegistry, long worldSeed) {
        this.config = config;
        this.worldSeed = worldSeed;
        this.movementController = new HeldMovementController();

        DungeonGenerator.DungeonLayout layout = DungeonGenerator.generateInfinite(config.tileSize(), worldSeed);
        this.grid = layout.grid();
        int spawnX = layout.spawnX();
        int spawnY = layout.spawnY();

        this.entityWorld = new EntityWorld();
        this.itemRegistry = itemRegistry;
        this.groundStore = new com.droiddungeon.items.GroundItemStore(entityWorld, itemRegistry);

        // enemy system is world-shared
        this.enemySystem = new EnemySystem(grid, worldSeed, entityWorld, groundStore);

        this.contextFactory = new GameContextFactory(
                config,
                grid,
                spawnX,
                spawnY,
                worldSeed,
                itemRegistry,
                entityWorld,
                enemySystem,
                groundStore
        );

        // camera and viewport are not needed for the server, passing null
        CameraController cameraController = null;
        this.updater = new GameUpdater(config, cameraController, movementController);
    }

    public long worldSeed() {
        return worldSeed;
    }

    /**
     * Register a player session on server: creates per-player state and inserts player entity.
     */
    public void registerPlayer(String playerId) {
        if (sessions.containsKey(playerId)) return;
        var sf = contextFactory.createPlayerSession(playerId);
        sessions.put(playerId, sf);
        entityWorld.add(sf.player);
        entityWorld.add(sf.companion);
    }

    /**
     * Unregister player and clean up entities
     */
    public void unregisterPlayer(String playerId) {
        var s = sessions.remove(playerId);
        if (s == null) return;
        entityWorld.remove(s.player);
        entityWorld.remove(s.companion);
    }

    /**
     * Perform one player-specific simulation step (does not update global AI; see updateGlobal).
     */
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

    /**
     * Perform global per-tick updates (AI, spawns, etc) after player inputs processed.
     */
    public void updateGlobal(float deltaSeconds) {
        // collect players for AI usage
        java.util.List<com.droiddungeon.grid.Player> players = new java.util.ArrayList<>();
        java.util.Map<Integer, com.droiddungeon.player.PlayerStats> stats = new java.util.HashMap<>();
        for (var s : sessions.values()) {
            players.add(s.player);
            stats.put(s.player.id(), s.stats);
        }
        enemySystem.update(deltaSeconds, players, stats);
    }

    public com.droiddungeon.net.dto.PlayerSnapshotDto playerSnapshotFor(String playerId, long lastProcessedTick) {
        var s = sessions.get(playerId);
        if (s == null) return null;
        return new com.droiddungeon.net.dto.PlayerSnapshotDto(
                playerId,
                s.player.getRenderX(),
                s.player.getRenderY(),
                s.player.getGridX(),
                s.player.getGridY(),
                s.stats.getHealth(),
                lastProcessedTick
        );
    }

    public Grid grid() { return grid; }

    public java.util.List<com.droiddungeon.items.GroundItem> getGroundItems() { return groundStore.getGroundItems(); }

    public com.droiddungeon.systems.MiningSystem.MiningTarget getPlayerMiningTarget(String playerId) {
        var s = sessions.get(playerId);
        if (s == null) return null;
        return s.mining.getTarget();
    }

    public com.droiddungeon.systems.EnemySystem enemySystem() { return enemySystem; }
    private static final class PlayerSession {
        private final String id;
        private final com.droiddungeon.grid.Player player;
        private final com.droiddungeon.player.PlayerStats stats;
        private final com.droiddungeon.systems.CompanionSystem companion;
        private final com.droiddungeon.inventory.Inventory inventory;
        private final com.droiddungeon.systems.InventorySystem inventorySystem;
        private final com.droiddungeon.systems.WeaponSystem weaponSystem;
        private final com.droiddungeon.systems.MiningSystem mining;
        private final com.droiddungeon.runtime.GameContext ctx;

        PlayerSession(String id, com.droiddungeon.grid.Player player, com.droiddungeon.player.PlayerStats stats, com.droiddungeon.systems.CompanionSystem companion, com.droiddungeon.inventory.Inventory inventory, com.droiddungeon.systems.InventorySystem inventorySystem, com.droiddungeon.systems.WeaponSystem weaponSystem, com.droiddungeon.systems.MiningSystem mining, com.droiddungeon.runtime.GameContext ctx) {
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

        public String id() { return id; }
        public com.droiddungeon.grid.Player player() { return player; }
        public com.droiddungeon.player.PlayerStats stats() { return stats; }
        public com.droiddungeon.systems.CompanionSystem companion() { return companion; }
        public com.droiddungeon.systems.InventorySystem inventorySystem() { return inventorySystem; }
        public com.droiddungeon.systems.WeaponSystem weaponSystem() { return weaponSystem; }
        public com.droiddungeon.systems.MiningSystem mining() { return mining; }
        public com.droiddungeon.runtime.GameContext context() { return ctx; }
    }
}
