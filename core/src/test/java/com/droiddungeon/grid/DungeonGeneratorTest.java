package com.droiddungeon.grid;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.droiddungeon.grid.BlockMaterial;

class DungeonGeneratorTest {

    @Test
    void carveColumnUsesExactWidthEven() throws Exception {
        DungeonGenerator.ChunkGenerator generator = new DungeonGenerator.ChunkGenerator(777L, 32, 4);
        DungeonGenerator.TileCell[][] cells = freshCells(generator.chunkSize());
        invokeCarveColumn(generator, cells, 10, 10);

        assertTile(cells, 10, 9, TileMaterial.STONE);
        assertTile(cells, 10, 10, TileMaterial.STONE);
        assertTile(cells, 10, 11, TileMaterial.STONE);
        assertTile(cells, 10, 12, TileMaterial.STONE);
        assertNotNull(cells[10][8].block);
    }

    @Test
    void carveColumnUsesExactWidthOdd() throws Exception {
        DungeonGenerator.ChunkGenerator generator = new DungeonGenerator.ChunkGenerator(777L, 32, 3);
        DungeonGenerator.TileCell[][] cells = freshCells(generator.chunkSize());
        invokeCarveColumn(generator, cells, 5, 5);

        assertTile(cells, 5, 4, TileMaterial.STONE);
        assertTile(cells, 5, 5, TileMaterial.STONE);
        assertTile(cells, 5, 6, TileMaterial.STONE);
        assertNotNull(cells[5][3].block);
    }

    @Test
    void generatedDungeonRoomsAreConnectedToSpawn() {
        float tileSize = 1f;
        DungeonGenerator.DungeonLayout layout = DungeonGenerator.generateInfinite(tileSize, 12345L);
        Grid grid = layout.grid();
        int chunkSize = grid.getChunkSize();

        Set<Long> reachable = bfsReachable(grid, layout.spawnX(), layout.spawnY(),
                0, 0, chunkSize - 1, chunkSize - 1);
        for (DungeonGenerator.Room room : layout.rooms()) {
            long key = key(room.centerX(), room.centerY());
            assertTrue(reachable.contains(key), "Room center not reachable: " + room.centerX() + "," + room.centerY());
        }
    }

    @Test
    void chooseSpawnRoomPrefersSafeRoomInSpawnChunk() {
        DungeonGenerator.ChunkGenerator generator = new DungeonGenerator.ChunkGenerator(9876L, 48, 2);
        DungeonGenerator.Chunk spawn = generator.generate(0, 0);

        DungeonGenerator.Room room = generator.chooseSpawnRoom(spawn);

        assertNotNull(room, "Spawn room should not be null");
        assertEquals(DungeonGenerator.RoomType.SAFE, room.type, "Spawn room must be SAFE");
        assertTrue(room.centerX() >= spawn.originX() && room.centerX() < spawn.originX() + generator.chunkSize());
        assertTrue(room.centerY() >= spawn.originY() && room.centerY() < spawn.originY() + generator.chunkSize());
    }

    @Test
    void chunkGenerationIsDeterministicPerSeed() {
        DungeonGenerator.ChunkGenerator genA = new DungeonGenerator.ChunkGenerator(13579L, 48, 2);
        DungeonGenerator.ChunkGenerator genB = new DungeonGenerator.ChunkGenerator(13579L, 48, 2);
        DungeonGenerator.ChunkGenerator genDifferent = new DungeonGenerator.ChunkGenerator(24680L, 48, 2);

        List<String> sigA = roomSignatures(genA.generate(2, -1).rooms());
        List<String> sigB = roomSignatures(genB.generate(2, -1).rooms());
        List<String> sigC = roomSignatures(genDifferent.generate(2, -1).rooms());

        assertEquals(sigA, sigB, "Chunks with same seed and coords must match");
        assertNotEquals(sigA, sigC, "Chunks with different seeds should differ most of the time");
    }

    @Test
    void gridReportsRoomsIntersectingArea() {
        DungeonGenerator.ChunkGenerator generator = new DungeonGenerator.ChunkGenerator(555L, 32, 2);
        Grid grid = new Grid(1f, generator);

        int minX = 0, minY = 0, maxX = 20, maxY = 20;
        List<DungeonGenerator.Room> rooms = grid.getRoomsInArea(minX, minY, maxX, maxY);

        assertFalse(rooms.isEmpty(), "Expected at least one room in spawn chunk");
        for (DungeonGenerator.Room room : rooms) {
            boolean intersects = room.x + room.width >= minX && room.x <= maxX
                    && room.y + room.height >= minY && room.y <= maxY;
            assertTrue(intersects, "Room does not intersect query bounds");
        }
        assertTrue(grid.getMinGeneratedX() <= minX);
        assertTrue(grid.getMinGeneratedY() <= minY);
        assertTrue(grid.getMaxGeneratedX() >= maxX);
        assertTrue(grid.getMaxGeneratedY() >= maxY);
    }

    private static DungeonGenerator.TileCell[][] freshCells(int size) {
        DungeonGenerator.TileCell[][] cells = new DungeonGenerator.TileCell[size][size];
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                cells[x][y] = new DungeonGenerator.TileCell(TileMaterial.STONE, BlockMaterial.STONE, null);
            }
        }
        return cells;
    }

    private static void invokeCarveColumn(DungeonGenerator.ChunkGenerator generator,
                                          DungeonGenerator.TileCell[][] cells,
                                          int x, int centerY) throws Exception {
        var method = DungeonGenerator.ChunkGenerator.class.getDeclaredMethod(
                "carveColumn", DungeonGenerator.TileCell[][].class, int.class, int.class);
        method.setAccessible(true);
        method.invoke(generator, cells, x, centerY);
    }

    private static void assertTile(DungeonGenerator.TileCell[][] cells, int x, int y, TileMaterial expected) {
        assertEquals(expected, cells[x][y].floor, "Unexpected floor at " + x + "," + y);
        assertNull(cells[x][y].block, "Expected carved air at " + x + "," + y);
    }

    private static List<String> roomSignatures(List<DungeonGenerator.Room> rooms) {
        List<String> signatures = new ArrayList<>(rooms.size());
        for (DungeonGenerator.Room room : rooms) {
            signatures.add(room.x + ":" + room.y + ":" + room.width + ":" + room.height + ":" + room.type);
        }
        Collections.sort(signatures);
        return signatures;
    }

    private static Set<Long> bfsReachable(Grid grid, int startX, int startY,
                                          int minX, int minY, int maxX, int maxY) {
        Set<Long> visited = new HashSet<>();
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        visited.add(key(startX, startY));

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.remove();
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (nx < minX || nx > maxX || ny < minY || ny > maxY) {
                    continue;
                }
                if (!grid.isWalkable(nx, ny)) {
                    continue;
                }
                long k = key(nx, ny);
                if (visited.add(k)) {
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return visited;
    }

    private static long key(int x, int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }
}
