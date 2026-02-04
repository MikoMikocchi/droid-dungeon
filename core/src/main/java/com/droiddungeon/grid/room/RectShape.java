package com.droiddungeon.grid.room;

import java.util.List;

/** Simple axis-aligned rectangular room. */
public final class RectShape implements RoomShape {
  private final IntRect bounds;

  public RectShape(int x, int y, int width, int height) {
    this.bounds = new IntRect(x, y, width, height);
  }

  @Override
  public IntRect bounds() {
    return bounds;
  }

  @Override
  public boolean contains(int x, int y) {
    return x >= bounds.x()
        && x <= bounds.maxX()
        && y >= bounds.y()
        && y <= bounds.maxY();
  }

  @Override
  public List<IntPoint> doorAnchors() {
    int cx = bounds.centerX();
    int cy = bounds.centerY();
    return List.of(
        new IntPoint(cx, bounds.y()), // south edge
        new IntPoint(cx, bounds.maxY()), // north edge
        new IntPoint(bounds.x(), cy), // west
        new IntPoint(bounds.maxX(), cy)); // east
  }
}
