package com.droiddungeon.enemies;

/** Static definitions for hostile entities. */
public enum EnemyType {
  CATSTER(
      "catster",
      3.8f, // tiles per second
      9f, // vision range in tiles
      1.15f, // melee reach in tiles
      1.05f, // seconds between attacks
      12f, // damage per hit
      0.9f, // idle pause before another wander step
      5f, // maximum wander radius from spawn center
      38f // max health
      );

  private final String id;
  private final float speedTilesPerSecond;
  private final float visionRangeTiles;
  private final float attackRangeTiles;
  private final float attackCooldownSeconds;
  private final float damage;
  private final float wanderDelaySeconds;
  private final float wanderRadiusTiles;
  private final float maxHealth;

  EnemyType(
      String id,
      float speedTilesPerSecond,
      float visionRangeTiles,
      float attackRangeTiles,
      float attackCooldownSeconds,
      float damage,
      float wanderDelaySeconds,
      float wanderRadiusTiles,
      float maxHealth) {
    this.id = id;
    this.speedTilesPerSecond = speedTilesPerSecond;
    this.visionRangeTiles = visionRangeTiles;
    this.attackRangeTiles = attackRangeTiles;
    this.attackCooldownSeconds = attackCooldownSeconds;
    this.damage = damage;
    this.wanderDelaySeconds = wanderDelaySeconds;
    this.wanderRadiusTiles = wanderRadiusTiles;
    this.maxHealth = Math.max(1f, maxHealth);
  }

  public String id() {
    return id;
  }

  public float speedTilesPerSecond() {
    return speedTilesPerSecond;
  }

  public float visionRangeTiles() {
    return visionRangeTiles;
  }

  public float attackRangeTiles() {
    return attackRangeTiles;
  }

  public float attackCooldownSeconds() {
    return attackCooldownSeconds;
  }

  public float damage() {
    return damage;
  }

  public float wanderDelaySeconds() {
    return wanderDelaySeconds;
  }

  public float wanderRadiusTiles() {
    return wanderRadiusTiles;
  }

  public float maxHealth() {
    return maxHealth;
  }
}
