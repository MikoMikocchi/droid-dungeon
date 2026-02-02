package com.droiddungeon.net.dto;

public record WelcomeDto(String playerId, PlayerSnapshotDto player, PlayerSnapshotDto[] players) {}
