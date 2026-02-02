package com.droiddungeon.crafting;

import com.droiddungeon.inventory.ItemStack;
import com.droiddungeon.items.ItemDefinition;
import com.droiddungeon.items.ItemRegistry;
import java.util.List;
import java.util.Objects;

/** Immutable crafting recipe with a flat list of ingredients and a single result stack. */
public final class CraftingRecipe {
  private final String id;
  private final String displayName;
  private final String resultItemId;
  private final int resultCount;
  private final List<CraftingIngredient> ingredients;

  public CraftingRecipe(
      String id,
      String displayName,
      String resultItemId,
      int resultCount,
      List<CraftingIngredient> ingredients) {
    this.id = Objects.requireNonNull(id, "id");
    this.displayName = Objects.requireNonNull(displayName, "displayName");
    this.resultItemId = Objects.requireNonNull(resultItemId, "resultItemId");
    if (resultCount <= 0) {
      throw new IllegalArgumentException("resultCount must be > 0");
    }
    this.resultCount = resultCount;
    if (ingredients == null || ingredients.isEmpty()) {
      throw new IllegalArgumentException("ingredients must not be empty");
    }
    this.ingredients = List.copyOf(ingredients);
  }

  public String id() {
    return id;
  }

  public String displayName() {
    return displayName;
  }

  public String resultItemId() {
    return resultItemId;
  }

  public int resultCount() {
    return resultCount;
  }

  public List<CraftingIngredient> ingredients() {
    return ingredients;
  }

  /** Build the output stack for this recipe, using max durability for tools if defined. */
  public ItemStack createResultStack(ItemRegistry registry) {
    int durability = 0;
    if (registry != null) {
      ItemDefinition def = registry.get(resultItemId);
      if (def != null && def.maxDurability() > 0) {
        durability = def.maxDurability();
      }
    }
    return new ItemStack(resultItemId, resultCount, durability);
  }
}
