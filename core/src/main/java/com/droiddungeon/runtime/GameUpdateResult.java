package com.droiddungeon.runtime;

import com.droiddungeon.systems.WeaponSystem;

/**
 * Output of a single update step used by renderers.
 */
public record GameUpdateResult(
        float gridOriginX,
        float gridOriginY,
        WeaponSystem.WeaponState weaponState,
        int pendingInputsCount,
        long lastProcessedTick
) {}
