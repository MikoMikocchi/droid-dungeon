package com.droiddungeon.runtime;

/**
 * Authoritative snapshot from server to drive client render in network mode.
 */
public record NetworkSnapshot(
        long tick,
        float playerRenderX,
        float playerRenderY,
        int playerGridX,
        int playerGridY,
        float playerHp
) {}
