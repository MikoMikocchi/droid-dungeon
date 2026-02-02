package com.droiddungeon.items;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

public record ItemDefinition(
    String id,
    String displayName,
    int maxStackSize,
    TextureRegion icon,
    boolean equippable,
    int maxDurability,
    ToolType toolType) {
  public ItemDefinition {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("displayName must not be blank");
    }
    if (maxStackSize <= 0) {
      throw new IllegalArgumentException("maxStackSize must be positive");
    }
    if (icon == null) {
      throw new IllegalArgumentException("icon must not be null");
    }
    if (maxDurability < 0) {
      throw new IllegalArgumentException("maxDurability must be >= 0");
    }
    if (toolType == null) {
      toolType = ToolType.NONE;
    }
  }
}
