package com.droiddungeon.enemies;

import com.droiddungeon.entity.DamageableEntity;
import com.droiddungeon.entity.EntityLayer;
import com.droiddungeon.entity.RenderableEntity;
import com.droiddungeon.grid.Grid;

/**
 * Runtime enemy instance (position, timers, room bounds).
 */
public final class Enemy implements RenderableEntity, DamageableEntity {
    private final int id;
    private final EnemyType type;
    private final int roomMinX;
    private final int roomMinY;
    private final int roomMaxX;
    private final int roomMaxY;
    private final int homeX;
    private final int homeY;

    private int gridX;
    private int gridY;
    private float renderX;
    private float renderY;
    private float attackCooldown;
    private float wanderCooldown;
    private boolean hasLineOfSight;
    private float health;
    private int lastHitSwing = -1;
    private float hitFlashTimer;

    public Enemy(int id, EnemyType type, int spawnX, int spawnY, int roomMinX, int roomMinY, int roomMaxX, int roomMaxY) {
        this.id = id;
        this.type = type;
        this.gridX = spawnX;
        this.gridY = spawnY;
        this.renderX = spawnX;
        this.renderY = spawnY;
        this.roomMinX = roomMinX;
        this.roomMinY = roomMinY;
        this.roomMaxX = roomMaxX;
        this.roomMaxY = roomMaxY;
        this.homeX = spawnX;
        this.homeY = spawnY;
        this.attackCooldown = 0f;
        this.wanderCooldown = 0f;
        this.hasLineOfSight = false;
        this.health = type.maxHealth();
        this.hitFlashTimer = 0f;
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public EntityLayer layer() {
        return EntityLayer.ACTOR;
    }

    @Override
    public boolean blocking() {
        return true;
    }

    public EnemyType getType() {
        return type;
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridY() {
        return gridY;
    }

    @Override
    public int gridX() {
        return gridX;
    }

    @Override
    public int gridY() {
        return gridY;
    }

    @Override
    public float renderX() {
        return renderX;
    }

    @Override
    public float renderY() {
        return renderY;
    }

    public float getRenderX() {
        return renderX;
    }

    public float getRenderY() {
        return renderY;
    }

    public boolean hasLineOfSight() {
        return hasLineOfSight;
    }

    public void setHasLineOfSight(boolean value) {
        hasLineOfSight = value;
    }

    public int getHomeX() {
        return homeX;
    }

    public int getHomeY() {
        return homeY;
    }

    @Override
    public boolean isMoving() {
        return Math.abs(renderX - gridX) > 0.001f || Math.abs(renderY - gridY) > 0.001f;
    }

    @Override
    public boolean isDead() {
        return health <= 0f;
    }

    public void updateRender(float deltaSeconds) {
        float targetX = gridX;
        float targetY = gridY;
        float dx = targetX - renderX;
        float dy = targetY - renderY;
        float dist2 = dx * dx + dy * dy;
        if (dist2 < 0.000001f || deltaSeconds <= 0f) {
            renderX = targetX;
            renderY = targetY;
            return;
        }
        float dist = (float) Math.sqrt(dist2);
        float step = type.speedTilesPerSecond() * deltaSeconds;
        if (step >= dist) {
            renderX = targetX;
            renderY = targetY;
            return;
        }
        renderX += (dx / dist) * step;
        renderY += (dy / dist) * step;
    }

    public void tickCooldowns(float deltaSeconds) {
        if (attackCooldown > 0f) {
            attackCooldown = Math.max(0f, attackCooldown - deltaSeconds);
        }
        if (wanderCooldown > 0f) {
            wanderCooldown = Math.max(0f, wanderCooldown - deltaSeconds);
        }
        if (hitFlashTimer > 0f) {
            hitFlashTimer = Math.max(0f, hitFlashTimer - deltaSeconds);
        }
    }

    public boolean readyToAttack() {
        return attackCooldown <= 0f;
    }

    public void triggerAttackCooldown() {
        attackCooldown = type.attackCooldownSeconds();
    }

    public void resetWanderCooldown() {
        wanderCooldown = type.wanderDelaySeconds();
    }

    public boolean readyToWander() {
        return wanderCooldown <= 0f && !isMoving();
    }

    public boolean moveTo(int x, int y, Grid grid) {
        if (!grid.isWalkable(x, y)) {
            return false;
        }
        gridX = x;
        gridY = y;
        return true;
    }

    public boolean isInsideRoomBounds(int x, int y) {
        return x >= roomMinX && x <= roomMaxX && y >= roomMinY && y <= roomMaxY;
    }

    public void snapToGrid() {
        renderX = gridX;
        renderY = gridY;
    }

    public boolean applyDamage(float amount, int swingIndex) {
        if (amount <= 0f) {
            return false;
        }
        if (lastHitSwing == swingIndex) {
            return false;
        }
        lastHitSwing = swingIndex;
        health = Math.max(0f, health - amount);
        hitFlashTimer = 0.18f;
        return true;
    }

    @Override
    public boolean applyDamage(float amount) {
        return applyDamage(amount, -1);
    }

    public int getLastHitSwing() {
        return lastHitSwing;
    }

    public float getHealth() {
        return health;
    }

    @Override
    public float healthRatio() {
        return type.maxHealth() > 0f ? health / type.maxHealth() : 0f;
    }

    public float getHitFlashTimer() {
        return hitFlashTimer;
    }
}
