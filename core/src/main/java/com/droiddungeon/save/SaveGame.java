package com.droiddungeon.save;

import com.droiddungeon.inventory.ItemStack;
import java.util.List;

/** Serializable snapshot of a single-player world. */
public final class SaveGame {
  public String name;
  public long seed;
  public long updatedAt;
  public int minX;
  public int maxX;
  public int minY;
  public int maxY;
  public PlayerState player;
  public CompanionState companion;
  public ItemStackState[] inventory;
  public List<BlockCellState> blocks;
  public List<GroundItemState> groundItems;
  public int nextEntityId;

  public SaveGame() {}

  public SaveGame(
      String name,
      long seed,
      long updatedAt,
      int minX,
      int maxX,
      int minY,
      int maxY,
      PlayerState player,
      CompanionState companion,
      ItemStackState[] inventory,
      List<BlockCellState> blocks,
      List<GroundItemState> groundItems,
      int nextEntityId) {
    this.name = name;
    this.seed = seed;
    this.updatedAt = updatedAt;
    this.minX = minX;
    this.maxX = maxX;
    this.minY = minY;
    this.maxY = maxY;
    this.player = player;
    this.companion = companion;
    this.inventory = inventory;
    this.blocks = blocks;
    this.groundItems = groundItems;
    this.nextEntityId = nextEntityId;
  }

  /** Player snapshot within the save. */
  public static final class PlayerState {
    public float renderX;
    public float renderY;
    public int gridX;
    public int gridY;
    public float health;

    public PlayerState() {}

    public PlayerState(float renderX, float renderY, int gridX, int gridY, float health) {
      this.renderX = renderX;
      this.renderY = renderY;
      this.gridX = gridX;
      this.gridY = gridY;
      this.health = health;
    }
  }

  /** Companion snapshot within the save. */
  public static final class CompanionState {
    public float renderX;
    public float renderY;
    public int gridX;
    public int gridY;

    public CompanionState() {}

    public CompanionState(float renderX, float renderY, int gridX, int gridY) {
      this.renderX = renderX;
      this.renderY = renderY;
      this.gridX = gridX;
      this.gridY = gridY;
    }
  }

  /** Simplified item stack for serialization. */
  public static final class ItemStackState {
    public String itemId;
    public int count;
    public int durability;

    public ItemStackState() {}

    public ItemStackState(String itemId, int count, int durability) {
      this.itemId = itemId;
      this.count = count;
      this.durability = durability;
    }

    public ItemStack toItemStack() {
      if (itemId == null || itemId.isBlank() || count <= 0) return null;
      return new ItemStack(itemId, count, durability);
    }

    public static ItemStackState from(ItemStack stack) {
      if (stack == null) return null;
      return new ItemStackState(stack.itemId(), stack.count(), stack.durability());
    }
  }

  /** Single tile state for block map. */
  public static final class BlockCellState {
    public int x;
    public int y;
    public String blockMaterial;
    public float blockHp;

    public BlockCellState() {}

    public BlockCellState(int x, int y, String blockMaterial, float blockHp) {
      this.x = x;
      this.y = y;
      this.blockMaterial = blockMaterial;
      this.blockHp = blockHp;
    }
  }

  /** Ground item snapshot. */
  public static final class GroundItemState {
    public int id;
    public int x;
    public int y;
    public ItemStackState stack;
    public List<ItemStackState> bundled;

    public GroundItemState() {}

    public GroundItemState(
        int id, int x, int y, ItemStackState stack, List<ItemStackState> bundled) {
      this.id = id;
      this.x = x;
      this.y = y;
      this.stack = stack;
      this.bundled = bundled;
    }
  }
}
