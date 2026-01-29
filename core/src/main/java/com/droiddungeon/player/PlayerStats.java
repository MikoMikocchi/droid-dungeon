package com.droiddungeon.player;

import com.droiddungeon.entity.DamageableEntity;

/**
 * Lightweight player combat state (health + brief invulnerability).
 */
public final class PlayerStats implements DamageableEntity {
    private final float maxHealth;
    private float health;
    private float hurtInvulnerability;

    public PlayerStats(float maxHealth) {
        this.maxHealth = Math.max(1f, maxHealth);
        this.health = this.maxHealth;
        this.hurtInvulnerability = 0f;
    }

    public void update(float deltaSeconds) {
        if (hurtInvulnerability > 0f) {
            hurtInvulnerability = Math.max(0f, hurtInvulnerability - deltaSeconds);
        }
    }

    @Override
    public boolean applyDamage(float amount) {
        if (amount <= 0f) {
            return false;
        }
        if (hurtInvulnerability > 0f) {
            return false;
        }
        health = Math.max(0f, health - amount);
        hurtInvulnerability = 0.6f;
        return true;
    }

    public float getHealth() {
        return health;
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public float getHealthRatio() {
        return health / maxHealth;
    }

    @Override
    public boolean isDead() {
        return health <= 0f;
    }

    @Override
    public float healthRatio() {
        return getHealthRatio();
    }

    public boolean isRecentlyHit() {
        return hurtInvulnerability > 0f;
    }
}
