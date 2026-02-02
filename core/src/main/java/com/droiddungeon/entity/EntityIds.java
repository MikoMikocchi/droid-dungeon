package com.droiddungeon.entity;

import java.util.concurrent.atomic.AtomicInteger;

/** Simple monotonic id generator for entities. */
public final class EntityIds {
  private static final AtomicInteger COUNTER = new AtomicInteger(1);

  private EntityIds() {}

  public static int next() {
    return COUNTER.getAndIncrement();
  }

  /** Returns the next id that will be assigned (monotonic). */
  public static int peek() {
    return COUNTER.get();
  }

  /** Force the next id to be at least {@code next}. Useful when restoring saves. */
  public static void setNext(int next) {
    if (next <= 0) return;
    int current;
    do {
      current = COUNTER.get();
      if (next <= current) {
        return;
      }
    } while (!COUNTER.compareAndSet(current, next));
  }
}
