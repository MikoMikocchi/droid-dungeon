package com.droiddungeon.net.dto;

public record GroundItemSnapshotDto(
        int id,
        int x,
        int y,
        String itemId,
        int count,
        int durability
) {}
