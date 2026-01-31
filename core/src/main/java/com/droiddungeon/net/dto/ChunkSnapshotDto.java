package com.droiddungeon.net.dto;

public record ChunkSnapshotDto(
        int chunkX,
        int chunkY,
        BlockChangeDto[] blocks
) {}
