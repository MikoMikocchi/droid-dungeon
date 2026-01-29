package com.droiddungeon.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight registry for grid entities with tile occupancy tracking.
 */
public final class EntityWorld {
    private final Map<Integer, GridEntity> byId = new HashMap<>();

    public void add(GridEntity entity) {
        if (entity == null) return;
        byId.put(entity.id(), entity);
    }

    public void remove(GridEntity entity) {
        if (entity == null) return;
        byId.remove(entity.id());
    }

    public void clear() {
        byId.clear();
    }

    public boolean isBlocked(int x, int y) {
        for (GridEntity e : byId.values()) {
            if (!e.blocking()) {
                continue;
            }
            if (e.gridX() == x && e.gridY() == y) {
                return true;
            }
        }
        return false;
    }

    public List<GridEntity> at(int x, int y, EntityLayer... layers) {
        EnumSet<EntityLayer> filter = layers == null || layers.length == 0
                ? EnumSet.allOf(EntityLayer.class)
                : EnumSet.of(layers[0], layers);
        List<GridEntity> result = new ArrayList<>();
        for (GridEntity e : byId.values()) {
            if (!filter.contains(e.layer())) continue;
            if (e.gridX() == x && e.gridY() == y) {
                result.add(e);
            }
        }
        return result;
    }

    public Iterable<GridEntity> all() {
        return byId.values();
    }
}
