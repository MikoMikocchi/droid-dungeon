package com.droiddungeon.config;

/**
 * Centralised gameplay/runtime parameters to avoid hard-coded literals.
 */
public record GameConfig(
        int columns,
        int rows,
        float tileSize,
        float playerSpeedTilesPerSecond,
        float companionSpeedTilesPerSecond,
        int companionDelayTiles,
        float cameraLerp,
        float cameraZoom
) {
    public static GameConfig defaults() {
        return new GameConfig(
                80,
                60,
                48f,
                10f,
                12f,
                3,
                6f,
                1f
        );
    }
}
