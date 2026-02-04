package com.droiddungeon.grid.room;

import java.util.ArrayList;
import java.util.List;

/** Elliptical room bounded by an AABB. */
public final class EllipseShape implements RoomShape {
  private final IntRect bounds;
  private final double rx;
  private final double ry;
  private final int centerX;
  private final int centerY;

  public EllipseShape(int x, int y, int width, int height) {
    this.bounds = new IntRect(x, y, width, height);
    this.rx = width / 2.0;
    this.ry = height / 2.0;
    this.centerX = bounds.centerX();
    this.centerY = bounds.centerY();
  }

  @Override
  public IntRect bounds() {
    return bounds;
  }

  @Override
  public boolean contains(int x, int y) {
    double dx = x - centerX;
    double dy = y - centerY;
    double val = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry);
    return val <= 1.0;
  }

  @Override
  public List<IntPoint> doorAnchors() {
    List<IntPoint> anchors = new ArrayList<>(4);
    int cx = centerX;
    int cy = centerY;
    // Sample axis intercepts; guard with contains to avoid degenerate thin ellipses.
    if (contains(bounds.x(), cy)) anchors.add(new IntPoint(bounds.x(), cy));
    if (contains(bounds.maxX(), cy)) anchors.add(new IntPoint(bounds.maxX(), cy));
    if (contains(cx, bounds.y())) anchors.add(new IntPoint(cx, bounds.y()));
    if (contains(cx, bounds.maxY())) anchors.add(new IntPoint(cx, bounds.maxY()));
    return anchors;
  }
}
