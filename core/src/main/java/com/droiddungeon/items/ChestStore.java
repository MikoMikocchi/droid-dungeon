package com.droiddungeon.items;

import com.droiddungeon.inventory.ItemStack;
import com.droiddungeon.save.SaveGame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** In-memory store of chests placed in the world. */
public final class ChestStore {
  private final Map<Long, Chest> chests = new HashMap<>();

  public void clear() {
    chests.clear();
  }

  public void upsert(int x, int y, List<ItemStack> contents) {
    Objects.requireNonNull(contents, "contents");
    chests.put(key(x, y), new Chest(x, y, new ArrayList<>(contents)));
  }

  public List<ItemStack> peek(int x, int y) {
    Chest chest = chests.get(key(x, y));
    if (chest == null) return List.of();
    return new ArrayList<>(chest.contents);
  }

  /** Remove chest and return its items (copy). */
  public List<ItemStack> drain(int x, int y) {
    Chest chest = chests.remove(key(x, y));
    if (chest == null) return List.of();
    return new ArrayList<>(chest.contents);
  }

  public List<SaveGame.ChestState> toSaveStates() {
    List<SaveGame.ChestState> states = new ArrayList<>();
    for (Chest chest : chests.values()) {
      List<SaveGame.ItemStackState> items = new ArrayList<>();
      for (ItemStack stack : chest.contents) {
        var state = SaveGame.ItemStackState.from(stack);
        if (state != null) {
          items.add(state);
        }
      }
      states.add(new SaveGame.ChestState(chest.x, chest.y, items));
    }
    return states;
  }

  public void loadFrom(List<SaveGame.ChestState> states) {
    chests.clear();
    if (states == null) {
      return;
    }
    for (SaveGame.ChestState state : states) {
      if (state == null) continue;
      List<ItemStack> contents = new ArrayList<>();
      if (state.items != null) {
        for (SaveGame.ItemStackState is : state.items) {
          ItemStack stack = is.toItemStack();
          if (stack != null) {
            contents.add(stack);
          }
        }
      }
      upsert(state.x, state.y, contents);
    }
  }

  private long key(int x, int y) {
    return ((long) x << 32) ^ (y & 0xffffffffL);
  }

  private record Chest(int x, int y, List<ItemStack> contents) {}
}
