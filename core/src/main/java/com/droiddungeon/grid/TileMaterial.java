package com.droiddungeon.grid;

import com.badlogic.gdx.graphics.Color;

public enum TileMaterial {
  VOID("Void", new Color(0f, 0f, 0f, 1f), new Color(0f, 0f, 0f, 1f), false),
  STONE("Stone", new Color(0.28f, 0.29f, 0.32f, 1f), new Color(0.22f, 0.23f, 0.25f, 1f), true),
  DIRT("Dirt", new Color(0.36f, 0.26f, 0.17f, 1f), new Color(0.30f, 0.21f, 0.14f, 1f), true),
  WOOD("Wood", new Color(0.45f, 0.33f, 0.20f, 1f), new Color(0.38f, 0.28f, 0.17f, 1f), true),
  GRAVEL("Gravel", new Color(0.56f, 0.56f, 0.58f, 1f), new Color(0.48f, 0.48f, 0.50f, 1f), true),
  PLANKS("Planks", new Color(0.62f, 0.45f, 0.28f, 1f), new Color(0.54f, 0.39f, 0.23f, 1f), true);

  private final String displayName;
  private final Color lightColor;
  private final Color darkColor;
  private final Color flatColor;
  private final boolean walkable;

  TileMaterial(String displayName, Color lightColor, Color darkColor, boolean walkable) {
    this.displayName = displayName;
    this.lightColor = lightColor;
    this.darkColor = darkColor;
    // Use a stable middle tone to avoid checkerboard parity artifacts in rendering.
    this.flatColor = new Color(lightColor).lerp(darkColor, 0.45f);
    this.walkable = walkable;
  }

  public String displayName() {
    return displayName;
  }

  public Color lightColor() {
    return lightColor;
  }

  public Color darkColor() {
    return darkColor;
  }

  public Color colorForParity(int parity) {
    return flatColor;
  }

  public boolean isWalkable() {
    return walkable;
  }
}
