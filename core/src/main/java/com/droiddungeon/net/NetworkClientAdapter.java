package com.droiddungeon.net;

import com.droiddungeon.input.MovementIntent;
import com.droiddungeon.input.WeaponInput;
import com.droiddungeon.runtime.NetworkSnapshotBuffer;

/**
 * Client-side network transport abstraction used by GameRuntime in network mode.
 */
public interface NetworkClientAdapter {
    /** Ensure the connection is established (idempotent). */
    void connectIfNeeded();

    boolean isConnected();

    void sendInput(MovementIntent movement, WeaponInput weapon, boolean drop, boolean pickUp, boolean mine, String playerId);

    NetworkSnapshotBuffer buffer();

    /** Called when disposing the game. */
    default void close() {}
}
