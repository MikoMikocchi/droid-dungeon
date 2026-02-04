package com.droiddungeon.grid.room;

import java.util.Random;

/** Inclusive integer range helper used for procedural generation. */
public record IntRange(int min, int max) {
  public IntRange {
    if (min > max) {
      throw new IllegalArgumentException("min must be <= max");
    }
  }

  public int random(Random rng) {
    if (min == max) return min;
    return rng.nextInt(max - min + 1) + min;
  }
}
