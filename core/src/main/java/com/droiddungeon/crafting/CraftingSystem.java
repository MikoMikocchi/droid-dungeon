package com.droiddungeon.crafting;

import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.inventory.ItemStack;
import com.droiddungeon.items.ItemRegistry;
import java.util.ArrayList;
import java.util.List;

/** Applies crafting recipes against the player's inventory. */
public final class CraftingSystem {
  public enum CraftResult {
    SUCCESS,
    INVALID_RECIPE,
    MISSING_INGREDIENTS,
    NO_SPACE
  }

  private final Inventory inventory;
  private final ItemRegistry itemRegistry;
  private final List<CraftingRecipe> recipes;

  public CraftingSystem(
      Inventory inventory, ItemRegistry itemRegistry, List<CraftingRecipe> recipes) {
    this.inventory = inventory;
    this.itemRegistry = itemRegistry;
    this.recipes = new ArrayList<>();
    if (recipes != null) {
      for (CraftingRecipe recipe : recipes) {
        if (recipe != null && isRecipeSupported(recipe)) {
          this.recipes.add(recipe);
        }
      }
    }
  }

  private boolean isRecipeSupported(CraftingRecipe recipe) {
    // Skip recipes that reference unknown items to avoid crashes when assets are missing.
    if (itemRegistry.get(recipe.resultItemId()) == null) {
      return false;
    }
    for (CraftingIngredient ingredient : recipe.ingredients()) {
      if (itemRegistry.get(ingredient.itemId()) == null) {
        return false;
      }
    }
    return true;
  }

  public List<CraftingRecipe> getRecipes() {
    return recipes;
  }

  public CraftResult craft(int recipeIndex) {
    if (recipeIndex < 0 || recipeIndex >= recipes.size()) {
      return CraftResult.INVALID_RECIPE;
    }
    CraftingRecipe recipe = recipes.get(recipeIndex);
    if (!hasAllIngredients(recipe)) {
      return CraftResult.MISSING_INGREDIENTS;
    }

    ItemStack resultStack = recipe.createResultStack(itemRegistry);
    if (!wouldFitAfterConsume(recipe, resultStack)) {
      return CraftResult.NO_SPACE;
    }

    consumeIngredients(recipe);
    ItemStack leftover = inventory.add(resultStack, itemRegistry);
    // Should not happen because we pre-check, but stay safe.
    if (leftover != null) {
      inventory.add(leftover, itemRegistry);
      return CraftResult.NO_SPACE;
    }
    return CraftResult.SUCCESS;
  }

  public boolean canCraft(int recipeIndex) {
    if (recipeIndex < 0 || recipeIndex >= recipes.size()) {
      return false;
    }
    CraftingRecipe recipe = recipes.get(recipeIndex);
    return hasAllIngredients(recipe)
        && wouldFitAfterConsume(recipe, recipe.createResultStack(itemRegistry));
  }

  public boolean hasAllIngredients(CraftingRecipe recipe) {
    for (CraftingIngredient ingredient : recipe.ingredients()) {
      int owned = countItem(ingredient.itemId());
      if (owned < ingredient.count()) {
        return false;
      }
    }
    return true;
  }

  private boolean wouldFitAfterConsume(CraftingRecipe recipe, ItemStack resultStack) {
    ItemStack[] snapshot = snapshotInventory();
    consumeIngredients(snapshot, recipe);
    return canFit(snapshot, resultStack);
  }

  private ItemStack[] snapshotInventory() {
    ItemStack[] copy = new ItemStack[Inventory.TOTAL_SLOTS];
    for (int i = 0; i < Inventory.TOTAL_SLOTS; i++) {
      copy[i] = inventory.get(i);
    }
    return copy;
  }

  private void consumeIngredients(CraftingRecipe recipe) {
    for (CraftingIngredient ingredient : recipe.ingredients()) {
      int remaining = ingredient.count();
      for (int i = 0; i < inventory.size() && remaining > 0; i++) {
        ItemStack slot = inventory.get(i);
        if (slot == null || !slot.itemId().equals(ingredient.itemId())) {
          continue;
        }
        if (slot.count() <= remaining) {
          remaining -= slot.count();
          inventory.set(i, null);
        } else {
          inventory.set(i, slot.withCount(slot.count() - remaining));
          remaining = 0;
        }
      }
    }
  }

  private void consumeIngredients(ItemStack[] snapshot, CraftingRecipe recipe) {
    for (CraftingIngredient ingredient : recipe.ingredients()) {
      int remaining = ingredient.count();
      for (int i = 0; i < snapshot.length && remaining > 0; i++) {
        ItemStack slot = snapshot[i];
        if (slot == null || !slot.itemId().equals(ingredient.itemId())) {
          continue;
        }
        if (slot.count() <= remaining) {
          remaining -= slot.count();
          snapshot[i] = null;
        } else {
          snapshot[i] = slot.withCount(slot.count() - remaining);
          remaining = 0;
        }
      }
    }
  }

  private boolean canFit(ItemStack[] snapshot, ItemStack stack) {
    if (stack == null) {
      return false;
    }
    int remaining = stack.count();
    int maxStack = itemRegistry.maxStackSize(stack.itemId());

    // First merge into existing stacks.
    for (int i = 0; i < snapshot.length && remaining > 0; i++) {
      ItemStack slot = snapshot[i];
      if (slot == null || !slot.canStackWith(stack)) {
        continue;
      }
      int space = maxStack - slot.count();
      if (space <= 0) {
        continue;
      }
      int moved = Math.min(space, remaining);
      remaining -= moved;
    }

    // Then use empty slots.
    for (int i = 0; i < snapshot.length && remaining > 0; i++) {
      ItemStack slot = snapshot[i];
      if (slot != null) {
        continue;
      }
      int placed = Math.min(maxStack, remaining);
      remaining -= placed;
    }
    return remaining <= 0;
  }

  private int countItem(String itemId) {
    int total = 0;
    for (int i = 0; i < inventory.size(); i++) {
      ItemStack slot = inventory.get(i);
      if (slot != null && slot.itemId().equals(itemId)) {
        total += slot.count();
      }
    }
    return total;
  }
}
