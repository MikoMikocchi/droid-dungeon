package com.droiddungeon.net.dto;

public record PlayerSnapshotDto(
    String playerId, float x, float y, int gridX, int gridY, float hp, long lastProcessedTick) {}
