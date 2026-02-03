package com.droiddungeon.server;

import com.droiddungeon.entity.EntityWorld;
import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.inventory.ItemStack;
import com.droiddungeon.runtime.GameContextFactory;
import java.util.HashMap;
import java.util.Map;

public final class PlayerSessionStore {
  private final GameContextFactory contextFactory;
  private final EntityWorld entityWorld;
  private final Map<String, GameContextFactory.PlayerSession> sessions = new HashMap<>();
  private final Map<String, PlayerSave> savedPlayers = new HashMap<>();

  public PlayerSessionStore(GameContextFactory contextFactory, EntityWorld entityWorld) {
    this.contextFactory = contextFactory;
    this.entityWorld = entityWorld;
  }

  /**
   * Register a player session on server: creates per-player state and inserts player entity.
   *
   * @return last processed tick if a saved state exists, otherwise -1
   */
  public long registerPlayer(String playerId) {
    if (sessions.containsKey(playerId)) {
      return savedPlayers.getOrDefault(playerId, PlayerSave.EMPTY).lastProcessedTick;
    }
    var session = contextFactory.createPlayerSession(playerId);
    var saved = savedPlayers.remove(playerId);
    applySavedState(session, saved);
    sessions.put(playerId, session);
    entityWorld.add(session.player);
    entityWorld.add(session.companion);
    return saved != null ? saved.lastProcessedTick : -1L;
  }

  /** Unregister player and clean up entities */
  public void unregisterPlayer(String playerId) {
    var session = sessions.remove(playerId);
    if (session == null) return;
    entityWorld.remove(session.player);
    entityWorld.remove(session.companion);
  }

  public GameContextFactory.PlayerSession getSession(String playerId) {
    return sessions.get(playerId);
  }

  public Iterable<GameContextFactory.PlayerSession> sessions() {
    return sessions.values();
  }

  /** Persist current session state for reconnects (in-memory only). */
  public void savePlayerState(String playerId, long lastProcessedTick) {
    var session = sessions.get(playerId);
    if (session == null) return;
    savedPlayers.put(playerId, PlayerSave.snapshot(session, lastProcessedTick));
  }

  private static void applySavedState(GameContextFactory.PlayerSession session, PlayerSave saved) {
    if (saved == null) return;
    session.player.setServerPosition(saved.renderX, saved.renderY, saved.gridX, saved.gridY);
    session.stats.setHealth(saved.health);
    session.companion.resetState(
        saved.companionGridX,
        saved.companionGridY,
        saved.companionRenderX,
        saved.companionRenderY,
        session.player.getGridX(),
        session.player.getGridY());

    Inventory inv = session.inventory;
    for (int i = 0; i < inv.size(); i++) {
      inv.set(i, null);
    }
    if (saved.inventory != null) {
      for (int i = 0; i < Math.min(inv.size(), saved.inventory.length); i++) {
        inv.set(i, saved.inventory[i]);
      }
    }
  }

  private record PlayerSave(
      float renderX,
      float renderY,
      int gridX,
      int gridY,
      float companionRenderX,
      float companionRenderY,
      int companionGridX,
      int companionGridY,
      float health,
      ItemStack[] inventory,
      long lastProcessedTick) {
    private static final PlayerSave EMPTY =
        new PlayerSave(0f, 0f, 0, 0, 0f, 0f, 0, 0, 0f, new ItemStack[0], -1L);

    private static PlayerSave snapshot(
        GameContextFactory.PlayerSession session, long lastProcessedTick) {
      ItemStack[] items = new ItemStack[session.inventory.size()];
      for (int i = 0; i < items.length; i++) {
        ItemStack stack = session.inventory.get(i);
        if (stack != null) {
          items[i] = new ItemStack(stack.itemId(), stack.count(), stack.durability());
        }
      }
      return new PlayerSave(
          session.player.getRenderX(),
          session.player.getRenderY(),
          session.player.getGridX(),
          session.player.getGridY(),
          session.companion.getRenderX(),
          session.companion.getRenderY(),
          session.companion.getGridX(),
          session.companion.getGridY(),
          session.stats.getHealth(),
          items,
          lastProcessedTick);
    }
  }
}
