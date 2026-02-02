package com.droiddungeon.crafting;

/** Simple pair of item id and amount needed for a recipe. */
public record CraftingIngredient(String itemId, int count) {
  public CraftingIngredient {
    if (itemId == null || itemId.isBlank()) {
      throw new IllegalArgumentException("itemId must not be blank");
    }
    if (count <= 0) {
      throw new IllegalArgumentException("count must be > 0");
    }
  }
}
