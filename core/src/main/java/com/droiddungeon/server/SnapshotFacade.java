package com.droiddungeon.server;

import com.droiddungeon.items.GroundItem;
import com.droiddungeon.items.GroundItemStore;
import com.droiddungeon.net.dto.PlayerSnapshotDto;
import com.droiddungeon.systems.MiningSystem;
import java.util.List;

public final class SnapshotFacade {
  private final PlayerSessionStore sessionStore;
  private final GroundItemStore groundStore;

  public SnapshotFacade(PlayerSessionStore sessionStore, GroundItemStore groundStore) {
    this.sessionStore = sessionStore;
    this.groundStore = groundStore;
  }

  public PlayerSnapshotDto playerSnapshotFor(String playerId, long lastProcessedTick) {
    var session = sessionStore.getSession(playerId);
    if (session == null) return null;
    return new PlayerSnapshotDto(
        playerId,
        session.player.getRenderX(),
        session.player.getRenderY(),
        session.player.getGridX(),
        session.player.getGridY(),
        session.stats.getHealth(),
        lastProcessedTick);
  }

  public List<GroundItem> getGroundItems() {
    return groundStore.getGroundItems();
  }

  public MiningSystem.MiningTarget getPlayerMiningTarget(String playerId) {
    var session = sessionStore.getSession(playerId);
    if (session == null) return null;
    return session.mining.getTarget();
  }
}
