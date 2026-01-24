package com.droiddungeon.inventory;

public record ItemStack(String itemId, int count) {
    public ItemStack {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0");
        }
    }

    public ItemStack withCount(int newCount) {
        return new ItemStack(itemId, newCount);
    }
}
