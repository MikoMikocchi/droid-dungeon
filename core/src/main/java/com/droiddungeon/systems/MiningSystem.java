package com.droiddungeon.systems;

import com.badlogic.gdx.math.Vector2;
import com.droiddungeon.grid.BlockMaterial;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.inventory.ItemStack;
import com.droiddungeon.items.ToolType;
import com.droiddungeon.items.ItemRegistry;

/**
 * Handles mining/digging/chopping separate from the melee weapon system.
 */
public final class MiningSystem {
    private final Grid grid;
    private final InventorySystem inventorySystem;
    private final ItemRegistry itemRegistry;

    private final float baseDamage = 4f;
    private final float reachTiles = 1.5f;

    public MiningSystem(Grid grid, InventorySystem inventorySystem, ItemRegistry itemRegistry) {
        this.grid = grid;
        this.inventorySystem = inventorySystem;
        this.itemRegistry = itemRegistry;
    }

    public void tryMine(Player player, ItemStack equippedItem, Vector2 mouseWorld, float tileSize) {
        if (player == null || mouseWorld == null) {
            return;
        }
        int targetX = (int) Math.floor(mouseWorld.x / tileSize);
        int targetY = (int) Math.floor(mouseWorld.y / tileSize);

        float dx = targetX + 0.5f - (player.getRenderX() + 0.5f);
        float dy = targetY + 0.5f - (player.getRenderY() + 0.5f);
        float dist2 = dx * dx + dy * dy;
        if (dist2 > reachTiles * reachTiles) {
            return; // out of range
        }

        BlockMaterial block = grid.getBlockMaterial(targetX, targetY);
        if (block == null) {
            return; // nothing to mine
        }

        ToolType tool = toolTypeFor(equippedItem);
        float efficiency = block.efficiencyFor(tool);
        float damage = baseDamage * efficiency;
        boolean destroyed = grid.damageBlock(targetX, targetY, damage);

        if (destroyed) {
            if (block.dropItemId() != null && block.dropCount() > 0) {
                inventorySystem.addGroundStack(targetX, targetY,
                        new ItemStack(block.dropItemId(), block.dropCount()));
            }
        }
    }

    private ToolType toolTypeFor(ItemStack equipped) {
        if (equipped == null) {
            return ToolType.NONE;
        }
        return itemRegistry.getToolType(equipped.itemId());
    }
}
