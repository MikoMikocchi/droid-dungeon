package com.droiddungeon.entity;

/** Something that can receive damage and report its health state. */
public interface DamageableEntity {
  /**
   * @return true if damage was applied.
   */
  boolean applyDamage(float amount);

  boolean isDead();

  float healthRatio();
}
