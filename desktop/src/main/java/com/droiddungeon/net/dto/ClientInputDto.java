package com.droiddungeon.net.dto;

public record ClientInputDto(
        long tick,
        String playerId,
        MovementIntentDto movement,
        WeaponInputDto weapon,
        boolean drop,
        boolean pickUp,
        boolean mine
) {}
