package com.droiddungeon.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Very small dungeon generator: scatters rectangle rooms, then connects them with L-shaped corridors.
 * Floors are carved as {@link TileMaterial#STONE}, the rest stays {@link TileMaterial#VOID}.
 * Room types (SAFE / DANGER) are kept for future use; all floors render the same for now.
 */
public final class DungeonGenerator {
    private final Random random;
    private final int columns;
    private final int rows;

    private final int minRoomSize = 5;
    private final int maxRoomSize = 10;
    private final int targetRooms;
    private final int maxPlacementAttempts;
    private final int corridorWidth = 2;

    private DungeonGenerator(int columns, int rows, long seed) {
        this.columns = columns;
        this.rows = rows;
        this.random = new Random(seed);
        // Rough heuristic: enough rooms to fill ~20-25% of area with floors.
        this.targetRooms = Math.max(14, Math.min(40, (columns * rows) / 160));
        this.maxPlacementAttempts = targetRooms * 8;
    }

    public static DungeonLayout generate(int columns, int rows, float tileSize, long seed) {
        DungeonGenerator generator = new DungeonGenerator(columns, rows, seed);
        return generator.generate(tileSize);
    }

    private DungeonLayout generate(float tileSize) {
        Grid grid = new Grid(columns, rows, tileSize, TileMaterial.VOID);
        List<Room> rooms = placeRooms();
        if (rooms.isEmpty()) {
            // Fallback: carve a single small room in the middle.
            Room center = new Room(columns / 2 - 2, rows / 2 - 2, 5, 5, RoomType.SAFE);
            carveRoom(grid, center);
            return new DungeonLayout(grid, center.centerX(), center.centerY(), List.of(center));
        }

        carveRooms(grid, rooms);
        connectRooms(grid, rooms);

        Room spawnRoom = rooms.getFirst();
        // Ensure spawn room is safe for early game.
        spawnRoom.type = RoomType.SAFE;
        int spawnX = spawnRoom.centerX();
        int spawnY = spawnRoom.centerY();

        return new DungeonLayout(grid, spawnX, spawnY, Collections.unmodifiableList(rooms));
    }

    private List<Room> placeRooms() {
        List<Room> rooms = new ArrayList<>();
        int attempts = 0;
        while (rooms.size() < targetRooms && attempts < maxPlacementAttempts) {
            attempts++;
            int w = random.nextInt(maxRoomSize - minRoomSize + 1) + minRoomSize;
            int h = random.nextInt(maxRoomSize - minRoomSize + 1) + minRoomSize;
            int x = random.nextInt(Math.max(1, columns - w - 1)) + 1; // leave 1-tile border
            int y = random.nextInt(Math.max(1, rows - h - 1)) + 1;

            Room candidate = new Room(x, y, w, h, randomRoomType());
            if (overlapsExisting(candidate, rooms)) {
                continue;
            }
            rooms.add(candidate);
        }
        return rooms;
    }

    private RoomType randomRoomType() {
        // 60% safe, 40% danger for later gameplay variety.
        return random.nextFloat() < 0.6f ? RoomType.SAFE : RoomType.DANGER;
    }

    private static boolean overlapsExisting(Room candidate, List<Room> rooms) {
        // Keep a 1-tile padding so rooms don't touch; corridors will handle connectivity.
        int pad = 1;
        int cx0 = candidate.x - pad;
        int cy0 = candidate.y - pad;
        int cx1 = candidate.x + candidate.width + pad;
        int cy1 = candidate.y + candidate.height + pad;
        for (Room room : rooms) {
            int rx0 = room.x;
            int ry0 = room.y;
            int rx1 = room.x + room.width;
            int ry1 = room.y + room.height;
            if (cx0 < rx1 && cx1 > rx0 && cy0 < ry1 && cy1 > ry0) {
                return true;
            }
        }
        return false;
    }

    private void carveRooms(Grid grid, List<Room> rooms) {
        for (Room room : rooms) {
            carveRoom(grid, room);
        }
    }

    private void carveRoom(Grid grid, Room room) {
        for (int x = room.x; x < room.x + room.width; x++) {
            for (int y = room.y; y < room.y + room.height; y++) {
                grid.setTileMaterial(x, y, TileMaterial.STONE);
            }
        }
    }

    private void connectRooms(Grid grid, List<Room> rooms) {
        if (rooms.size() < 2) {
            return;
        }

        // Build a list of potential edges sorted by center distance (Prim's/MST-like).
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) {
            for (int j = i + 1; j < rooms.size(); j++) {
                rooms.get(i);
                Room a = rooms.get(i);
                Room b = rooms.get(j);
                int dx = a.centerX() - b.centerX();
                int dy = a.centerY() - b.centerY();
                int dist2 = dx * dx + dy * dy;
                edges.add(new Edge(i, j, dist2));
            }
        }
        edges.sort(Comparator.comparingInt(e -> e.dist2));

        // Simple Union-Find for MST.
        int[] parent = new int[rooms.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }

        int connections = 0;
        for (Edge edge : edges) {
            if (union(parent, edge.aIndex, edge.bIndex)) {
                carveCorridor(grid, rooms.get(edge.aIndex), rooms.get(edge.bIndex));
                connections++;
            }
            if (connections >= rooms.size() - 1) {
                break;
            }
        }

        // Extra loops to reduce dead-ends.
        int extra = Math.min(rooms.size() / 3, 6);
        int added = 0;
        int attempts = 0;
        while (added < extra && attempts < edges.size()) {
            Edge edge = edges.get(random.nextInt(edges.size()));
            carveCorridor(grid, rooms.get(edge.aIndex), rooms.get(edge.bIndex));
            added++;
            attempts++;
        }
    }

    private void carveCorridor(Grid grid, Room a, Room b) {
        int ax = a.centerX();
        int ay = a.centerY();
        int bx = b.centerX();
        int by = b.centerY();

        boolean horizontalFirst = random.nextBoolean();
        if (horizontalFirst) {
            carveHorizontal(grid, ax, bx, ay);
            carveVertical(grid, ay, by, bx);
        } else {
            carveVertical(grid, ay, by, ax);
            carveHorizontal(grid, ax, bx, by);
        }
    }

    private void carveHorizontal(Grid grid, int x0, int x1, int y) {
        int start = Math.min(x0, x1);
        int end = Math.max(x0, x1);
        for (int x = start; x <= end; x++) {
            carveColumn(grid, x, y);
        }
    }

    private void carveVertical(Grid grid, int y0, int y1, int x) {
        int start = Math.min(y0, y1);
        int end = Math.max(y0, y1);
        for (int y = start; y <= end; y++) {
            carveColumn(grid, x, y);
        }
    }

    private void carveColumn(Grid grid, int x, int centerY) {
        int half = corridorWidth / 2;
        int startOffset = -half;
        int endOffset = (half - 1);
        for (int offset = startOffset; offset <= endOffset; offset++) {
            int y = centerY + offset;
            if (grid.isInside(x, y)) {
                grid.setTileMaterial(x, y, TileMaterial.STONE);
            }
        }
    }

    private static int find(int[] parent, int i) {
        if (parent[i] == i) {
            return i;
        }
        parent[i] = find(parent, parent[i]);
        return parent[i];
    }

    private static boolean union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra == rb) {
            return false;
        }
        parent[rb] = ra;
        return true;
    }

    public record DungeonLayout(Grid grid, int spawnX, int spawnY, List<Room> rooms) {}

    public enum RoomType { SAFE, DANGER }

    public static final class Room {
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public RoomType type;

        public Room(int x, int y, int width, int height, RoomType type) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.type = type;
        }

        public int centerX() {
            return x + width / 2;
        }

        public int centerY() {
            return y + height / 2;
        }
    }

    private record Edge(int aIndex, int bIndex, int dist2) {}
}
