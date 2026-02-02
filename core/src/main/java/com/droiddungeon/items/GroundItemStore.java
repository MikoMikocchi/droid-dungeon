package com.droiddungeon.items;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.droiddungeon.entity.EntityIds;
import com.droiddungeon.entity.EntityWorld;
import com.droiddungeon.inventory.ItemStack;
import com.droiddungeon.inventory.Inventory;

/**
 * Shared container for ground items in the world used by server and client.
 */
public final class GroundItemStore {
    private final List<GroundItem> groundItems = new ArrayList<>();
    private final EntityWorld entityWorld;
    private final ItemRegistry itemRegistry;

    public GroundItemStore(EntityWorld entityWorld, ItemRegistry itemRegistry) {
        this.entityWorld = entityWorld;
        this.itemRegistry = itemRegistry;
    }

    public synchronized void addGroundStack(int gridX, int gridY, ItemStack stack) {
        if (stack == null) return;
        int maxStack = itemRegistry != null ? itemRegistry.maxStackSize(stack.itemId()) : Inventory.DEFAULT_MAX_STACK;
        ItemStack remaining = stack;

        for (GroundItem groundItem : groundItems) {
            if (!groundItem.isAt(gridX, gridY)) continue;
            if (!groundItem.getStack().canStackWith(remaining)) continue;
            int space = maxStack - groundItem.getStack().count();
            if (space <= 0) continue;
            int toMove = Math.min(space, remaining.count());
            groundItem.setStack(groundItem.getStack().withCount(groundItem.getStack().count() + toMove));
            if (toMove == remaining.count()) {
                remaining = null;
                break;
            }
            remaining = remaining.withCount(remaining.count() - toMove);
        }

        while (remaining != null) {
            int chunk = Math.min(remaining.count(), maxStack);
            GroundItem newItem = new GroundItem(EntityIds.next(), gridX, gridY, new ItemStack(remaining.itemId(), chunk, remaining.durability()));
            groundItems.add(newItem);
            if (entityWorld != null) entityWorld.add(newItem);
            if (remaining.count() <= maxStack) {
                remaining = null;
            } else {
                remaining = remaining.withCount(remaining.count() - chunk);
            }
        }
    }

    public synchronized void addGroundBundle(int gridX, int gridY, ItemStack pouchStack, List<ItemStack> bundled) {
        if (pouchStack == null || bundled == null || bundled.isEmpty()) return;
        GroundItem item = new GroundItem(EntityIds.next(), gridX, gridY, pouchStack, bundled);
        groundItems.add(item);
        if (entityWorld != null) entityWorld.add(item);
    }

    public synchronized List<GroundItem> getGroundItems() {
        return new ArrayList<>(groundItems);
    }

    public synchronized void removeGroundItem(int id) {
        Iterator<GroundItem> it = groundItems.iterator();
        while (it.hasNext()) {
            GroundItem gi = it.next();
            if (gi.id() == id) {
                if (entityWorld != null) entityWorld.remove(gi);
                it.remove();
                return;
            }
        }
    }

    public synchronized void upsertGroundItem(int id, int x, int y, ItemStack stack) {
        if (stack == null) return;
        removeGroundItem(id);
        GroundItem newItem = new GroundItem(id, x, y, stack);
        groundItems.add(newItem);
        if (entityWorld != null) entityWorld.add(newItem);
    }

    public synchronized void clear() {
        if (entityWorld != null) {
            for (GroundItem groundItem : groundItems) {
                entityWorld.remove(groundItem);
            }
        }
        groundItems.clear();
    }
}
