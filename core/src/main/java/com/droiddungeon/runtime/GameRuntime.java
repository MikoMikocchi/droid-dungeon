package com.droiddungeon.runtime;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.config.GameConfig;
import com.droiddungeon.control.GameRenderCoordinator;
import com.droiddungeon.control.GameUpdater;
import com.droiddungeon.control.MapController;
import com.droiddungeon.debug.DebugTextBuilder;
import com.droiddungeon.entity.EntityWorld;
import com.droiddungeon.grid.DungeonGenerator;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.input.GameInputController;
import com.droiddungeon.input.HeldMovementController;
import com.droiddungeon.input.InputBindings;
import com.droiddungeon.input.InputFrame;
import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.inventory.ItemStack;
import com.droiddungeon.items.ItemDefinition;
import com.droiddungeon.items.ItemRegistry;
import com.droiddungeon.render.RenderAssets;
import com.droiddungeon.systems.CameraController;
import com.droiddungeon.systems.EnemySystem;
import com.droiddungeon.systems.InventorySystem;
import com.droiddungeon.systems.WeaponSystem;
import com.droiddungeon.ui.MapOverlay;

/**
 * Thin orchestration shell: wires input → update → render.
 */
public final class GameRuntime {
    private final GameConfig config;
    private final InputBindings inputBindings;
    private final DebugTextBuilder debugTextBuilder = new DebugTextBuilder();

    private Viewport worldViewport;
    private Viewport uiViewport;
    private OrthographicCamera worldCamera;
    private OrthographicCamera uiCamera;
    private HeldMovementController movementController;
    private GameInputController inputController;
    private MapController mapController;
    private CameraController cameraController;
    private GameUpdater updater;
    private GameRenderCoordinator renderer;

    private long worldSeed;
    private int spawnX;
    private int spawnY;
    private MapOverlay mapOverlay;

    private Inventory inventory;
    private ItemRegistry itemRegistry;
    private InventorySystem inventorySystem;
    private EntityWorld entityWorld;
    private EnemySystem enemySystem;

    private WeaponSystem weaponSystem;
    private WeaponSystem.WeaponState weaponState;
    private GameContext context;
    private GameContextFactory contextFactory;
    private RunStateManager runStateManager;

    public GameRuntime(GameConfig config) {
        this.config = config;
        this.inputBindings = InputBindings.defaults();
    }

    public void create() {
        worldCamera = new OrthographicCamera();
        worldViewport = new ScreenViewport(worldCamera);
        uiCamera = new OrthographicCamera();
        uiViewport = new ScreenViewport(uiCamera);
        cameraController = new CameraController(worldCamera, worldViewport, config.cameraLerp(), config.cameraZoom());

        worldSeed = com.badlogic.gdx.utils.TimeUtils.millis();
        DungeonGenerator.DungeonLayout layout = DungeonGenerator.generateInfinite(config.tileSize(), worldSeed);
        Grid grid = layout.grid();
        spawnX = layout.spawnX();
        spawnY = layout.spawnY();

        mapOverlay = new MapOverlay(RenderAssets.font(14));
        movementController = new HeldMovementController();
        renderer = new GameRenderCoordinator();
        inputController = new GameInputController(inputBindings, renderer.hudRenderer());
        mapController = new MapController();
        updater = new GameUpdater(config, cameraController, movementController);

        entityWorld = new EntityWorld();
        inventory = new Inventory();
        itemRegistry = ItemRegistry.load("items.txt");
        inventorySystem = new InventorySystem(inventory, itemRegistry, grid, entityWorld);
        enemySystem = new EnemySystem(grid, worldSeed, entityWorld);
        contextFactory = new GameContextFactory(config, grid, spawnX, spawnY, worldSeed, inventory, inventorySystem, itemRegistry, entityWorld, enemySystem);
        context = contextFactory.createContext();
        weaponSystem = context.weaponSystem();
        weaponState = weaponSystem.getState();
        mapOverlay.clearExplored();
        mapOverlay.revealAround(context.player(), 10);
        seedDemoItems();
        runStateManager = new RunStateManager();
    }

    public void resize(int width, int height) {
        uiViewport.update(width, height, true);
        cameraController.resize(width, height);
        renderer.resize(width, height);
    }

    public void render() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float delta = Gdx.graphics.getDeltaTime();
        context.playerStats().update(delta);
        if (context.playerStats().isDead() && !runStateManager.isDead()) {
            runStateManager.handlePlayerDeath(context, mapOverlay);
        }

        InputFrame input = inputController.collect(uiViewport, inventorySystem);
        boolean mapOpen = mapController.update(input, mapOverlay, renderer.minimapBounds(), uiViewport, context);

        Vector2 mouseWorld = worldViewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));

        GameUpdateResult updateResult = updater.update(
                delta,
                runStateManager.isDead(),
                input,
                context,
                context.grid().getTileSize(),
                mouseWorld,
                mapOpen
        );
        weaponState = updateResult.weaponState();

        boolean restartHovered = renderer.render(
                worldViewport,
                uiViewport,
                context,
                updateResult,
                mapOverlay,
                debugTextBuilder,
                input,
                delta,
                runStateManager.isDead(),
                mapOpen
        );

        if (runStateManager.isDead()) {
            if (Gdx.input.justTouched() && restartHovered) {
                restartRun();
            }
            if (input.restartRequested()) {
                restartRun();
            }
        }
    }

    public void dispose() {
        renderer.dispose();
        mapOverlay.dispose();
        RenderAssets.dispose();
        itemRegistry.dispose();
    }

    private void restartRun() {
        runStateManager.reset(mapOverlay);
        context = contextFactory.createContext();
        weaponSystem = context.weaponSystem();
        weaponState = weaponSystem.getState();
        inventorySystem.forceCloseInventory();
        mapOverlay.close();
        mapOverlay.clearExplored();
        mapOverlay.revealAround(context.player(), 10);
    }

    private void seedDemoItems() {
        Inventory inv = context.inventory();
        inv.add(new ItemStack("test_chip", 8), itemRegistry);
        ItemDefinition rapierDef = itemRegistry.get("steel_rapier");
        if (rapierDef != null) {
            inv.add(new ItemStack("steel_rapier", 1, rapierDef.maxDurability()), itemRegistry);
        }
        ItemDefinition pickaxeDef = itemRegistry.get("steel_pickaxe");
        if (pickaxeDef != null) {
            inv.add(new ItemStack("steel_pickaxe", 1, pickaxeDef.maxDurability()), itemRegistry);
        }
        context.inventorySystem().addGroundStack(context.player().getGridX() + 1, context.player().getGridY(), new ItemStack("test_chip", 5));
    }
}
