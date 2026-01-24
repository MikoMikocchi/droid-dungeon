package com.droiddungeon.inventory;

public final class Inventory {
    public static final int HOTBAR_SLOTS = 10;
    public static final int TOTAL_SLOTS = 40;

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

    private static void checkIndex(int index) {
        if (index < 0 || index >= TOTAL_SLOTS) {
            throw new IndexOutOfBoundsException("index=" + index);
        }
    }
}
