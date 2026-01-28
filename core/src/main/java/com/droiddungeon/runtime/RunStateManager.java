package com.droiddungeon.runtime;

import com.droiddungeon.inventory.ItemStack;
import com.droiddungeon.ui.MapOverlay;

import java.util.List;

/**
 * Handles death lifecycle and run restarts.
 */
public final class RunStateManager {
    private boolean dead;
    private int deathGridX;
    private int deathGridY;

    public boolean isDead() {
        return dead;
    }

    public void handlePlayerDeath(GameContext ctx, MapOverlay mapOverlay) {
        dead = true;
        deathGridX = ctx.player().getGridX();
        deathGridY = ctx.player().getGridY();
        ctx.inventorySystem().forceCloseInventory();
        mapOverlay.close();

        List<ItemStack> loot = ctx.inventorySystem().drainAllItems();
        if (!loot.isEmpty()) {
            ctx.inventorySystem().addGroundBundle(deathGridX, deathGridY, new ItemStack("pouch", 1), loot);
        }
        mapOverlay.addDeathMarker(deathGridX, deathGridY);
    }

    public void reset(MapOverlay mapOverlay) {
        dead = false;
        mapOverlay.close();
    }
}
