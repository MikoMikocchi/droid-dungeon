package com.droiddungeon.grid.room;

import java.util.List;

/** Geometric shape of a room. */
public interface RoomShape {
  IntRect bounds();

  /** World-space integer tile inclusion check. */
  boolean contains(int x, int y);

  /**
   * Preferred doorway anchor points in world coords. Implementations can return empty; the caller
   * may fall back to center points.
   */
  default List<IntPoint> doorAnchors() {
    return List.of();
  }
}
