package com.droiddungeon.runtime;

import com.badlogic.gdx.math.MathUtils;
import com.droiddungeon.config.GameConfig;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.items.ItemRegistry;
import com.droiddungeon.player.PlayerStats;
import com.droiddungeon.systems.CompanionSystem;
import com.droiddungeon.systems.EnemySystem;
import com.droiddungeon.systems.InventorySystem;
import com.droiddungeon.systems.WeaponSystem;

/**
 * Builds GameContext instances for initial run and restarts.
 */
public final class GameContextFactory {
    private final GameConfig config;
    private final Grid grid;
    private final int spawnX;
    private final int spawnY;
    private final long worldSeed;
    private final Inventory inventory;
    private final InventorySystem inventorySystem;
    private final ItemRegistry itemRegistry;
    private final EnemySystem enemySystem;

    public GameContextFactory(
            GameConfig config,
            Grid grid,
            int spawnX,
            int spawnY,
            long worldSeed,
            Inventory inventory,
            InventorySystem inventorySystem,
            ItemRegistry itemRegistry,
            EnemySystem enemySystem
    ) {
        this.config = config;
        this.grid = grid;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.worldSeed = worldSeed;
        this.inventory = inventory;
        this.inventorySystem = inventorySystem;
        this.itemRegistry = itemRegistry;
        this.enemySystem = enemySystem;
    }

    public GameContext createContext() {
        Player player = new Player(spawnX, spawnY);
        PlayerStats playerStats = new PlayerStats(100f);
        CompanionSystem companionSystem = new CompanionSystem(player.getGridX(), player.getGridY(), config.companionDelayTiles(), config.companionSpeedTilesPerSecond());
        WeaponSystem weaponSystem = setupWeapons();

        return new GameContext(
                grid,
                player,
                playerStats,
                companionSystem,
                enemySystem,
                inventory,
                inventorySystem,
                itemRegistry,
                weaponSystem
        );
    }

    private WeaponSystem setupWeapons() {
        WeaponSystem weaponSystem = new WeaponSystem();
        weaponSystem.register("steel_rapier", new WeaponSystem.WeaponSpec(
                MathUtils.degreesToRadians * 20f,
                3.4f,
                0.42f,
                0.22f,
                0.55f,
                12f
        ));
        return weaponSystem;
    }
}
