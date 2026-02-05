package com.droiddungeon.server;

import com.droiddungeon.config.GameConfig;
import com.droiddungeon.entity.EntityWorld;
import com.droiddungeon.grid.DungeonGenerator;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.items.ChestStore;
import com.droiddungeon.items.GroundItemStore;
import com.droiddungeon.items.ItemRegistry;
import com.droiddungeon.runtime.GameContextFactory;
import com.droiddungeon.systems.EnemySystem;

public final class WorldInitializer {
  public WorldState initialize(GameConfig config, ItemRegistry itemRegistry, long worldSeed) {
    DungeonGenerator.DungeonLayout layout =
        DungeonGenerator.generateInfinite(config.tileSize(), worldSeed);
    Grid grid = layout.grid();
    int spawnX = layout.spawnX();
    int spawnY = layout.spawnY();

    EntityWorld entityWorld = new EntityWorld();
    GroundItemStore groundStore = new GroundItemStore(entityWorld, itemRegistry);
    ChestStore chestStore = new ChestStore();
    EnemySystem enemySystem = new EnemySystem(grid, worldSeed, entityWorld, groundStore);

    GameContextFactory contextFactory =
        new GameContextFactory(
            config,
            grid,
            spawnX,
            spawnY,
            worldSeed,
            itemRegistry,
            entityWorld,
            enemySystem,
            groundStore,
            chestStore);

    return new WorldState(grid, entityWorld, groundStore, chestStore, enemySystem, contextFactory);
  }

  public record WorldState(
      Grid grid,
      EntityWorld entityWorld,
      GroundItemStore groundStore,
      ChestStore chestStore,
      EnemySystem enemySystem,
      GameContextFactory contextFactory) {}
}
