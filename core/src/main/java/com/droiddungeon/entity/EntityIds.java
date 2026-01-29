package com.droiddungeon.entity;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple monotonic id generator for entities.
 */
public final class EntityIds {
    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    private EntityIds() {}

    public static int next() {
        return COUNTER.getAndIncrement();
    }
}
