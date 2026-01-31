package com.droiddungeon.server;

import com.droiddungeon.config.GameConfig;
import com.droiddungeon.control.GameUpdater;
import com.droiddungeon.entity.EntityWorld;
import com.droiddungeon.grid.DungeonGenerator;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.input.HeldMovementController;
import com.droiddungeon.input.InputFrame;
import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.items.ItemRegistry;
import com.droiddungeon.runtime.GameContext;
import com.droiddungeon.runtime.GameContextFactory;
import com.droiddungeon.runtime.GameUpdateResult;
import com.droiddungeon.systems.CameraController;
import com.droiddungeon.systems.EnemySystem;
import com.droiddungeon.systems.InventorySystem;

/**
 * Minimal headless loop for server tick: does not rely on Gdx.graphics/Gdx.input.
 * Adapts input DTOs (InputFrame) to GameUpdater and provides a state snapshot.
 */
public final class ServerGameLoop {
    private final GameConfig config;
    private final long worldSeed;
    private final GameUpdater updater;
    private final GameContextFactory contextFactory;
    private final InventorySystem inventorySystem;
    private final EnemySystem enemySystem;
    private final HeldMovementController movementController;

    private GameContext context;

    public ServerGameLoop(GameConfig config, ItemRegistry itemRegistry, long worldSeed) {
        this.config = config;
        this.worldSeed = worldSeed;
        this.movementController = new HeldMovementController();

        DungeonGenerator.DungeonLayout layout = DungeonGenerator.generateInfinite(config.tileSize(), worldSeed);
        Grid grid = layout.grid();
        int spawnX = layout.spawnX();
        int spawnY = layout.spawnY();

        EntityWorld entityWorld = new EntityWorld();
        Inventory inventory = new Inventory();
        this.inventorySystem = new InventorySystem(inventory, itemRegistry, grid, entityWorld);
        this.enemySystem = new EnemySystem(grid, worldSeed, entityWorld, inventorySystem);
        this.contextFactory = new GameContextFactory(
                config,
                grid,
                spawnX,
                spawnY,
                worldSeed,
                inventory,
                inventorySystem,
                itemRegistry,
                entityWorld,
                enemySystem
        );

        // camera and viewport are not needed for the server, passing null
        CameraController cameraController = null;
        this.updater = new GameUpdater(config, cameraController, movementController);
        this.context = contextFactory.createContext();
    }

    public long worldSeed() {
        return worldSeed;
    }

    public GameContext context() {
        return context;
    }

    /**
     * Perform one simulation tick.
     * @param input input frame (MovementIntent/WeaponInput and action flags)
     * @param deltaSeconds time step
     * @return final update snapshot (originally used by renderer, but contains weaponState)
     */
    public GameUpdateResult tick(InputFrame input, float deltaSeconds) {
        return updater.update(
                deltaSeconds,
                false, // authoritative server controls death/restart itself
                input,
                context,
                context.grid().getTileSize(),
                false // mapOpen
        );
    }

    /**
     * Restart the run with the same seed. Can be used after death/new session.
     */
    public void resetRun() {
        this.context = contextFactory.createContext();
    }
}
