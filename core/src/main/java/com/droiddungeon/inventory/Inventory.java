package com.droiddungeon.inventory;

public final class Inventory {
    public static final int HOTBAR_SLOTS = 10;
    public static final int TOTAL_SLOTS = 40;
    public static final int DEFAULT_MAX_STACK = 99;

    private final ItemStack[] slots = new ItemStack[TOTAL_SLOTS];

    public ItemStack get(int index) {
        checkIndex(index);
        return slots[index];
    }

    public void set(int index, ItemStack stack) {
        checkIndex(index);
        slots[index] = stack;
    }

    public int size() {
        return TOTAL_SLOTS;
    }

    public ItemStack remove(int index) {
        checkIndex(index);
        ItemStack existing = slots[index];
        slots[index] = null;
        return existing;
    }

    public void swap(int a, int b) {
        checkIndex(a);
        checkIndex(b);
        if (a == b) {
            return;
        }
        ItemStack tmp = slots[a];
        slots[a] = slots[b];
        slots[b] = tmp;
    }

    public ItemStack add(ItemStack stack, ItemStackSizer sizer) {
        if (stack == null) {
            return null;
        }
        ItemStackSizer resolvedSizer = sizer != null ? sizer : itemId -> DEFAULT_MAX_STACK;
        ItemStack remaining = stack;

        // First, try to merge into existing stacks of the same item.
        for (int i = 0; i < TOTAL_SLOTS && remaining != null; i++) {
            ItemStack slot = slots[i];
            if (slot == null || !slot.canStackWith(remaining)) {
                continue;
            }
            int max = resolvedSizer.maxStackSize(remaining.itemId());
            if (slot.count() >= max) {
                continue;
            }
            int transferable = Math.min(max - slot.count(), remaining.count());
            slots[i] = slot.withCount(slot.count() + transferable);
            if (transferable == remaining.count()) {
                remaining = null;
            } else {
                remaining = remaining.withCount(remaining.count() - transferable);
            }
        }

        // Then, fill empty slots.
        for (int i = 0; i < TOTAL_SLOTS && remaining != null; i++) {
            if (slots[i] != null) {
                continue;
            }
            int max = resolvedSizer.maxStackSize(remaining.itemId());
            int placed = Math.min(max, remaining.count());
            slots[i] = new ItemStack(remaining.itemId(), placed, remaining.durability());
            if (placed == remaining.count()) {
                remaining = null;
            } else {
                remaining = remaining.withCount(remaining.count() - placed);
            }
        }

        return remaining;
    }

    private static void checkIndex(int index) {
        if (index < 0 || index >= TOTAL_SLOTS) {
            throw new IndexOutOfBoundsException("index=" + index);
        }
    }
}
