package com.droiddungeon.grid;

import com.droiddungeon.entity.EntityLayer;
import com.droiddungeon.entity.RenderableEntity;

public final class Player implements RenderableEntity {
    private final int id;
    private int gridX;
    private int gridY;

    private float renderX;
    private float renderY;

    public Player(int id, int gridX, int gridY) {
        this.id = id;
        this.gridX = gridX;
        this.gridY = gridY;
        this.renderX = gridX;
        this.renderY = gridY;
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

    public void update(float deltaSeconds, float speedTilesPerSecond) {
        if (deltaSeconds <= 0f) {
            return;
        }
        if (speedTilesPerSecond <= 0f) {
            renderX = gridX;
            renderY = gridY;
            return;
        }

        float targetX = gridX;
        float targetY = gridY;

        float dx = targetX - renderX;
        float dy = targetY - renderY;
        float dist2 = dx * dx + dy * dy;
        if (dist2 < 0.000001f) {
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

    public void moveTo(int x, int y, Grid grid) {
        if (!grid.isWalkable(x, y)) {
            return;
        }
        this.gridX = x;
        this.gridY = y;
    }

    public void tryMoveBy(int dx, int dy, Grid grid) {
        moveTo(gridX + dx, gridY + dy, grid);
    }
}
