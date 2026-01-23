package com.droiddungeon.grid;

public final class Player {
    private int gridX;
    private int gridY;

    public Player(int gridX, int gridY) {
        this.gridX = gridX;
        this.gridY = gridY;
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridY() {
        return gridY;
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
