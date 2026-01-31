package com.droiddungeon.net.dto;

public record WorldSnapshotDto(
        long tick,
        long seed,
        String version,
        boolean full,
        ChunkSnapshotDto[] chunks,
        PlayerSnapshotDto player,
        PlayerSnapshotDto[] players,
        EnemySnapshotDto[] enemies,
        int[] enemyRemovals,
        BlockChangeDto[] blockChanges,
        GroundItemSnapshotDto[] groundItems,
        int[] groundItemRemovals,
        WeaponStateSnapshotDto[] weaponStates,
        MiningStateSnapshotDto[] miningStates
) {}
