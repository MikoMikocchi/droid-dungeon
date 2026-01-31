package com.droiddungeon.net.dto;

public record GroundItemSnapshotDto(
        int x,
        int y,
        String itemId,
        int count,
        int durability
) {}
