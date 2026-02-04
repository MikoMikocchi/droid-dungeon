package com.droiddungeon.grid.room;

import com.droiddungeon.grid.DungeonGenerator;
import java.util.Objects;
import java.util.Random;

/** Data-driven room: type, size ranges, and geometry factory. Can live in code or be data-driven. */
public record RoomTemplate(
    String id,
    DungeonGenerator.RoomType type,
    IntRange widthRange,
    IntRange heightRange,
    ShapeFactory shapeFactory,
    float weight) {
  public RoomTemplate {
    Objects.requireNonNull(id);
    Objects.requireNonNull(type);
    Objects.requireNonNull(widthRange);
    Objects.requireNonNull(heightRange);
    Objects.requireNonNull(shapeFactory);
    if (weight <= 0f) throw new IllegalArgumentException("weight must be > 0");
  }

  public RoomShape create(int x, int y, Random rng) {
    int w = widthRange.random(rng);
    int h = heightRange.random(rng);
    return shapeFactory.create(x, y, w, h, rng);
  }

  @FunctionalInterface
  public interface ShapeFactory {
    RoomShape create(int x, int y, int width, int height, Random rng);
  }
}
