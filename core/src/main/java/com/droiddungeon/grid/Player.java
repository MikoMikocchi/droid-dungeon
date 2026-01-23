package com.droiddungeon.grid;

public final class Player {
    private int gridX;
    private int gridY;

    private float renderX;
    private float renderY;

    public Player(int gridX, int gridY) {
        this.gridX = gridX;
        this.gridY = gridY;
        this.renderX = gridX;
        this.renderY = gridY;
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridY() {
        return gridY;
    }

    public float getRenderX() {
        return renderX;
    }

    public float getRenderY() {
        return renderY;
    }

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
        if (!grid.isInside(x, y)) {
            return;
        }
        this.gridX = x;
        this.gridY = y;
    }

    public void tryMoveBy(int dx, int dy, Grid grid) {
        moveTo(gridX + dx, gridY + dy, grid);
    }
}
