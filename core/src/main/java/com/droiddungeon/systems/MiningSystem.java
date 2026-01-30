package com.droiddungeon.systems;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.MathUtils;
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

    private final float baseDamagePerSecond = 12f; // tuned so stone breaks in ~1s with correct tool
    private final float reachTiles = 1.5f;

    private int targetX = Integer.MIN_VALUE;
    private int targetY = Integer.MIN_VALUE;
    private float accumulatedDamage = 0f;
    private float targetHealth = 0f;
    private BlockMaterial targetBlock = null;
    private boolean hasHighlight = false;
    private float progressRatio = 0f;

    public MiningSystem(Grid grid, InventorySystem inventorySystem, ItemRegistry itemRegistry) {
        this.grid = grid;
        this.inventorySystem = inventorySystem;
        this.itemRegistry = itemRegistry;
    }

    public void update(
            float deltaSeconds,
            Player player,
            ItemStack equippedItem,
            Vector2 mouseWorld,
            float tileSize,
            boolean interactionEnabled,
            boolean miningHeld
    ) {
        if (!interactionEnabled || player == null || mouseWorld == null) {
            resetProgress();
            return;
        }

        ToolType tool = toolTypeFor(equippedItem);
        // If a weapon (or any item without a mining tool type) is equipped, disable mining and highlighting.
        if (equippedItem != null && tool == ToolType.NONE) {
            resetProgress();
            return;
        }
        int targetX = (int) Math.floor(mouseWorld.x / tileSize);
        int targetY = (int) Math.floor(mouseWorld.y / tileSize);

        float dx = targetX + 0.5f - (player.getRenderX() + 0.5f);
        float dy = targetY + 0.5f - (player.getRenderY() + 0.5f);
        float dist2 = dx * dx + dy * dy;
        if (dist2 > reachTiles * reachTiles) {
            resetProgress();
            return; // out of range
        }

        BlockMaterial block = grid.getBlockMaterial(targetX, targetY);
        if (block == null) {
            resetProgress();
            return; // nothing to mine
        }

        boolean targetChanged = this.targetX != targetX || this.targetY != targetY || this.targetBlock != block;
        if (targetChanged) {
            accumulatedDamage = 0f;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetBlock = block;
            this.targetHealth = grid.getBlockHealth(targetX, targetY);
        }

        // Always update highlight while valid target exists.
        hasHighlight = true;
        progressRatio = 0f;

        if (!miningHeld) {
            accumulatedDamage = 0f;
            return; // highlight only; no progress when not holding
        }

        float efficiency = block.efficiencyFor(tool);
        float dps = baseDamagePerSecond * Math.max(0f, efficiency);
        float damage = dps * deltaSeconds;
        float remainingHealth = grid.getBlockHealth(targetX, targetY);
        targetHealth = remainingHealth;

        accumulatedDamage += damage;
        progressRatio = MathUtils.clamp(remainingHealth <= 0f ? 1f : accumulatedDamage / remainingHealth, 0f, 1f);

        if (accumulatedDamage >= remainingHealth && remainingHealth > 0f) {
            boolean destroyed = grid.damageBlock(targetX, targetY, remainingHealth);
            if (destroyed && block.dropItemId() != null && block.dropCount() > 0) {
                inventorySystem.addGroundStack(targetX, targetY,
                        new ItemStack(block.dropItemId(), block.dropCount()));
            }
            if (destroyed && equippedItem != null) {
                inventorySystem.damageEquippedItem(1);
            }
            resetProgress(); // completed; require re-aim to restart
        }
    }

    public MiningTarget getTarget() {
        if (!hasHighlight) {
            return null;
        }
        return new MiningTarget(targetX, targetY, progressRatio);
    }

    private void resetProgress() {
        accumulatedDamage = 0f;
        targetHealth = 0f;
        targetBlock = null;
        targetX = Integer.MIN_VALUE;
        targetY = Integer.MIN_VALUE;
        hasHighlight = false;
        progressRatio = 0f;
    }

    private ToolType toolTypeFor(ItemStack equipped) {
        if (equipped == null) {
            return ToolType.NONE;
        }
        return itemRegistry.getToolType(equipped.itemId());
    }

    public record MiningTarget(int x, int y, float progress) {}
}
