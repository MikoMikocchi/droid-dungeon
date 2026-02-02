package com.droiddungeon.grid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Infinite, chunk-backed tile field. Chunks are generated lazily via {@link
 * DungeonGenerator.ChunkGenerator} and cached in memory. Coordinates are world-space tile indices
 * (can be negative).
 */
public final class Grid {
  private final float tileSize;
  private final DungeonGenerator.ChunkGenerator chunkGenerator;
  private final Map<Long, DungeonGenerator.Chunk> chunks = new HashMap<>();

  // Bounds of generated tiles (inclusive) for debug visualisation.
  private int minGeneratedX = 0;
  private int maxGeneratedX = 0;
  private int minGeneratedY = 0;
  private int maxGeneratedY = 0;

  public Grid(float tileSize, DungeonGenerator.ChunkGenerator chunkGenerator) {
    if (tileSize <= 0f) {
      throw new IllegalArgumentException("tileSize must be positive");
    }
    this.tileSize = tileSize;
    this.chunkGenerator = Objects.requireNonNull(chunkGenerator, "chunkGenerator");
  }

  /** Ensures a chunk is generated and cached. */
  public DungeonGenerator.Chunk ensureChunk(int chunkX, int chunkY) {
    long key = key(chunkX, chunkY);
    DungeonGenerator.Chunk chunk = chunks.get(key);
    if (chunk != null) {
      return chunk;
    }
    DungeonGenerator.Chunk generated = chunkGenerator.generate(chunkX, chunkY);
    chunks.put(key, generated);
    updateBounds(generated);
    return generated;
  }

  private void updateBounds(DungeonGenerator.Chunk chunk) {
    int originX = chunk.originX();
    int originY = chunk.originY();
    int endX = originX + chunkGenerator.chunkSize() - 1;
    int endY = originY + chunkGenerator.chunkSize() - 1;
    if (chunks.size() == 1) {
      minGeneratedX = originX;
      maxGeneratedX = endX;
      minGeneratedY = originY;
      maxGeneratedY = endY;
    } else {
      minGeneratedX = Math.min(minGeneratedX, originX);
      maxGeneratedX = Math.max(maxGeneratedX, endX);
      minGeneratedY = Math.min(minGeneratedY, originY);
      maxGeneratedY = Math.max(maxGeneratedY, endY);
    }
  }

  private long key(int chunkX, int chunkY) {
    return ((long) chunkX << 32) ^ (chunkY & 0xffffffffL);
  }

  private DungeonGenerator.TileCell cellAt(int x, int y) {
    int chunkX = Math.floorDiv(x, chunkGenerator.chunkSize());
    int chunkY = Math.floorDiv(y, chunkGenerator.chunkSize());
    DungeonGenerator.Chunk chunk = ensureChunk(chunkX, chunkY);
    return chunk.cellAt(x, y);
  }

  public TileMaterial getTileMaterial(int x, int y) {
    return cellAt(x, y).floor;
  }

  public BlockMaterial getBlockMaterial(int x, int y) {
    return cellAt(x, y).block;
  }

  public float getBlockHealth(int x, int y) {
    return cellAt(x, y).blockHealth;
  }

  public boolean hasBlock(int x, int y) {
    return getBlockMaterial(x, y) != null;
  }

  public boolean damageBlock(int x, int y, float amount) {
    if (amount <= 0f) {
      return false;
    }
    DungeonGenerator.TileCell cell = mutableCell(x, y);
    if (cell.block == null) {
      return false;
    }
    cell.blockHealth = Math.max(0f, cell.blockHealth - amount);
    if (cell.blockHealth <= 0f) {
      cell.block = null;
      cell.blockHealth = 0f;
      return true;
    }
    return false;
  }

  public void setBlock(int x, int y, BlockMaterial block) {
    DungeonGenerator.TileCell cell = mutableCell(x, y);
    cell.block = block;
    cell.blockHealth = block != null ? block.maxHealth() : 0f;
  }

  private DungeonGenerator.TileCell mutableCell(int x, int y) {
    int chunkX = Math.floorDiv(x, chunkGenerator.chunkSize());
    int chunkY = Math.floorDiv(y, chunkGenerator.chunkSize());
    DungeonGenerator.Chunk chunk = ensureChunk(chunkX, chunkY);
    return chunk.cellAt(x, y);
  }

  public DungeonGenerator.RoomType getRoomType(int x, int y) {
    return cellAt(x, y).roomType;
  }

  public boolean isWalkable(int x, int y) {
    DungeonGenerator.TileCell cell = cellAt(x, y);
    return cell.floor.isWalkable() && cell.block == null;
  }

  public boolean isTransparent(int x, int y) {
    DungeonGenerator.TileCell cell = cellAt(x, y);
    return cell.block == null || cell.block.transparent();
  }

  /** Infinite world -> always true. */
  public boolean isInside(int x, int y) {
    return true;
  }

  public float getTileSize() {
    return tileSize;
  }

  public int getChunkSize() {
    return chunkGenerator.chunkSize();
  }

  public int getMinGeneratedX() {
    return minGeneratedX;
  }

  public int getMaxGeneratedX() {
    return maxGeneratedX;
  }

  public int getMinGeneratedY() {
    return minGeneratedY;
  }

  public int getMaxGeneratedY() {
    return maxGeneratedY;
  }

  /**
   * Returns rooms whose bounding boxes intersect the supplied tile bounds (inclusive). Chunks
   * covering the area will be generated on demand.
   */
  public List<DungeonGenerator.Room> getRoomsInArea(int minX, int minY, int maxX, int maxY) {
    int chunkSize = chunkGenerator.chunkSize();
    int minChunkX = Math.floorDiv(minX, chunkSize);
    int maxChunkX = Math.floorDiv(maxX, chunkSize);
    int minChunkY = Math.floorDiv(minY, chunkSize);
    int maxChunkY = Math.floorDiv(maxY, chunkSize);

    List<DungeonGenerator.Room> result = new ArrayList<>();
    for (int cx = minChunkX; cx <= maxChunkX; cx++) {
      for (int cy = minChunkY; cy <= maxChunkY; cy++) {
        DungeonGenerator.Chunk chunk = ensureChunk(cx, cy);
        for (DungeonGenerator.Room room : chunk.rooms()) {
          if (room.x + room.width < minX || room.x > maxX) {
            continue;
          }
          if (room.y + room.height < minY || room.y > maxY) {
            continue;
          }
          result.add(room);
        }
      }
    }
    return result;
  }
}
