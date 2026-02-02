package com.droiddungeon.runtime;

import com.badlogic.gdx.math.MathUtils;
import com.droiddungeon.config.GameConfig;
import com.droiddungeon.entity.EntityIds;
import com.droiddungeon.entity.EntityWorld;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.items.ItemRegistry;
import com.droiddungeon.player.PlayerStats;
import com.droiddungeon.systems.CompanionSystem;
import com.droiddungeon.systems.EnemySystem;
import com.droiddungeon.systems.InventorySystem;
import com.droiddungeon.systems.MiningSystem;
import com.droiddungeon.systems.WeaponSystem;
import com.droiddungeon.items.GroundItemStore;

/**
 * Builds GameContext instances for initial run and restarts.
 */
public final class GameContextFactory {
    private final GameConfig config;
    private final Grid grid;
    private final int spawnX;
    private final int spawnY;
    private final long worldSeed;
    private final ItemRegistry itemRegistry;
    private final EntityWorld entityWorld;
    private final EnemySystem enemySystem;
    private final GroundItemStore groundStore;

    public GameContextFactory(
            GameConfig config,
            Grid grid,
            int spawnX,
            int spawnY,
            long worldSeed,
            ItemRegistry itemRegistry,
            EntityWorld entityWorld,
            EnemySystem enemySystem,
            GroundItemStore groundStore
    ) {
        this.config = config;
        this.grid = grid;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.worldSeed = worldSeed;
        this.itemRegistry = itemRegistry;
        this.entityWorld = entityWorld;
        this.enemySystem = enemySystem;
        this.groundStore = groundStore;
    }

    public GameContext createContext() {
        entityWorld.clear();
        groundStore.clear();
        enemySystem.reset();

        Player player = new Player(EntityIds.next(), spawnX, spawnY);
        PlayerStats playerStats = new PlayerStats(100f);
        CompanionSystem companionSystem = new CompanionSystem(EntityIds.next(), player.getGridX(), player.getGridY(), config.companionDelayTiles(), config.companionSpeedTilesPerSecond(), entityWorld);
        WeaponSystem weaponSystem = setupWeapons();
        Inventory inventory = new Inventory();
        InventorySystem inventorySystem = new InventorySystem(inventory, itemRegistry, grid, entityWorld, groundStore);
        MiningSystem miningSystem = new MiningSystem(grid, inventorySystem, itemRegistry);
        entityWorld.add(player);
        entityWorld.add(companionSystem);

        return new GameContext(
                grid,
                entityWorld,
                player,
                playerStats,
                companionSystem,
                enemySystem,
                inventory,
                inventorySystem,
                itemRegistry,
                weaponSystem,
                miningSystem
        );
    }

    public PlayerSession createPlayerSession(String playerId) {
        Player player = new Player(EntityIds.next(), spawnX, spawnY);
        PlayerStats playerStats = new PlayerStats(100f);
        CompanionSystem companionSystem = new CompanionSystem(EntityIds.next(), player.getGridX(), player.getGridY(), config.companionDelayTiles(), config.companionSpeedTilesPerSecond(), entityWorld);
        Inventory inventory = new Inventory();
        InventorySystem inventorySystem = new InventorySystem(inventory, itemRegistry, grid, entityWorld, groundStore);
        WeaponSystem weaponSystem = setupWeapons();
        MiningSystem miningSystem = new MiningSystem(grid, inventorySystem, itemRegistry);
        entityWorld.add(player);
        entityWorld.add(companionSystem);
        GameContext ctx = new GameContext(grid, entityWorld, player, playerStats, companionSystem, enemySystem, inventory, inventorySystem, itemRegistry, weaponSystem, miningSystem);
        return new PlayerSession(playerId, player, playerStats, companionSystem, inventory, inventorySystem, weaponSystem, miningSystem, ctx);
    }

    // Lightweight holder used by server-side factory to produce session tuples
    public static final class PlayerSession {
        public final String id;
        public final Player player;
        public final PlayerStats stats;
        public final CompanionSystem companion;
        public final Inventory inventory;
        public final InventorySystem inventorySystem;
        public final WeaponSystem weaponSystem;
        public final MiningSystem mining;
        public final GameContext context;

        public PlayerSession(String id, Player player, PlayerStats stats, CompanionSystem companion, Inventory inventory, InventorySystem inventorySystem, WeaponSystem weaponSystem, MiningSystem mining, GameContext context) {
            this.id = id;
            this.player = player;
            this.stats = stats;
            this.companion = companion;
            this.inventory = inventory;
            this.inventorySystem = inventorySystem;
            this.weaponSystem = weaponSystem;
            this.mining = mining;
            this.context = context;
        }
    }

    private WeaponSystem setupWeapons() {
        WeaponSystem weaponSystem = new WeaponSystem();
        weaponSystem.register("steel_rapier", new WeaponSystem.WeaponSpec(
                MathUtils.degreesToRadians * 10f,
                3.4f,
                0.42f,
                0.22f,
                0.1f,
                12f
        ));
        return weaponSystem;
    }
}
