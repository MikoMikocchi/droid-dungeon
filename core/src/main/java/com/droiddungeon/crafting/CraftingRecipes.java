package com.droiddungeon.crafting;

import java.util.List;

/** Static recipe catalog. Eventually this could load from data files. */
public final class CraftingRecipes {
  private CraftingRecipes() {}

  public static List<CraftingRecipe> basic() {
    return List.of(
        new CraftingRecipe(
            "chip_bundle",
            "Chip Bundle",
            "test_chip",
            4,
            List.of(new CraftingIngredient("stone", 6))),
        new CraftingRecipe(
            "stone_pouch", "Pouch", "pouch", 1, List.of(new CraftingIngredient("stone", 12))),
        new CraftingRecipe(
            "stone_pickaxe",
            "Steel Pickaxe",
            "steel_pickaxe",
            1,
            List.of(new CraftingIngredient("stone", 14))),
        new CraftingRecipe(
            "stone_shovel",
            "Steel Shovel",
            "steel_shovel",
            1,
            List.of(new CraftingIngredient("stone", 12))),
        new CraftingRecipe(
            "stone_axe", "Steel Axe", "steel_axe", 1, List.of(new CraftingIngredient("stone", 16))),
        new CraftingRecipe(
            "stone_rapier",
            "Steel Rapier",
            "steel_rapier",
            1,
            List.of(new CraftingIngredient("stone", 18))),
        new CraftingRecipe(
            "chest",
            "Chest",
            "chest",
            1,
            List.of(new CraftingIngredient("stone", 16))));
  }
}
