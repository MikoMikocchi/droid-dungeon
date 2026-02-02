package com.droiddungeon.entity;

/** Grid-bound entity that also exposes render-space coordinates. */
public interface RenderableEntity extends GridEntity {
  float renderX();

  float renderY();

  boolean isMoving();
}
