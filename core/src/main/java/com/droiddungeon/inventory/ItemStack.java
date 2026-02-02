package com.droiddungeon.inventory;

public record ItemStack(String itemId, int count, int durability) {
  public ItemStack {
    if (itemId == null || itemId.isBlank()) {
      throw new IllegalArgumentException("itemId must not be blank");
    }
    if (count <= 0) {
      throw new IllegalArgumentException("count must be > 0");
    }
    if (durability < 0) {
      throw new IllegalArgumentException("durability must be >= 0");
    }
  }

  public ItemStack(String itemId, int count) {
    this(itemId, count, 0);
  }

  public ItemStack withCount(int newCount) {
    return new ItemStack(itemId, newCount, durability);
  }

  public ItemStack withDurability(int newDurability) {
    return new ItemStack(itemId, count, newDurability);
  }

  public boolean canStackWith(ItemStack other) {
    return other != null && itemId.equals(other.itemId) && durability == other.durability;
  }
}
