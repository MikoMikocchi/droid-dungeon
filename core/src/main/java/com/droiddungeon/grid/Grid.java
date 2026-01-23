package com.droiddungeon.grid;

public final class Grid {
    private final int columns;
    private final int rows;
    private final float tileSize;

    public Grid(int columns, int rows, float tileSize) {
        if (columns <= 0 || rows <= 0) {
            throw new IllegalArgumentException("Grid size must be positive");
        }
        if (tileSize <= 0f) {
            throw new IllegalArgumentException("tileSize must be positive");
        }
        this.columns = columns;
        this.rows = rows;
        this.tileSize = tileSize;
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public float getTileSize() {
        return tileSize;
    }

    public float getWorldWidth() {
        return columns * tileSize;
    }

    public float getWorldHeight() {
        return rows * tileSize;
    }

    public boolean isInside(int x, int y) {
        return x >= 0 && x < columns && y >= 0 && y < rows;
    }
}
