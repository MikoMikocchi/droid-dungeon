package com.droiddungeon.input;

/**
 * Snapshot of movement intents per frame. Holds both key holds and 'just pressed' events so that
 * the previous logic for selecting the preferred direction can be reproduced.
 */
public record MovementIntent(
    boolean leftHeld,
    boolean rightHeld,
    boolean upHeld,
    boolean downHeld,
    boolean leftJustPressed,
    boolean rightJustPressed,
    boolean upJustPressed,
    boolean downJustPressed) {}
