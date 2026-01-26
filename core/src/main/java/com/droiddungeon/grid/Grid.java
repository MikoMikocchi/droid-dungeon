package com.droiddungeon.grid;

public final class Grid {
    private final int columns;
    private final int rows;
    private final float tileSize;
    private final TileMaterial[][] tileMaterials;

    public Grid(int columns, int rows, float tileSize) {
        this(columns, rows, tileSize, TileMaterial.STONE);
    }

    public Grid(int columns, int rows, float tileSize, TileMaterial fillMaterial) {
        if (columns <= 0 || rows <= 0) {
            throw new IllegalArgumentException("Grid size must be positive");
        }
        if (tileSize <= 0f) {
            throw new IllegalArgumentException("tileSize must be positive");
        }
        this.columns = columns;
        this.rows = rows;
        this.tileSize = tileSize;
        TileMaterial material = fillMaterial == null ? TileMaterial.STONE : fillMaterial;
        this.tileMaterials = new TileMaterial[columns][rows];
        fill(material);
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

    public boolean isWalkable(int x, int y) {
        if (!isInside(x, y)) {
            return false;
        }
        return tileMaterials[x][y].isWalkable();
    }

    public TileMaterial getTileMaterial(int x, int y) {
        ensureInside(x, y);
        return tileMaterials[x][y];
    }

    public void setTileMaterial(int x, int y, TileMaterial material) {
        if (material == null) {
            throw new IllegalArgumentException("material must not be null");
        }
        ensureInside(x, y);
        tileMaterials[x][y] = material;
    }

    public void fill(TileMaterial material) {
        if (material == null) {
            throw new IllegalArgumentException("material must not be null");
        }
        for (int x = 0; x < columns; x++) {
            for (int y = 0; y < rows; y++) {
                tileMaterials[x][y] = material;
            }
        }
    }

    private void ensureInside(int x, int y) {
        if (!isInside(x, y)) {
            throw new IndexOutOfBoundsException("Grid position out of bounds: (" + x + ", " + y + ")");
        }
    }
}
