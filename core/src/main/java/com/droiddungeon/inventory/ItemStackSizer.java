package com.droiddungeon.inventory;

@FunctionalInterface
public interface ItemStackSizer {
    int maxStackSize(String itemId);
}
