package com.droiddungeon.net.dto;

public record EnemySnapshotDto(
        int id,
        String enemyType,
        float x,
        float y,
        int gridX,
        int gridY,
        float hp
) {}
