package com.droiddungeon.grid.room;

/** Axis-aligned integer rectangle, width/height are positive. */
public record IntRect(int x, int y, int width, int height) {
  public IntRect {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("width and height must be positive");
    }
  }

  public int maxX() {
    return x + width - 1;
  }

  public int maxY() {
    return y + height - 1;
  }

  public int centerX() {
    return x + width / 2;
  }

  public int centerY() {
    return y + height / 2;
  }

  public boolean overlaps(IntRect other, int padding) {
    int ax0 = x - padding;
    int ay0 = y - padding;
    int ax1 = maxX() + padding;
    int ay1 = maxY() + padding;

    int bx0 = other.x - padding;
    int by0 = other.y - padding;
    int bx1 = other.maxX() + padding;
    int by1 = other.maxY() + padding;

    return ax0 <= bx1 && ax1 >= bx0 && ay0 <= by1 && ay1 >= by0;
  }
}
