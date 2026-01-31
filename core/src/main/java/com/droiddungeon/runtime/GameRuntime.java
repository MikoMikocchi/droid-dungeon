package com.droiddungeon.runtime;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
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
import com.droiddungeon.items.TextureLoader;
import com.droiddungeon.render.ClientAssets;
import com.droiddungeon.systems.CameraController;
import com.droiddungeon.systems.EnemySystem;
import com.droiddungeon.systems.InventorySystem;
import com.droiddungeon.systems.WeaponSystem;
import com.droiddungeon.ui.MapOverlay;
import com.droiddungeon.runtime.NetworkSnapshotBuffer;
import com.droiddungeon.runtime.NetworkSnapshot;
import com.droiddungeon.net.NetworkClientAdapter;
import com.droiddungeon.net.dto.BlockChangeDto;
import com.droiddungeon.net.dto.ChunkSnapshotDto;
import com.droiddungeon.net.dto.EnemySnapshotDto;
import com.droiddungeon.net.dto.GroundItemSnapshotDto;
import com.droiddungeon.net.dto.WeaponStateSnapshotDto;
import com.droiddungeon.net.dto.WorldSnapshotDto;
import com.droiddungeon.grid.BlockMaterial;

/**
 * Thin orchestration shell: wires input → update → render.
 */
public final class GameRuntime {
    private final GameConfig config;
    private final InputBindings inputBindings;
    private final DebugTextBuilder debugTextBuilder = new DebugTextBuilder();
    private final com.droiddungeon.items.TextureLoader textureLoader;
    private final ClientAssets clientAssets;
    private final boolean networkMode;
    private final NetworkClientAdapter networkClient;
    private final NetworkSnapshotBuffer snapshotBuffer;

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
    private boolean worldSeedForced;
    private int spawnX;
    private int spawnY;
    private String playerId = java.util.UUID.randomUUID().toString();
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
        this(config, null, null, null, new NetworkSnapshotBuffer(), false);
    }

    public GameRuntime(GameConfig config, com.droiddungeon.items.TextureLoader textureLoader, ClientAssets clientAssets, NetworkClientAdapter networkClient, NetworkSnapshotBuffer buffer, boolean networkMode) {
        this.config = config;
        this.inputBindings = InputBindings.defaults();
        this.textureLoader = textureLoader;
        this.clientAssets = clientAssets;
        this.networkMode = networkMode;
        this.networkClient = networkClient;
        this.snapshotBuffer = buffer != null ? buffer : new NetworkSnapshotBuffer();
    }

    /**
     * Set the seed in advance (for example, from the server) before calling {@link #create()}.
     */
    public void setWorldSeed(long worldSeed) {
        this.worldSeed = worldSeed;
        this.worldSeedForced = true;
    }

    public void create() {
        worldCamera = new OrthographicCamera();
        worldViewport = new ScreenViewport(worldCamera);
        uiCamera = new OrthographicCamera();
        uiViewport = new ScreenViewport(uiCamera);
        cameraController = new CameraController(worldCamera, worldViewport, config.cameraLerp(), config.cameraZoom());

        if (!worldSeedForced) {
            String seedProp = System.getProperty("network.seed");
            if (networkMode && seedProp != null) {
                try {
                    worldSeed = Long.parseLong(seedProp);
                    worldSeedForced = true;
                } catch (NumberFormatException ignored) {
                    worldSeed = com.badlogic.gdx.utils.TimeUtils.millis();
                }
            } else {
                worldSeed = com.badlogic.gdx.utils.TimeUtils.millis();
            }
        }
        DungeonGenerator.DungeonLayout layout = DungeonGenerator.generateInfinite(config.tileSize(), worldSeed);
        Grid grid = layout.grid();
        spawnX = layout.spawnX();
        spawnY = layout.spawnY();

        if (clientAssets == null) {
            throw new IllegalStateException("ClientAssets must be provided for GameRuntime");
        }
        mapOverlay = new MapOverlay(clientAssets.font(14));
        movementController = new HeldMovementController();
        renderer = new GameRenderCoordinator(clientAssets);
        renderer.initLighting(config.tileSize(), worldSeed);
        inputController = new GameInputController(inputBindings, renderer.hudRenderer());
        mapController = new MapController();
        updater = new GameUpdater(config, cameraController, movementController);

        entityWorld = new EntityWorld();
        inventory = new Inventory();
        if (textureLoader != null) {
            String itemsPath = Gdx.files.internal("items.txt").file().getAbsolutePath();
            itemRegistry = ItemRegistry.loadWithLoader(itemsPath, textureLoader);
        } else {
            itemRegistry = ItemRegistry.loadDataOnly(java.nio.file.Path.of("items.txt"));
        }
        inventorySystem = new InventorySystem(inventory, itemRegistry, grid, entityWorld);
        enemySystem = new EnemySystem(grid, worldSeed, entityWorld, inventorySystem);
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

        InputFrame input = inputController.collect(uiViewport, worldViewport, inventorySystem);
        boolean mapOpen = mapController.update(input, mapOverlay, renderer.minimapBounds(), uiViewport, context);

        if (networkMode) {
            if (networkClient != null) {
                networkClient.connectIfNeeded();
                if (networkClient.isConnected()) {
                    if (networkClient.playerId() != null) {
                        playerId = networkClient.playerId();
                    }
                    WorldSnapshotDto latest = networkClient.pollSnapshot();
                    if (latest != null) {
                        applySnapshot(latest);
                    }

                    networkClient.sendInput(
                            input.movementIntent(),
                            input.weaponInput(),
                            input.dropRequested(),
                            input.pickUpRequested(),
                            input.mineRequested(),
                            playerId
                    );
                    NetworkSnapshot snap = snapshotBuffer.interpolate(delta * 60f); // rough alpha by FPS
                    if (snap != null) {
                        context.player().setServerPosition(
                                snap.playerRenderX(),
                                snap.playerRenderY(),
                                snap.playerGridX(),
                                snap.playerGridY()
                        );
                        context.playerStats().setHealth(snap.playerHp());
                        context.companionSystem().updateFollowerTrail(context.player().getGridX(), context.player().getGridY());
                        context.companionSystem().updateRender(delta);
                    }
                    cameraController.update(context.grid(), context.player(), delta);
                    float gridOriginX = cameraController.getGridOriginX();
                    float gridOriginY = cameraController.getGridOriginY();
                    GameUpdateResult netResult = new GameUpdateResult(gridOriginX, gridOriginY, weaponState);
                    renderer.render(worldViewport, uiViewport, context, netResult, mapOverlay, debugTextBuilder, input, delta, runStateManager.isDead(), mapOpen);
                    return;
                }
            }
        }

        GameUpdateResult updateResult = updater.update(
                delta,
                runStateManager.isDead(),
                input,
                context,
                context.grid().getTileSize(),
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
        if (clientAssets != null) {
            clientAssets.close();
        }
        itemRegistry.dispose();
    }

    /**
     * Rebuilds the entire runtime world from a server-provided seed so карта совпадает с авторитетной.
     */
    private void rebuildWorldFromSeed(long newSeed) {
        worldSeed = newSeed;
        worldSeedForced = true;

        DungeonGenerator.DungeonLayout layout = DungeonGenerator.generateInfinite(config.tileSize(), worldSeed);
        Grid grid = layout.grid();
        spawnX = layout.spawnX();
        spawnY = layout.spawnY();

        inventory = new Inventory();
        inventorySystem = new InventorySystem(inventory, itemRegistry, grid, entityWorld);
        enemySystem = new EnemySystem(grid, worldSeed, entityWorld, inventorySystem);

        contextFactory = new GameContextFactory(config, grid, spawnX, spawnY, worldSeed, inventory, inventorySystem, itemRegistry, entityWorld, enemySystem);
        context = contextFactory.createContext();
        weaponSystem = context.weaponSystem();
        weaponState = weaponSystem.getState();

        mapOverlay.clearExplored();
        mapOverlay.revealAround(context.player(), 10);
        seedDemoItems();
        runStateManager.reset(mapOverlay);
    }

    private void applySnapshot(WorldSnapshotDto snap) {
        if (snap == null) {
            return;
        }

        if (snap.seed() != 0L && snap.seed() != worldSeed) {
            rebuildWorldFromSeed(snap.seed());
        }

        if (snap.full()) {
            applyChunkSnapshots(snap.chunks());
        }
        applyBlockChanges(snap.blockChanges());
        applyGroundItems(snap.groundItems(), snap.groundItemRemovals(), snap.full());
        applyEnemies(snap.enemies(), snap.enemyRemovals(), snap.full());

        String myId = playerId;
        if (snap.players() != null) {
            for (var p : snap.players()) {
                if (myId != null && myId.equals(p.playerId())) {
                    context.player().setServerPosition(p.x(), p.y(), p.gridX(), p.gridY());
                    context.playerStats().setHealth(p.hp());
                }
            }
        } else if (snap.player() != null) {
            context.player().setServerPosition(
                    snap.player().x(),
                    snap.player().y(),
                    snap.player().gridX(),
                    snap.player().gridY()
            );
            context.playerStats().setHealth(snap.player().hp());
        }

        if (snap.weaponStates() != null && weaponState != null && myId != null) {
            for (WeaponStateSnapshotDto ws : snap.weaponStates()) {
                if (myId.equals(ws.playerId())) {
                    weaponState = new WeaponSystem.WeaponState(
                            weaponState.active(),
                            ws.swinging(),
                            ws.aimAngleRad(),
                            weaponState.arcRad(),
                            weaponState.reachTiles(),
                            weaponState.innerHoleTiles(),
                            ws.swingProgress(),
                            weaponState.cooldownRatio(),
                            weaponState.damage(),
                            weaponState.swingIndex()
                    );
                }
            }
        }
    }

    private void applyBlockChanges(BlockChangeDto[] changes) {
        if (changes == null || changes.length == 0) {
            return;
        }
        Grid grid = context.grid();
        for (BlockChangeDto bc : changes) {
            String id = bc.materialId();
            if (id == null || id.isEmpty()) {
                grid.setBlock(bc.x(), bc.y(), null);
                continue;
            }
            try {
                BlockMaterial mat = BlockMaterial.valueOf(id);
                grid.setBlock(bc.x(), bc.y(), mat);
                float desiredHp = bc.blockHp();
                float maxHp = mat.maxHealth();
                float damage = Math.max(0f, maxHp - desiredHp);
                if (damage > 0f) {
                    grid.damageBlock(bc.x(), bc.y(), damage);
                }
            } catch (IllegalArgumentException ignored) {
                // unknown material id
            }
        }
    }

    private void applyChunkSnapshots(ChunkSnapshotDto[] chunks) {
        if (chunks == null) {
            return;
        }
        for (ChunkSnapshotDto chunk : chunks) {
            applyBlockChanges(chunk.blocks());
        }
    }

    private void applyGroundItems(GroundItemSnapshotDto[] items, int[] removals, boolean full) {
        if (full) {
            inventorySystem.clearGroundItems();
        }
        if (removals != null) {
            for (int id : removals) {
                inventorySystem.removeGroundItem(id);
            }
        }
        if (items != null) {
            for (GroundItemSnapshotDto gi : items) {
                ItemStack stack = new ItemStack(gi.itemId(), gi.count(), gi.durability());
                inventorySystem.upsertGroundItem(gi.id(), gi.x(), gi.y(), stack);
            }
        }
    }

    private void applyEnemies(EnemySnapshotDto[] enemies, int[] removals, boolean full) {
        enemySystem.applySnapshot(enemies, removals, full);
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
    private void connectNetworkClient() {
        // network client should be injected by desktop layer
    }
}
