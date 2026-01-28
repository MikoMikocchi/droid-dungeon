package com.droiddungeon.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.droiddungeon.inventory.ItemStack;

public final class GroundItem {
    private int gridX;
    private int gridY;
    private ItemStack stack;
    private final List<ItemStack> bundled;

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

    public boolean isBundle() {
        return !bundled.isEmpty();
    }

    public List<ItemStack> getBundledItems() {
        return bundled;
    }

    public GroundItem(int gridX, int gridY, ItemStack stack) {
        this(gridX, gridY, stack, List.of());
    }

    public GroundItem(int gridX, int gridY, ItemStack stack, List<ItemStack> bundled) {
        if (stack == null) {
            throw new IllegalArgumentException("stack must not be null");
        }
        this.gridX = gridX;
        this.gridY = gridY;
        this.stack = stack;
        this.bundled = bundled == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(bundled));
    }
}
