package com.droiddungeon.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Chunk-based dungeon generator that streams rooms on demand.
 * Every chunk is carved independently but deterministically from the world seed,
 * producing an effectively infinite dungeon.
 *
 * Floors are carved as {@link TileMaterial#STONE}. RoomType metadata is stored per tile
 * so renderers can tint SAFE / DANGER rooms differently without changing collision.
 */
public final class DungeonGenerator {
    private static final int DEFAULT_CHUNK_SIZE = 48;
    private static final int DEFAULT_CORRIDOR_WIDTH = 2;

    private DungeonGenerator() {}

    /**
     * Generates an infinite dungeon backed by a chunked {@link Grid}.
     *
     * @param tileSize size of a tile in world units
     * @param seed     world seed, deterministic across sessions
     */
    public static DungeonLayout generateInfinite(float tileSize, long seed) {
        ChunkGenerator chunkGenerator = new ChunkGenerator(seed, DEFAULT_CHUNK_SIZE, DEFAULT_CORRIDOR_WIDTH);
        Grid grid = new Grid(tileSize, chunkGenerator);

        // Force spawn chunk so we can pick a safe spawn position.
        Chunk spawnChunk = grid.ensureChunk(0, 0);
        Room spawnRoom = chunkGenerator.chooseSpawnRoom(spawnChunk);
        int spawnX = spawnRoom != null ? spawnRoom.centerX() : 0;
        int spawnY = spawnRoom != null ? spawnRoom.centerY() : 0;

        List<Room> seedRooms = spawnChunk != null ? Collections.unmodifiableList(spawnChunk.rooms()) : List.of();
        return new DungeonLayout(grid, spawnX, spawnY, seedRooms);
    }

    /**
     * Record returned by generators. For infinite worlds {@code rooms} contains only
     * the rooms from the spawn chunk (for quick UI lookups); the world keeps streaming
     * additional rooms as you explore.
     */
    public record DungeonLayout(Grid grid, int spawnX, int spawnY, List<Room> rooms) {}

    public enum RoomType { SAFE, DANGER }

    /**
     * Immutable room definition in world-space coordinates.
     */
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

    /**
     * Raw per-tile data stored inside chunks.
     */
    public static final class TileCell {
        public TileMaterial floor;
        public BlockMaterial block;
        public float blockHealth;
        public RoomType roomType;

        public TileCell(TileMaterial floor, BlockMaterial block, RoomType roomType) {
            this.floor = floor;
            this.block = block;
            this.blockHealth = block != null ? block.maxHealth() : 0f;
            this.roomType = roomType;
        }
    }

    /**
     * Runtime chunk data produced by {@link ChunkGenerator}.
     */
    public static final class Chunk {
        private final int chunkX;
        private final int chunkY;
        private final int originX;
        private final int originY;
        private final TileCell[][] cells;
        private final List<Room> rooms;

        Chunk(int chunkX, int chunkY, int originX, int originY, TileCell[][] cells, List<Room> rooms) {
            this.chunkX = chunkX;
            this.chunkY = chunkY;
            this.originX = originX;
            this.originY = originY;
            this.cells = cells;
            this.rooms = rooms;
        }

        public TileCell cellAt(int worldX, int worldY) {
            int localX = worldX - originX;
            int localY = worldY - originY;
            if (localX < 0 || localX >= cells.length || localY < 0 || localY >= cells[0].length) {
                return new TileCell(TileMaterial.VOID, null, null);
            }
            return cells[localX][localY];
        }

        public List<Room> rooms() {
            return rooms;
        }

        public int originX() {
            return originX;
        }

        public int originY() {
            return originY;
        }

        public int chunkX() {
            return chunkX;
        }

        public int chunkY() {
            return chunkY;
        }
    }

    /**
     * Deterministic chunk generator responsible for carving rooms and corridors.
     */
    public static final class ChunkGenerator {
        private final long worldSeed;
        private final int chunkSize;
        private final int corridorWidth;

        private final int minRoomSize = 6;
        private final int maxRoomSize = 14;

        public ChunkGenerator(long worldSeed, int chunkSize, int corridorWidth) {
            this.worldSeed = worldSeed;
            this.chunkSize = Math.max(24, chunkSize);
            this.corridorWidth = Math.max(1, corridorWidth);
        }

        public int chunkSize() {
            return chunkSize;
        }

        public Chunk generate(int chunkX, int chunkY) {
            int originX = chunkX * chunkSize;
            int originY = chunkY * chunkSize;

            TileCell[][] cells = new TileCell[chunkSize][chunkSize];
            for (int x = 0; x < chunkSize; x++) {
                for (int y = 0; y < chunkSize; y++) {
                    // Default: solid stone block sitting on stone floor.
                    cells[x][y] = new TileCell(TileMaterial.STONE, BlockMaterial.STONE, null);
                }
            }

            Random rng = rngForChunk(chunkX, chunkY, 0xA55A1EAFL);
            boolean isSpawnChunk = chunkX == 0 && chunkY == 0;
            List<Room> rooms = placeRooms(originX, originY, rng, isSpawnChunk);

            // Edge connectors ensure cross-chunk continuity.
            List<Node> nodes = new ArrayList<>();
            for (Room room : rooms) {
                nodes.add(Node.fromRoom(room));
            }

            List<Connector> connectors = connectorsForChunk(chunkX, chunkY);
            for (Connector connector : connectors) {
                if (connector.open) {
                    nodes.add(Node.fromConnector(connector));
                }
            }

            carveRooms(cells, rooms, originX, originY);
            if (nodes.size() >= 2) {
                connectNodesMst(cells, nodes);
            }
            return new Chunk(chunkX, chunkY, originX, originY, cells, rooms);
        }

        public Room chooseSpawnRoom(Chunk chunk) {
            if (chunk == null) {
                return null;
            }
            for (Room room : chunk.rooms()) {
                if (room.type == RoomType.SAFE) {
                    return room;
                }
            }
            return chunk.rooms().isEmpty() ? null : chunk.rooms().getFirst();
        }

        private List<Room> placeRooms(int originX, int originY, Random rng, boolean forceSafeCenter) {
            int targetRooms = 3 + rng.nextInt(2);
            int maxAttempts = 40;
            List<Room> rooms = new ArrayList<>();

            if (forceSafeCenter) {
                int w = clamp(rng.nextInt(maxRoomSize - minRoomSize + 1) + minRoomSize, 8, maxRoomSize);
                int h = clamp(rng.nextInt(maxRoomSize - minRoomSize + 1) + minRoomSize, 8, maxRoomSize);
                int x = originX + (chunkSize - w) / 2;
                int y = originY + (chunkSize - h) / 2;
                Room spawn = new Room(x, y, w, h, RoomType.SAFE);
                rooms.add(spawn);
            }

            int attempts = 0;
            while (rooms.size() < targetRooms && attempts < maxAttempts) {
                attempts++;
                int w = rng.nextInt(maxRoomSize - minRoomSize + 1) + minRoomSize;
                int h = rng.nextInt(maxRoomSize - minRoomSize + 1) + minRoomSize;
                int x = originX + rng.nextInt(Math.max(1, chunkSize - w - 4)) + 2;
                int y = originY + rng.nextInt(Math.max(1, chunkSize - h - 4)) + 2;

                Room candidate = new Room(x, y, w, h, randomRoomType(rng));
                if (overlapsExisting(candidate, rooms)) {
                    continue;
                }
                rooms.add(candidate);
            }
            return rooms;
        }

        private RoomType randomRoomType(Random rng) {
            return rng.nextFloat() < 0.58f ? RoomType.SAFE : RoomType.DANGER;
        }

        private boolean overlapsExisting(Room candidate, List<Room> rooms) {
            int pad = 2;
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

        private void carveRooms(TileCell[][] cells, List<Room> rooms, int originX, int originY) {
            for (Room room : rooms) {
                for (int x = room.x; x < room.x + room.width; x++) {
                    for (int y = room.y; y < room.y + room.height; y++) {
                        int lx = x - originX;
                        int ly = y - originY;
                        if (lx < 0 || lx >= chunkSize || ly < 0 || ly >= chunkSize) {
                            continue;
                        }
                        cells[lx][ly].block = null; // carve space
                        cells[lx][ly].blockHealth = 0f;
                        cells[lx][ly].floor = TileMaterial.STONE;
                        cells[lx][ly].roomType = room.type;
                    }
                }
            }
        }

        private void connectNodesMst(TileCell[][] cells, List<Node> nodes) {
            List<Edge> edges = new ArrayList<>();
            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    Node a = nodes.get(i);
                    Node b = nodes.get(j);
                    int dx = a.x - b.x;
                    int dy = a.y - b.y;
                    int dist2 = dx * dx + dy * dy;
                    edges.add(new Edge(i, j, dist2));
                }
            }
            edges.sort(Comparator.comparingInt(e -> e.dist2));

            int[] parent = new int[nodes.size()];
            for (int i = 0; i < parent.length; i++) {
                parent[i] = i;
            }

            int connections = 0;
            for (Edge edge : edges) {
                if (union(parent, edge.aIndex, edge.bIndex)) {
                    carveCorridor(cells, nodes.get(edge.aIndex), nodes.get(edge.bIndex));
                    connections++;
                }
                if (connections >= nodes.size() - 1) {
                    break;
                }
            }

            // Add a handful of extra loops for variety.
            int chunkX = Math.floorDiv(nodes.get(0).x, chunkSize);
            int chunkY = Math.floorDiv(nodes.get(0).y, chunkSize);
            Random loopRng = rngForChunk(chunkX, chunkY, 0xBEEFC0DEDEADL);
            Set<String> carvedPairs = new HashSet<>();
            int extra = Math.min(nodes.size() / 3 + 1, 6);
            int added = 0;
            int attempts = 0;
            while (added < extra && attempts < edges.size()) {
                Edge edge = edges.get(loopRng.nextInt(edges.size()));
                String key = edge.aIndex < edge.bIndex ? edge.aIndex + "-" + edge.bIndex : edge.bIndex + "-" + edge.aIndex;
                if (carvedPairs.contains(key)) {
                    attempts++;
                    continue;
                }
                carveCorridor(cells, nodes.get(edge.aIndex), nodes.get(edge.bIndex));
                carvedPairs.add(key);
                added++;
                attempts++;
            }
        }

        private void carveCorridor(TileCell[][] cells, Node a, Node b) {
            int ax = a.x;
            int ay = a.y;
            int bx = b.x;
            int by = b.y;

            boolean horizontalFirst = (ax + ay + bx + by & 1) == 0;
            if (horizontalFirst) {
                carveHorizontal(cells, ax, bx, ay);
                carveVertical(cells, ay, by, bx);
            } else {
                carveVertical(cells, ay, by, ax);
                carveHorizontal(cells, ax, bx, by);
            }
        }

        private void carveHorizontal(TileCell[][] cells, int x0, int x1, int y) {
            int start = Math.min(x0, x1);
            int end = Math.max(x0, x1);
            for (int x = start; x <= end; x++) {
                carveColumn(cells, x, y);
            }
        }

        private void carveVertical(TileCell[][] cells, int y0, int y1, int x) {
            int start = Math.min(y0, y1);
            int end = Math.max(y0, y1);
            for (int y = start; y <= end; y++) {
                carveColumn(cells, x, y);
            }
        }

        private void carveColumn(TileCell[][] cells, int worldX, int centerY) {
            int chunkOriginX = Math.floorDiv(worldX, chunkSize) * chunkSize;
            int localX = worldX - chunkOriginX;
            if (localX < 0 || localX >= chunkSize) {
                return;
            }

            int start = centerY - (corridorWidth - 1) / 2;
            for (int i = 0; i < corridorWidth; i++) {
                int y = start + i;
                int chunkOriginY = Math.floorDiv(y, chunkSize) * chunkSize;
                int localY = y - chunkOriginY;
                if (localY < 0 || localY >= chunkSize) {
                    continue;
                }
                cells[localX][localY].block = null; // carve corridor air
                cells[localX][localY].blockHealth = 0f;
                cells[localX][localY].floor = TileMaterial.STONE;
                // roomType left as-is for corridors; renderer tints by null -> default.
            }
        }

        private List<Connector> connectorsForChunk(int chunkX, int chunkY) {
            List<Connector> connectors = new ArrayList<>();
            for (Direction dir : Direction.values()) {
                connectors.add(connectorForEdge(chunkX, chunkY, dir));
            }
            return connectors;
        }

        private Connector connectorForEdge(int chunkX, int chunkY, Direction dir) {
            int neighborX = chunkX + dir.dx;
            int neighborY = chunkY + dir.dy;

            int ax = Math.min(chunkX, neighborX);
            int ay = Math.min(chunkY, neighborY);
            int bx = Math.max(chunkX, neighborX);
            int by = Math.max(chunkY, neighborY);

            long h = mix(worldSeed, ax, ay, bx, by, dir.axisId);
            Random rng = new Random(h);
            boolean open = rng.nextFloat() < 0.88f;
            int offset = 3 + rng.nextInt(chunkSize - 6);

            int originX = chunkX * chunkSize;
            int originY = chunkY * chunkSize;
            int x = dir.dx > 0 ? originX + chunkSize - 1 : dir.dx < 0 ? originX : originX + offset;
            int y = dir.dy > 0 ? originY + chunkSize - 1 : dir.dy < 0 ? originY : originY + offset;
            return new Connector(x, y, dir, open);
        }

        private Random rngForChunk(int chunkX, int chunkY, long salt) {
            long mixed = mix(worldSeed, chunkX, chunkY, salt);
            return new Random(mixed);
        }

        private long mix(long seed, int a, int b, long salt) {
            long h = seed ^ salt;
            h ^= (long) a * 0x9E3779B97F4A7C15L;
            h = Long.rotateLeft(h, 17);
            h ^= (long) b * 0xC2B2AE3DL;
            h *= 0x165667919E3779F9L;
            h ^= h >>> 33;
            return h;
        }

        private long mix(long seed, int ax, int ay, int bx, int by, int axisId) {
            long h = seed ^ 0xD1B54A32D192ED03L;
            h ^= (long) ax * 0x9E3779B97F4A7C15L;
            h ^= (long) ay * 0xC2B2AE3D27D4EB4FL;
            h = Long.rotateLeft(h, 21);
            h ^= (long) bx * 0x165667B19E3779F9L;
            h ^= (long) by * 0x27D4EB2F165667C5L;
            h ^= axisId * 0x9E37;
            h ^= h >>> 29;
            return h;
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static int find(int[] parent, int i) {
            if (parent[i] == i) return i;
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

        private record Node(int x, int y, boolean isConnector) {
            static Node fromRoom(Room room) {
                return new Node(room.centerX(), room.centerY(), false);
            }

            static Node fromConnector(Connector connector) {
                return new Node(connector.x, connector.y, true);
            }
        }

        private record Edge(int aIndex, int bIndex, int dist2) {}

        private enum Direction {
            NORTH(0, 1, 1),
            SOUTH(0, -1, 1),
            EAST(1, 0, 0),
            WEST(-1, 0, 0);

            final int dx;
            final int dy;
            final int axisId;

            Direction(int dx, int dy, int axisId) {
                this.dx = dx;
                this.dy = dy;
                this.axisId = axisId;
            }
        }

        private record Connector(int x, int y, Direction direction, boolean open) {}
    }
}
