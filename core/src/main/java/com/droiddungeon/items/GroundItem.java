package com.droiddungeon.items;

import com.droiddungeon.inventory.ItemStack;

public final class GroundItem {
    private int gridX;
    private int gridY;
    private ItemStack stack;

    public GroundItem(int gridX, int gridY, ItemStack stack) {
        if (stack == null) {
            throw new IllegalArgumentException("stack must not be null");
        }
        this.gridX = gridX;
        this.gridY = gridY;
        this.stack = stack;
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridY() {
        return gridY;
    }

    public ItemStack getStack() {
        return stack;
    }

    public void setStack(ItemStack stack) {
        if (stack == null) {
            throw new IllegalArgumentException("stack must not be null");
        }
        this.stack = stack;
    }

    public boolean isAt(int x, int y) {
        return gridX == x && gridY == y;
    }
}
