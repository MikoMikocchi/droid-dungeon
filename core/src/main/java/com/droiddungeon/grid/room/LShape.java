package com.droiddungeon.grid.room;

import java.util.ArrayList;
import java.util.List;

/**
 * L-shaped figure as a union of two rectangles. Arm thickness derives from width/height to keep the
 * signature compact without extra parameters.
 */
public final class LShape implements RoomShape {
  private final IntRect bounds;
  private final IntRect legA;
  private final IntRect legB;

  public LShape(int x, int y, int width, int height, int thickness) {
    if (thickness <= 0 || thickness >= Math.min(width, height)) {
      throw new IllegalArgumentException("invalid thickness for L-shape");
    }
    this.bounds = new IntRect(x, y, width, height);
    // Vertical leg on the left, horizontal leg on the bottom.
    this.legA = new IntRect(x, y, thickness, height);
    this.legB = new IntRect(x, y, width, thickness);
  }

  @Override
  public IntRect bounds() {
    return bounds;
  }

  @Override
  public boolean contains(int x, int y) {
    return containsRect(legA, x, y) || containsRect(legB, x, y);
  }

  private boolean containsRect(IntRect r, int x, int y) {
    return x >= r.x() && x <= r.maxX() && y >= r.y() && y <= r.maxY();
  }

  @Override
  public List<IntPoint> doorAnchors() {
    List<IntPoint> anchors = new ArrayList<>(4);
    int cx = bounds.centerX();
    int cy = bounds.centerY();
    // Only anchors that actually lie inside the shape.
    int[][] candidates =
        new int[][] {
          {bounds.x(), cy},
          {bounds.maxX(), cy},
          {cx, bounds.y()},
          {cx, bounds.maxY()}
        };
    for (int[] c : candidates) {
      if (contains(c[0], c[1])) {
        anchors.add(new IntPoint(c[0], c[1]));
      }
    }
    return anchors;
  }
}
