package com.droiddungeon.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lightweight registry for grid entities with tile occupancy tracking. */
public final class EntityWorld {
  private final Map<Integer, GridEntity> byId = new HashMap<>();
  private final Map<Long, List<Integer>> byTile = new HashMap<>();

  public void add(GridEntity entity) {
    if (entity == null) return;
    byId.put(entity.id(), entity);
    addToTile(entity, entity.gridX(), entity.gridY());
  }

  public void remove(GridEntity entity) {
    if (entity == null) return;
    byId.remove(entity.id());
    removeFromTile(entity, entity.gridX(), entity.gridY());
  }

  public void move(GridEntity entity, int fromX, int fromY, int toX, int toY) {
    if (entity == null) return;
    long fromKey = key(fromX, fromY);
    long toKey = key(toX, toY);
    if (fromKey == toKey) {
      return;
    }
    removeFromTile(entity, fromX, fromY);
    addToTile(entity, toX, toY);
  }

  public void clear() {
    byId.clear();
    byTile.clear();
  }

  public boolean isBlocked(int x, int y) {
    List<Integer> list = byTile.get(key(x, y));
    if (list == null) {
      return false;
    }
    for (Integer id : list) {
      GridEntity e = byId.get(id);
      if (e != null && e.blocking()) {
        return true;
      }
    }
    return false;
  }

  public List<GridEntity> at(int x, int y, EntityLayer... layers) {
    EnumSet<EntityLayer> filter =
        layers == null || layers.length == 0
            ? EnumSet.allOf(EntityLayer.class)
            : EnumSet.of(layers[0], layers);
    List<GridEntity> result = new ArrayList<>();
    List<Integer> list = byTile.get(key(x, y));
    if (list == null) {
      return result;
    }
    for (Integer id : list) {
      GridEntity e = byId.get(id);
      if (e != null && filter.contains(e.layer())) {
        result.add(e);
      }
    }
    return result;
  }

  public Iterable<GridEntity> all() {
    return byId.values();
  }

  private void addToTile(GridEntity entity, int x, int y) {
    long key = key(x, y);
    byTile.computeIfAbsent(key, k -> new ArrayList<>()).add(entity.id());
  }

  private void removeFromTile(GridEntity entity, int x, int y) {
    long key = key(x, y);
    List<Integer> list = byTile.get(key);
    if (list == null) {
      return;
    }
    list.remove((Integer) entity.id());
    if (list.isEmpty()) {
      byTile.remove(key);
    }
  }

  private long key(int x, int y) {
    return ((long) x << 32) ^ (y & 0xffffffffL);
  }
}
