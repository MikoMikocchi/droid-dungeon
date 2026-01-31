package com.droiddungeon.input;

/**
 * Snapshot of weapon input per frame. For melee combat, the attack button and world cursor coordinates are sufficient.
 * In network mode, the cursor can be replaced with the gaze direction.
 */
public record WeaponInput(
        boolean attackJustPressed,
        boolean attackHeld,
        float aimWorldX,
        float aimWorldY
) {
    public static WeaponInput idle() {
        return new WeaponInput(false, false, 0f, 0f);
    }
}
