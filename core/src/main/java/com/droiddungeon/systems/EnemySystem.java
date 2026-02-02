package com.droiddungeon.systems;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

import com.droiddungeon.enemies.Enemy;
import com.droiddungeon.enemies.EnemyType;
import com.droiddungeon.entity.EntityIds;
import com.droiddungeon.entity.EntityWorld;
import com.droiddungeon.grid.DungeonGenerator;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.player.PlayerStats;
import com.droiddungeon.items.GroundItemStore;
import com.droiddungeon.net.dto.EnemySnapshotDto;

import java.util.Map;

/**
 * Spawns and updates hostile entities.
 */
public final class EnemySystem {
    private final Grid grid;
    private final long worldSeed;
    private final EntityWorld entityWorld;
    private final GroundItemStore groundStore;
    private final List<Enemy> enemies = new ArrayList<>();
    private final Set<String> spawnedRooms = new HashSet<>();
    private final SplittableRandom ambientRng;

    public EnemySystem(Grid grid, long worldSeed, EntityWorld entityWorld, GroundItemStore groundStore) {
        this.grid = grid;
        this.worldSeed = worldSeed;
        this.entityWorld = entityWorld;
        this.groundStore = groundStore;
        this.ambientRng = new SplittableRandom(worldSeed ^ 0xACEDBADEL);
    }

    public List<Enemy> getEnemies() {
        return enemies;
    }

    public void reset() {
        enemies.clear();
        spawnedRooms.clear();
    }

    /**
     * Replace or update enemies from authoritative snapshot (network mode).
     */
    public void applySnapshot(EnemySnapshotDto[] addsOrUpdates, int[] removals, boolean full) {
        if (full) {
            enemies.clear();
        }
        if (removals != null && removals.length > 0) {
            for (int id : removals) {
                removeEnemy(id);
            }
        }
        if (addsOrUpdates != null) {
            for (EnemySnapshotDto s : addsOrUpdates) {
                upsertEnemy(s);
            }
        }
    }

    private void removeEnemy(int id) {
        enemies.removeIf(e -> {
            if (e.id() == id) {
                if (entityWorld != null) {
                    entityWorld.remove(e);
                }
                return true;
            }
            return false;
        });
    }

    private void upsertEnemy(EnemySnapshotDto s) {
        removeEnemy(s.id());
        EnemyType type;
        try {
            type = EnemyType.valueOf(s.enemyType());
        } catch (IllegalArgumentException ex) {
            return;
        }
        Enemy enemy = new Enemy(
                s.id(),
                type,
                s.gridX(),
                s.gridY(),
                s.gridX() - 5,
                s.gridY() - 5,
                s.gridX() + 5,
                s.gridY() + 5
        );
        enemies.add(enemy);
        if (entityWorld != null) {
            entityWorld.add(enemy);
        }
    }

    public void update(float deltaSeconds, List<Player> players, Map<Integer, PlayerStats> playerStatsById) {
        if (players == null || players.isEmpty()) {
            return;
        }
        // spawn near all players
        for (Player p : players) {
            spawnNearby(p);
        }

        Set<Long> occupied = new HashSet<>();
        for (Enemy enemy : enemies) {
            occupied.add(key(enemy.getGridX(), enemy.getGridY()));
        }

        for (Enemy enemy : enemies) {
            enemy.tickCooldowns(deltaSeconds);

            Player nearest = findNearestPlayer(enemy, players);
            boolean seesPlayer = nearest != null && seesPlayer(enemy, nearest);
            enemy.setHasLineOfSight(seesPlayer);

            if (seesPlayer && nearest != null) {
                chasePlayer(enemy, nearest, occupied);
                var stats = playerStatsById.get(nearest.id());
                if (stats != null) {
                    attemptAttack(enemy, nearest, stats);
                }
            } else {
                wander(enemy, occupied);
            }
        }

        for (Enemy enemy : enemies) {
            enemy.updateRender(deltaSeconds);
        }

        enemies.removeIf(enemy -> {
            if (enemy.isDead()) {
                if (entityWorld != null) {
                    entityWorld.remove(enemy);
                }
                return true;
            }
            return false;
        });
    }

    private Player findNearestPlayer(Enemy enemy, List<Player> players) {
        Player best = null;
        float bestDist = Float.MAX_VALUE;
        for (Player p : players) {
            float dx = (p.getGridX() + 0.5f) - (enemy.getGridX() + 0.5f);
            float dy = (p.getGridY() + 0.5f) - (enemy.getGridY() + 0.5f);
            float d2 = dx * dx + dy * dy;
            if (d2 < bestDist) {
                bestDist = d2;
                best = p;
            }
        }
        return best;
    }

    private void spawnNearby(Player player) {
        int radius = grid.getChunkSize(); // one chunk in every direction
        List<DungeonGenerator.Room> rooms = grid.getRoomsInArea(
                player.getGridX() - radius,
                player.getGridY() - radius,
                player.getGridX() + radius,
                player.getGridY() + radius
        );
        for (DungeonGenerator.Room room : rooms) {
            if (room.type != DungeonGenerator.RoomType.DANGER) {
                continue;
            }
            String key = roomKey(room);
            if (spawnedRooms.contains(key)) {
                continue;
            }
            spawnedRooms.add(key);
            spawnCatsters(room, player);
        }
    }

    private void spawnCatsters(DungeonGenerator.Room room, Player player) {
        int area = room.width * room.height;
        int count = area > 220 ? 2 : 1;

        SplittableRandom rng = new SplittableRandom(hashRoom(room));
        for (int i = 0; i < count; i++) {
            int spawnX = room.centerX();
            int spawnY = room.centerY();
            boolean found = false;
            for (int attempt = 0; attempt < 40; attempt++) {
                int x = room.x + rng.nextInt(Math.max(1, room.width));
                int y = room.y + rng.nextInt(Math.max(1, room.height));
                if (!grid.isWalkable(x, y)) {
                    continue;
                }
                if (x == player.getGridX() && y == player.getGridY()) {
                    continue;
                }
                spawnX = x;
                spawnY = y;
                found = true;
                break;
            }
            if (!found && !grid.isWalkable(spawnX, spawnY)) {
                // fallback: skip spawn if the room is malformed
                continue;
            }
            Enemy enemy = new Enemy(
                    EntityIds.next(),
                    EnemyType.CATSTER,
                    spawnX,
                    spawnY,
                    room.x,
                    room.y,
                    room.x + room.width - 1,
                    room.y + room.height - 1
            );
            enemies.add(enemy);
            if (entityWorld != null) {
                entityWorld.add(enemy);
            }
        }
    }

    private void chasePlayer(Enemy enemy, Player player, Set<Long> occupied) {
        if (enemy.isMoving()) {
            return;
        }
        int targetX = player.getGridX();
        int targetY = player.getGridY();

        int dx = Integer.compare(targetX, enemy.getGridX());
        int dy = Integer.compare(targetY, enemy.getGridY());

        boolean xFirst = Math.abs(targetX - enemy.getGridX()) >= Math.abs(targetY - enemy.getGridY());
        if (xFirst) {
            if (!tryStep(enemy, dx, 0, occupied, true, player)) {
                tryStep(enemy, 0, dy, occupied, true, player);
            }
        } else {
            if (!tryStep(enemy, 0, dy, occupied, true, player)) {
                tryStep(enemy, dx, 0, occupied, true, player);
            }
        }
    }

    private void wander(Enemy enemy, Set<Long> occupied) {
        if (!enemy.readyToWander()) {
            return;
        }
        int[] dirs = new int[]{-1, 0, 1};
        for (int attempt = 0; attempt < 4; attempt++) {
            int dx = dirs[ambientRng.nextInt(3)];
            int dy = dirs[ambientRng.nextInt(3)];
            if (dx == 0 && dy == 0) {
                continue;
            }
            if (Math.abs(enemy.getGridX() + dx - enemy.getHomeX()) > enemy.getType().wanderRadiusTiles()) {
                continue;
            }
            if (Math.abs(enemy.getGridY() + dy - enemy.getHomeY()) > enemy.getType().wanderRadiusTiles()) {
                continue;
            }
            if (!enemy.isInsideRoomBounds(enemy.getGridX() + dx, enemy.getGridY() + dy)) {
                continue;
            }
            if (tryStep(enemy, dx, dy, occupied, false, null)) {
                enemy.resetWanderCooldown();
                return;
            }
        }
        enemy.resetWanderCooldown();
    }

    private boolean tryStep(Enemy enemy, int dx, int dy, Set<Long> occupied, boolean allowLeaveRoom, Player player) {
        if (dx == 0 && dy == 0) {
            return false;
        }
        int nextX = enemy.getGridX() + dx;
        int nextY = enemy.getGridY() + dy;
        if (!allowLeaveRoom && !enemy.isInsideRoomBounds(nextX, nextY)) {
            return false;
        }
        if (player != null && nextX == player.getGridX() && nextY == player.getGridY()) {
            return false; // do not step into player's tile
        }
        long nextKey = key(nextX, nextY);
        if (occupied.contains(nextKey)) {
            return false;
        }
        int fromX = enemy.getGridX();
        int fromY = enemy.getGridY();
        if (!enemy.moveTo(nextX, nextY, grid)) {
            return false;
        }

        long oldKey = key(enemy.getGridX() - dx, enemy.getGridY() - dy);
        occupied.remove(oldKey);
        occupied.add(nextKey);
        if (entityWorld != null && (fromX != nextX || fromY != nextY)) {
            entityWorld.move(enemy, fromX, fromY, nextX, nextY);
        }
        return true;
    }


    private float angleDelta(float a, float b) {
        float diff = a - b;
        while (diff > Math.PI) diff -= Math.PI * 2f;
        while (diff < -Math.PI) diff += Math.PI * 2f;
        return diff;
    }

    private void attemptAttack(Enemy enemy, Player player, PlayerStats playerStats) {
        float dx = enemy.getGridX() + 0.5f - (player.getGridX() + 0.5f);
        float dy = enemy.getGridY() + 0.5f - (player.getGridY() + 0.5f);
        float dist2 = dx * dx + dy * dy;
        float range = enemy.getType().attackRangeTiles();
        if (dist2 > range * range) {
            return;
        }
        if (!enemy.readyToAttack()) {
            return;
        }
        boolean damaged = playerStats.applyDamage(enemy.getType().damage());
        if (damaged) {
            enemy.triggerAttackCooldown();
        }
    }

    private boolean seesPlayer(Enemy enemy, Player player) {
        float dx = enemy.getGridX() + 0.5f - (player.getGridX() + 0.5f);
        float dy = enemy.getGridY() + 0.5f - (player.getGridY() + 0.5f);
        float dist2 = dx * dx + dy * dy;
        float vision = enemy.getType().visionRangeTiles();
        if (dist2 > vision * vision) {
            return false;
        }
        return hasLineOfSight(enemy.getGridX(), enemy.getGridY(), player.getGridX(), player.getGridY());
    }

    private boolean hasLineOfSight(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int cx = x0;
        int cy = y0;
        while (true) {
            if (!grid.isTransparent(cx, cy)) {
                return false;
            }
            if (cx == x1 && cy == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                cx += sx;
            }
            if (cx == x1 && cy == y1) {
                if (!grid.isTransparent(cx, cy)) {
                    return false;
                }
                break;
            }
            if (e2 < dx) {
                err += dx;
                cy += sy;
            }
        }
        return true;
    }

    private String roomKey(DungeonGenerator.Room room) {
        return room.x + ":" + room.y + ":" + room.width + "x" + room.height;
    }

    private long hashRoom(DungeonGenerator.Room room) {
        long h = worldSeed ^ 0xC0FFEEDEL;
        h ^= (long) room.x * 0x9E3779B97F4A7C15L;
        h ^= (long) room.y * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) room.width * 0x165667B19E3779F9L;
        h ^= (long) room.height * 0x27D4EB2F165667C5L;
        h ^= h >>> 17;
        return h;
    }

    private long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }
}
