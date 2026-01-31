package com.droiddungeon.net;

import com.droiddungeon.input.MovementIntent;
import com.droiddungeon.input.WeaponInput;
import com.droiddungeon.runtime.NetworkSnapshotBuffer;
import com.droiddungeon.net.dto.WorldSnapshotDto;

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

    /** Latest snapshot if available (optional). */
    default WorldSnapshotDto pollSnapshot() { return null; }

    /** Player id assigned by server if known. */
    default String playerId() { return null; }
}
