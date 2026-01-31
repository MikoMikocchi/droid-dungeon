package com.droiddungeon.net.dto;

public record WeaponStateSnapshotDto(
        String playerId,
        boolean swinging,
        float swingProgress,
        float aimAngleRad
) {}
