package com.droiddungeon.runtime;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-safe buffer of recent authoritative snapshots from the server. Allows interpolation for a
 * target server tick (fractional).
 */
public final class NetworkSnapshotBuffer {
  private static final int DEFAULT_CAPACITY = 32;
  private final Deque<NetworkSnapshot> deque = new ArrayDeque<>(DEFAULT_CAPACITY);

  public synchronized void push(NetworkSnapshot snap) {
    if (deque.size() >= DEFAULT_CAPACITY) {
      deque.removeFirst();
    }
    deque.addLast(snap);
  }

  public synchronized long latestTick() {
    NetworkSnapshot last = deque.peekLast();
    return last == null ? -1L : last.tick();
  }

  /**
   * Interpolates snapshots for a fractional server tick. If exact tick is not available, the method
   * will find surrounding snapshots and linearly interpolate between them.
   */
  public synchronized NetworkSnapshot interpolateForTick(double targetTick) {
    if (deque.isEmpty()) return null;
    if (deque.size() == 1) return deque.peekLast();

    NetworkSnapshot prev = null;
    for (NetworkSnapshot s : deque) {
      if (s.tick() == (long) targetTick) {
        return s;
      }
      if (s.tick() > targetTick) {
        if (prev == null) return s;
        double t = (targetTick - prev.tick()) / (double) (s.tick() - prev.tick());
        float tf = (float) Math.min(1.0, Math.max(0.0, t));
        long interpTick = (long) (prev.tick() + (s.tick() - prev.tick()) * tf);
        long interpLastProc =
            (long)
                Math.round(
                    prev.lastProcessedTick()
                        + (s.lastProcessedTick() - prev.lastProcessedTick()) * tf);
        return new NetworkSnapshot(
            interpTick,
            lerp(prev.playerRenderX(), s.playerRenderX(), tf),
            lerp(prev.playerRenderY(), s.playerRenderY(), tf),
            (int) Math.round(lerp(prev.playerGridX(), s.playerGridX(), tf)),
            (int) Math.round(lerp(prev.playerGridY(), s.playerGridY(), tf)),
            lerp(prev.playerHp(), s.playerHp(), tf),
            interpLastProc);
      }
      prev = s;
    }
    // targetTick is after last snapshot, clamp to last
    return deque.peekLast();
  }

  private static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
  }
}
