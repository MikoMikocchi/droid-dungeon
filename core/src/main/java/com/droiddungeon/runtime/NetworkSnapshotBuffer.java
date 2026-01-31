package com.droiddungeon.runtime;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Maintains the two most recent snapshots for interpolation.
 */
public final class NetworkSnapshotBuffer {
    private final Deque<NetworkSnapshot> deque = new ArrayDeque<>(2);

    public void push(NetworkSnapshot snap) {
        if (deque.size() == 2) {
            deque.removeFirst();
        }
        deque.addLast(snap);
    }

    /**
     * Interpolates between last two snapshots based on alpha in [0,1].
     * If only one snapshot exists, returns it.
     */
    public NetworkSnapshot interpolate(float alpha) {
        if (deque.isEmpty()) return null;
        if (deque.size() == 1 || alpha <= 0f) return deque.peekLast();

        var a = deque.getFirst();
        var b = deque.getLast();

        float t = Math.min(1f, Math.max(0f, alpha));
        return new NetworkSnapshot(
                (long) (a.tick() + (b.tick() - a.tick()) * t),
                lerp(a.playerRenderX(), b.playerRenderX(), t),
                lerp(a.playerRenderY(), b.playerRenderY(), t),
                (int) Math.round(lerp(a.playerGridX(), b.playerGridX(), t)),
                (int) Math.round(lerp(a.playerGridY(), b.playerGridY(), t)),
                lerp(a.playerHp(), b.playerHp(), t)
        );
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
