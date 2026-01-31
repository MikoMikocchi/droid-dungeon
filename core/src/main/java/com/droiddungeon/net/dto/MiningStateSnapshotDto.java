package com.droiddungeon.net.dto;

public record MiningStateSnapshotDto(
        String playerId,
        int targetX,
        int targetY,
        float progress
) {}
