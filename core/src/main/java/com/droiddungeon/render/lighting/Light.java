package com.droiddungeon.render.lighting;

import com.badlogic.gdx.graphics.Color;

/**
 * Base class for all light sources in the game. Supports warm torch-like colors, smooth falloff,
 * and flickering effects.
 */
public class Light {
  /** World position X */
  protected float x;

  /** World position Y */
  protected float y;

  /** Base radius in world units (before flicker) */
  protected float radius;

  /** Light color (warm orange/yellow for torches) */
  protected final Color color = new Color(1f, 0.85f, 0.6f, 1f);

  /** Base intensity 0-1 (before flicker) */
  protected float intensity = 0.9f;

  /** Inner radius ratio where light is at full intensity (0-1) */
  protected float innerRadiusRatio = 0.15f;

  /** Flicker enabled */
  protected boolean flickerEnabled = true;

  /** Current flicker phase (internal timer) */
  protected float flickerPhase = 0f;

  /** Flicker speed multiplier */
  protected float flickerSpeed = 3.5f;

  /** Flicker intensity variation (0-1) */
  protected float flickerIntensityRange = 0.12f;

  /** Flicker radius variation (0-1 ratio of base radius) */
  protected float flickerRadiusRange = 0.06f;

  /** Is this light active */
  protected boolean active = true;

  /** Soft edge exponent for falloff curve (higher = sharper edge, lower = softer) */
  protected float falloffExponent = 2.2f;

  public Light(float x, float y, float radius) {
    this.x = x;
    this.y = y;
    this.radius = radius;
  }

  public Light(float x, float y, float radius, Color color) {
    this(x, y, radius);
    this.color.set(color);
  }

  /**
   * Update flicker animation.
   *
   * @param delta time since last frame
   */
  public void update(float delta) {
    if (flickerEnabled && active) {
      flickerPhase += delta * flickerSpeed;
      // Keep phase bounded to avoid float precision issues over time
      if (flickerPhase > 1000f) {
        flickerPhase -= 1000f;
      }
    }
  }

  /** Get current intensity including flicker effect. */
  public float getCurrentIntensity() {
    if (!flickerEnabled || !active) {
      return intensity;
    }
    // Multi-frequency noise for organic flicker
    float noise1 = (float) Math.sin(flickerPhase * 2.3f) * 0.5f;
    float noise2 = (float) Math.sin(flickerPhase * 5.7f + 1.3f) * 0.3f;
    float noise3 = (float) Math.sin(flickerPhase * 11.1f + 2.7f) * 0.2f;
    float flicker = (noise1 + noise2 + noise3) * flickerIntensityRange;
    return Math.max(0.1f, Math.min(1f, intensity + flicker));
  }

  /** Get current radius including flicker effect. */
  public float getCurrentRadius() {
    if (!flickerEnabled || !active) {
      return radius;
    }
    // Slightly different frequency for radius variation
    float noise = (float) Math.sin(flickerPhase * 3.1f + 0.5f);
    float variation = noise * radius * flickerRadiusRange;
    return Math.max(radius * 0.5f, radius + variation);
  }

  public float getX() {
    return x;
  }

  public void setX(float x) {
    this.x = x;
  }

  public float getY() {
    return y;
  }

  public void setY(float y) {
    this.y = y;
  }

  public void setPosition(float x, float y) {
    this.x = x;
    this.y = y;
  }

  public float getRadius() {
    return radius;
  }

  public void setRadius(float radius) {
    this.radius = Math.max(1f, radius);
  }

  public Color getColor() {
    return color;
  }

  public void setColor(Color color) {
    this.color.set(color);
  }

  public void setColor(float r, float g, float b) {
    this.color.set(r, g, b, 1f);
  }

  public float getIntensity() {
    return intensity;
  }

  public void setIntensity(float intensity) {
    this.intensity = Math.max(0f, Math.min(1f, intensity));
  }

  public float getInnerRadiusRatio() {
    return innerRadiusRatio;
  }

  public void setInnerRadiusRatio(float ratio) {
    this.innerRadiusRatio = Math.max(0f, Math.min(0.5f, ratio));
  }

  public boolean isFlickerEnabled() {
    return flickerEnabled;
  }

  public void setFlickerEnabled(boolean enabled) {
    this.flickerEnabled = enabled;
  }

  public float getFlickerSpeed() {
    return flickerSpeed;
  }

  public void setFlickerSpeed(float speed) {
    this.flickerSpeed = speed;
  }

  public float getFlickerIntensityRange() {
    return flickerIntensityRange;
  }

  public void setFlickerIntensityRange(float range) {
    this.flickerIntensityRange = range;
  }

  public float getFlickerRadiusRange() {
    return flickerRadiusRange;
  }

  public void setFlickerRadiusRange(float range) {
    this.flickerRadiusRange = range;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public float getFalloffExponent() {
    return falloffExponent;
  }

  public void setFalloffExponent(float exponent) {
    this.falloffExponent = Math.max(0.5f, exponent);
  }

  /**
   * Calculate light contribution at a given distance from center.
   *
   * @param distance distance from light center
   * @return intensity multiplier 0-1
   */
  public float calculateFalloff(float distance) {
    float currentRadius = getCurrentRadius();
    if (distance >= currentRadius) {
      return 0f;
    }
    float innerRadius = currentRadius * innerRadiusRatio;
    if (distance <= innerRadius) {
      return getCurrentIntensity();
    }
    // Smooth falloff using power curve
    float t = (distance - innerRadius) / (currentRadius - innerRadius);
    float falloff = 1f - (float) Math.pow(t, falloffExponent);
    return falloff * getCurrentIntensity();
  }

  /** Check if a point is within this light's maximum radius. */
  public boolean affects(float px, float py) {
    if (!active) return false;
    float dx = px - x;
    float dy = py - y;
    float distSq = dx * dx + dy * dy;
    float r = getCurrentRadius();
    return distSq < r * r;
  }
}
