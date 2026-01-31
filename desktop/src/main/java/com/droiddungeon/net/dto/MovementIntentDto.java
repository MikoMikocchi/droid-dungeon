package com.droiddungeon.net.dto;

public record MovementIntentDto(
        boolean leftHeld,
        boolean rightHeld,
        boolean upHeld,
        boolean downHeld,
        boolean leftJustPressed,
        boolean rightJustPressed,
        boolean upJustPressed,
        boolean downJustPressed
) {}
