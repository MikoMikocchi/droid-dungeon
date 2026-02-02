package com.droiddungeon.items;

import com.droiddungeon.entity.EntityLayer;
import com.droiddungeon.entity.GridEntity;
import com.droiddungeon.inventory.ItemStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GroundItem implements GridEntity {
  private final int id;
  private final int gridX;
  private final int gridY;
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

  public GroundItem(int id, int gridX, int gridY, ItemStack stack) {
    this(id, gridX, gridY, stack, List.of());
  }

  public GroundItem(int id, int gridX, int gridY, ItemStack stack, List<ItemStack> bundled) {
    if (stack == null) {
      throw new IllegalArgumentException("stack must not be null");
    }
    this.id = id;
    this.gridX = gridX;
    this.gridY = gridY;
    this.stack = stack;
    this.bundled =
        bundled == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(bundled));
  }

  @Override
  public int id() {
    return id;
  }

  @Override
  public EntityLayer layer() {
    return EntityLayer.ITEM;
  }

  @Override
  public int gridX() {
    return gridX;
  }

  @Override
  public int gridY() {
    return gridY;
  }

  @Override
  public boolean blocking() {
    return false;
  }
}
