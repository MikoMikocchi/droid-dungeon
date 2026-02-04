package com.droiddungeon.grid.room;

import java.util.ArrayList;
import java.util.List;

/** Diamond (Manhattan) shape inside a bounding box. */
public final class DiamondShape implements RoomShape {
  private final IntRect bounds;
  private final double halfW;
  private final double halfH;
  private final int centerX;
  private final int centerY;

  public DiamondShape(int x, int y, int width, int height) {
    this.bounds = new IntRect(x, y, width, height);
    this.halfW = width / 2.0;
    this.halfH = height / 2.0;
    this.centerX = bounds.centerX();
    this.centerY = bounds.centerY();
  }

  @Override
  public IntRect bounds() {
    return bounds;
  }

  @Override
  public boolean contains(int x, int y) {
    double dx = Math.abs(x - centerX) / halfW;
    double dy = Math.abs(y - centerY) / halfH;
    return dx + dy <= 1.0;
  }

  @Override
  public List<IntPoint> doorAnchors() {
    List<IntPoint> anchors = new ArrayList<>(4);
    if (contains(bounds.x(), centerY)) anchors.add(new IntPoint(bounds.x(), centerY));
    if (contains(bounds.maxX(), centerY)) anchors.add(new IntPoint(bounds.maxX(), centerY));
    if (contains(centerX, bounds.y())) anchors.add(new IntPoint(centerX, bounds.y()));
    if (contains(centerX, bounds.maxY())) anchors.add(new IntPoint(centerX, bounds.maxY()));
    return anchors;
  }
}
