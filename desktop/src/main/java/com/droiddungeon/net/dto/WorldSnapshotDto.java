package com.droiddungeon.net.dto;

public record WorldSnapshotDto(
        long tick,
        PlayerSnapshotDto player,
        PlayerSnapshotDto[] players
) {}
