package com.droiddungeon.debug;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.grid.DungeonGenerator;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.items.GroundItem;
import com.droiddungeon.items.ItemDefinition;
import com.droiddungeon.items.ItemRegistry;
import com.droiddungeon.inventory.ItemStack;
import com.droiddungeon.player.PlayerStats;
import com.droiddungeon.systems.CompanionSystem;
import com.droiddungeon.systems.EnemySystem;
import com.droiddungeon.systems.InventorySystem;

/**
 * Builds debug overlay text; extracted to keep GameRuntime focused on orchestration.
 */
public final class DebugTextBuilder {

    public String build(
            Viewport worldViewport,
            Grid grid,
            Player player,
            CompanionSystem companionSystem,
            EnemySystem enemySystem,
            InventorySystem inventorySystem,
            ItemRegistry itemRegistry,
            PlayerStats playerStats,
            float gridOriginX,
            float gridOriginY
    ) {
        float tileSize = grid.getTileSize();
        Vector2 world = worldViewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
        float localX = world.x - gridOriginX;
        float localY = world.y - gridOriginY;

        int tileX = (int) Math.floor(localX / tileSize);
        int tileY = (int) Math.floor(localY / tileSize);

        StringBuilder text = new StringBuilder();
        if (grid.isInside(tileX, tileY)) {
            appendTileInfo(grid, player, companionSystem, enemySystem, inventorySystem, itemRegistry, tileX, tileY, text);
        } else {
            text.append("Cursor: out of bounds");
        }

        appendEquippedInfo(inventorySystem, itemRegistry, text);
        appendPlayerAndEnemyInfo(playerStats, enemySystem, text);
        return text.toString();
    }

    private void appendTileInfo(
            Grid grid,
            Player player,
            CompanionSystem companionSystem,
            EnemySystem enemySystem,
            InventorySystem inventorySystem,
            ItemRegistry itemRegistry,
            int tileX,
            int tileY,
            StringBuilder text
    ) {
        var material = grid.getTileMaterial(tileX, tileY);
        text.append("Tile ").append(tileX).append(", ").append(tileY)
                .append(" — ").append(material.displayName());

        DungeonGenerator.RoomType roomType = grid.getRoomType(tileX, tileY);
        text.append("\nRoom: ").append(roomType == null ? "Corridor" : roomType);

        boolean hasEntities = false;
        if (player.getGridX() == tileX && player.getGridY() == tileY) {
            text.append("\nEntity: Player");
            hasEntities = true;
        }

        int doroX = companionSystem.getGridX();
        int doroY = companionSystem.getGridY();
        if (doroX == tileX && doroY == tileY) {
            text.append(hasEntities ? ", " : "\nEntity: ").append("Doro");
            hasEntities = true;
        }

        if (enemySystem != null) {
            for (com.droiddungeon.enemies.Enemy enemy : enemySystem.getEnemies()) {
                if (enemy.getGridX() == tileX && enemy.getGridY() == tileY) {
                    text.append(hasEntities ? ", " : "\nEntity: ").append("Catster");
                    hasEntities = true;
                }
            }
        }

        for (GroundItem groundItem : inventorySystem.getGroundItems()) {
            if (groundItem.isAt(tileX, tileY)) {
                ItemDefinition def = itemRegistry.get(groundItem.getStack().itemId());
                String name = def != null ? def.displayName() : groundItem.getStack().itemId();
                int count = groundItem.getStack().count();
                text.append(hasEntities ? ", " : "\nEntity: ");
                text.append(name);
                if (count > 1) {
                    text.append(" x").append(count);
                }
                hasEntities = true;
            }
        }

        if (!hasEntities) {
            text.append("\nEntity: —");
        }
    }

    private void appendEquippedInfo(InventorySystem inventorySystem, ItemRegistry itemRegistry, StringBuilder text) {
        ItemStack equipped = inventorySystem.getEquippedStack();
        if (equipped != null) {
            ItemDefinition def = itemRegistry.get(equipped.itemId());
            String name = def != null ? def.displayName() : equipped.itemId();
            text.append("\nEquipped: ").append(name);
            if (def != null && def.maxDurability() > 0) {
                int current = Math.min(equipped.durability(), def.maxDurability());
                text.append(" (").append(current).append("/").append(def.maxDurability()).append(")");
            }
        } else {
            text.append("\nEquipped: —");
        }
    }

    private void appendPlayerAndEnemyInfo(PlayerStats playerStats, EnemySystem enemySystem, StringBuilder text) {
        text.append("\nHP: ").append(Math.round(playerStats.getHealth())).append("/").append((int) playerStats.getMaxHealth());
        if (enemySystem != null) {
            int total = enemySystem.getEnemies().size();
            int alert = 0;
            for (com.droiddungeon.enemies.Enemy enemy : enemySystem.getEnemies()) {
                if (enemy.hasLineOfSight()) {
                    alert++;
                }
            }
            text.append("\nEnemies: ").append(total);
            if (alert > 0) {
                text.append(" (alert: ").append(alert).append(")");
            }
        }
    }
}
