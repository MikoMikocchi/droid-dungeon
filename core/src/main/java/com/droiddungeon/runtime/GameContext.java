package com.droiddungeon.runtime;

import com.droiddungeon.entity.EntityWorld;
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
 * Shared runtime state passed to subsystems to reduce parameter noise.
 */
public record GameContext(
        Grid grid,
        EntityWorld entityWorld,
        Player player,
        PlayerStats playerStats,
        CompanionSystem companionSystem,
        EnemySystem enemySystem,
        Inventory inventory,
        InventorySystem inventorySystem,
        ItemRegistry itemRegistry,
        WeaponSystem weaponSystem
) {}
