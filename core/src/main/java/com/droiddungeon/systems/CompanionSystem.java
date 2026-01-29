package com.droiddungeon.systems;

import java.util.ArrayDeque;
import java.util.Deque;

import com.droiddungeon.entity.EntityLayer;
import com.droiddungeon.entity.RenderableEntity;

/**
 * Handles follower movement logic: grid trail + smooth render interpolation.
 */
public final class CompanionSystem implements RenderableEntity {
    private final int id;
    private final int delayTiles;
    private final float speedTilesPerSecond;
    private final Deque<int[]> trail = new ArrayDeque<>();

    private int gridX;
    private int gridY;
    private float renderX;
    private float renderY;
    private int lastPlayerGridX;
    private int lastPlayerGridY;

    public CompanionSystem(int id, int startGridX, int startGridY, int delayTiles, float speedTilesPerSecond) {
        this.id = id;
        this.delayTiles = Math.max(0, delayTiles);
        this.speedTilesPerSecond = Math.max(0f, speedTilesPerSecond);
        this.gridX = startGridX;
        this.gridY = startGridY;
        this.renderX = startGridX;
        this.renderY = startGridY;
        this.lastPlayerGridX = startGridX;
        this.lastPlayerGridY = startGridY;

        trail.clear();
        for (int i = 0; i < this.delayTiles; i++) {
            trail.addLast(new int[]{startGridX, startGridY});
        }
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
        return false;
    }

    public void updateFollowerTrail(int playerGridX, int playerGridY) {
        if (playerGridX == lastPlayerGridX && playerGridY == lastPlayerGridY) {
            return;
        }
        trail.addLast(new int[]{playerGridX, playerGridY});
        while (trail.size() > delayTiles) {
            int[] next = trail.removeFirst();
            gridX = next[0];
            gridY = next[1];
        }
        lastPlayerGridX = playerGridX;
        lastPlayerGridY = playerGridY;
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
        float step = speedTilesPerSecond * deltaSeconds;
        if (step >= dist) {
            renderX = targetX;
            renderY = targetY;
            return;
        }
        renderX += (dx / dist) * step;
        renderY += (dy / dist) * step;
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

    @Override
    public boolean isMoving() {
        return Math.abs(renderX - gridX) > 0.001f || Math.abs(renderY - gridY) > 0.001f;
    }
}
