package com.droiddungeon.entity;

/** Minimal contract for anything that occupies a grid cell. */
public interface GridEntity {
  int id();

  EntityLayer layer();

  int gridX();

  int gridY();

  boolean blocking();
}
