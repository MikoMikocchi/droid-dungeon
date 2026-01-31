package com.droiddungeon.net.dto;

public record WeaponInputDto(
        boolean attackJustPressed,
        boolean attackHeld,
        float aimWorldX,
        float aimWorldY
) {}
