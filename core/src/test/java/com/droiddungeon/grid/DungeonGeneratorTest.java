package com.droiddungeon.grid;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DungeonGeneratorTest {

    @Test
    void carveColumnUsesExactWidthEven() throws Exception {
        Grid grid = new Grid(20, 20, 1f, TileMaterial.VOID);
        DungeonGenerator generator = new DungeonGenerator(20, 20, 777L, 4);
        invokeCarveColumn(generator, grid, 10, 10);

        assertTile(grid, 10, 9, TileMaterial.STONE);
        assertTile(grid, 10, 10, TileMaterial.STONE);
        assertTile(grid, 10, 11, TileMaterial.STONE);
        assertTile(grid, 10, 12, TileMaterial.STONE);
        assertEquals(TileMaterial.VOID, grid.getTileMaterial(10, 8));
    }

    @Test
    void carveColumnUsesExactWidthOdd() throws Exception {
        Grid grid = new Grid(20, 20, 1f, TileMaterial.VOID);
        DungeonGenerator generator = new DungeonGenerator(20, 20, 777L, 3);
        invokeCarveColumn(generator, grid, 5, 5);

        assertTile(grid, 5, 4, TileMaterial.STONE);
        assertTile(grid, 5, 5, TileMaterial.STONE);
        assertTile(grid, 5, 6, TileMaterial.STONE);
        assertEquals(TileMaterial.VOID, grid.getTileMaterial(5, 3));
    }

    @Test
    void generatedDungeonRoomsAreConnectedToSpawn() {
        int cols = 50;
        int rows = 40;
        float tileSize = 1f;
        DungeonGenerator.DungeonLayout layout = DungeonGenerator.generate(cols, rows, tileSize, 12345L);
        Grid grid = layout.grid();

        Set<Long> reachable = bfsReachable(grid, layout.spawnX(), layout.spawnY());
        for (DungeonGenerator.Room room : layout.rooms()) {
            long key = key(room.centerX(), room.centerY());
            assertTrue(reachable.contains(key), "Room center not reachable: " + room.centerX() + "," + room.centerY());
        }
    }

    private static void invokeCarveColumn(DungeonGenerator generator, Grid grid, int x, int centerY) throws Exception {
        var method = DungeonGenerator.class.getDeclaredMethod("carveColumn", Grid.class, int.class, int.class);
        method.setAccessible(true);
        method.invoke(generator, grid, x, centerY);
    }

    private static void assertTile(Grid grid, int x, int y, TileMaterial expected) {
        assertEquals(expected, grid.getTileMaterial(x, y), "Unexpected tile at " + x + "," + y);
    }

    private static Set<Long> bfsReachable(Grid grid, int startX, int startY) {
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
                if (!grid.isInside(nx, ny) || !grid.isWalkable(nx, ny)) {
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
