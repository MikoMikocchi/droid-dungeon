package com.droiddungeon.render.lighting;

import com.badlogic.gdx.graphics.Color;

/** Predefined light types with appropriate warm colors and behaviors. */
public enum LightType {
  /** Personal light around player - large and bright */
  PLAYER_AURA(
      new Color(1.0f, 0.90f, 0.70f, 1f), // Warm yellow-white
      1.1f, // intensity - brighter
      8.5f, // radius tiles - larger for visibility
      true, // flicker
      0.05f, // flicker intensity - subtle
      0.03f, // flicker radius
      1.5f // flicker speed - slow, gentle
      ),

  /** Wall-mounted torch */
  TORCH(
      new Color(1.0f, 0.75f, 0.40f, 1f), // Orange-yellow
      1.0f,
      6.2f,
      true,
      0.12f,
      0.06f,
      3.5f),

  /** Campfire or larger fire source */
  CAMPFIRE(
      new Color(1.0f, 0.70f, 0.35f, 1f), // Deep orange
      1.1f,
      9.0f,
      true,
      0.15f,
      0.08f,
      3.0f),

  /** Small candle */
  CANDLE(
      new Color(1.0f, 0.85f, 0.55f, 1f), // Pale orange
      0.8f,
      3.0f,
      true,
      0.20f,
      0.12f,
      5.0f),

  /** Lantern (more stable than torch) */
  LANTERN(
      new Color(1.0f, 0.88f, 0.62f, 1f), // Warm yellow
      1.0f,
      6.5f,
      true,
      0.06f,
      0.03f,
      2.5f),

  /** Magical/crystal light (cooler but still warm) */
  CRYSTAL(
      new Color(0.95f, 0.90f, 0.80f, 1f), // Warm white
      0.9f,
      5.0f,
      true,
      0.04f,
      0.02f,
      1.5f),

  /** Ambient room light (very subtle) */
  AMBIENT(new Color(0.95f, 0.88f, 0.75f, 1f), 0.45f, 14.0f, false, 0f, 0f, 0f);

  public final Color color;
  public final float intensity;
  public final float radiusTiles;
  public final boolean flicker;
  public final float flickerIntensity;
  public final float flickerRadius;
  public final float flickerSpeed;

  LightType(
      Color color,
      float intensity,
      float radiusTiles,
      boolean flicker,
      float flickerIntensity,
      float flickerRadius,
      float flickerSpeed) {
    this.color = color;
    this.intensity = intensity;
    this.radiusTiles = radiusTiles;
    this.flicker = flicker;
    this.flickerIntensity = flickerIntensity;
    this.flickerRadius = flickerRadius;
    this.flickerSpeed = flickerSpeed;
  }

  /** Create a Light instance configured with this type's defaults. */
  public Light createLight(float x, float y, float tileSize) {
    Light light = new Light(x, y, radiusTiles * tileSize, color);
    light.setIntensity(intensity);
    light.setFlickerEnabled(flicker);
    light.setFlickerIntensityRange(flickerIntensity);
    light.setFlickerRadiusRange(flickerRadius);
    light.setFlickerSpeed(flickerSpeed);
    return light;
  }
}
