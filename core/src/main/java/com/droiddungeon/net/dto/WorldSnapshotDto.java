package com.droiddungeon.net.dto;

public record WorldSnapshotDto(
        long tick,
        long seed,
        String version,
        PlayerSnapshotDto player,
        PlayerSnapshotDto[] players,
        EnemySnapshotDto[] enemies,
        BlockChangeDto[] blockChanges,
        GroundItemSnapshotDto[] groundItems,
        WeaponStateSnapshotDto[] weaponStates,
        MiningStateSnapshotDto[] miningStates
) {}
