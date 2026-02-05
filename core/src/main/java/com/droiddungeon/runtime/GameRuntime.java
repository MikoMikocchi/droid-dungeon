package com.droiddungeon.runtime;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.config.GameConfig;
import com.droiddungeon.control.GameRenderCoordinator;
import com.droiddungeon.control.GameUpdater;
import com.droiddungeon.control.MapController;
import com.droiddungeon.debug.DebugTextBuilder;
import com.droiddungeon.entity.EntityIds;
import com.droiddungeon.entity.EntityWorld;
import com.droiddungeon.grid.BlockMaterial;
import com.droiddungeon.grid.DungeonGenerator;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.input.GameInputController;
import com.droiddungeon.input.HeldMovementController;
import com.droiddungeon.input.InputBindings;
import com.droiddungeon.input.InputFrame;
import com.droiddungeon.input.MovementIntent;
import com.droiddungeon.input.WeaponInput;
import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.inventory.ItemStack;
import com.droiddungeon.items.ChestStore;
import com.droiddungeon.items.GroundItemStore;
import com.droiddungeon.items.ItemDefinition;
import com.droiddungeon.items.ItemRegistry;
import com.droiddungeon.items.TextureLoader;
import com.droiddungeon.net.NetworkClientAdapter;
import com.droiddungeon.net.dto.BlockChangeDto;
import com.droiddungeon.net.dto.ChunkSnapshotDto;
import com.droiddungeon.net.dto.EnemySnapshotDto;
import com.droiddungeon.net.dto.GroundItemSnapshotDto;
import com.droiddungeon.net.dto.WeaponStateSnapshotDto;
import com.droiddungeon.net.dto.WorldSnapshotDto;
import com.droiddungeon.render.ClientAssets;
import com.droiddungeon.save.SaveGame;
import com.droiddungeon.systems.CameraController;
import com.droiddungeon.systems.EnemySystem;
import com.droiddungeon.systems.InventorySystem;
import com.droiddungeon.systems.WeaponSystem;
import com.droiddungeon.ui.MapOverlay;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/** Thin orchestration shell: wires input → update → render. */
public final class GameRuntime {
  private final GameConfig config;
  private final InputBindings inputBindings;
  private final DebugTextBuilder debugTextBuilder = new DebugTextBuilder();
  private final TextureLoader textureLoader;
  private final ClientAssets clientAssets;
  private final boolean networkMode;
  private final NetworkClientAdapter networkClient;
  private final NetworkSnapshotBuffer snapshotBuffer;
  private SaveGame pendingSave;

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
  private String playerId = UUID.randomUUID().toString();
  private MapOverlay mapOverlay;

  // Network prediction state
  private long clientTickCounter = 0L;
  private final Deque<SentInput> pendingInputs = new ArrayDeque<>();

  private static final record SentInput(
      long tick,
      MovementIntent movement,
      WeaponInput weapon,
      boolean drop,
      boolean pickUp,
      boolean mine) {}

  private Inventory inventory;
  private ItemRegistry itemRegistry;
  private InventorySystem inventorySystem;
  private GroundItemStore groundStore;
  private ChestStore chestStore;
  private EntityWorld entityWorld;
  private EnemySystem enemySystem;

  private WeaponSystem weaponSystem;
  private WeaponSystem.WeaponState weaponState;
  private GameContext context;
  private GameContextFactory contextFactory;
  private RunStateManager runStateManager;

  // last acked server tick for this client (from server PlayerSnapshot.lastProcessedTick)
  private long lastProcessedTickAck = -1L;

  public GameRuntime(GameConfig config) {
    this(config, null, null, null, new NetworkSnapshotBuffer(), false);
  }

  public GameRuntime(
      GameConfig config,
      TextureLoader textureLoader,
      ClientAssets clientAssets,
      NetworkClientAdapter networkClient,
      NetworkSnapshotBuffer buffer,
      boolean networkMode) {
    this.config = config;
    this.inputBindings = InputBindings.defaults();
    this.textureLoader = textureLoader;
    this.clientAssets = clientAssets;
    this.networkMode = networkMode;
    this.networkClient = networkClient;
    this.snapshotBuffer = buffer != null ? buffer : new NetworkSnapshotBuffer();
  }

  public void setPendingSave(SaveGame save) {
    this.pendingSave = save;
    if (save != null) {
      this.worldSeed = save.seed;
      this.worldSeedForced = true;
    }
  }

  /** Set the seed in advance (for example, from the server) before calling {@link #create()}. */
  public void setWorldSeed(long worldSeed) {
    this.worldSeed = worldSeed;
    this.worldSeedForced = true;
  }

  public void create() {
    worldCamera = new OrthographicCamera();
    worldViewport = new ScreenViewport(worldCamera);
    uiCamera = new OrthographicCamera();
    uiViewport = new ScreenViewport(uiCamera);
    cameraController =
        new CameraController(worldCamera, worldViewport, config.cameraLerp(), config.cameraZoom());

    if (!worldSeedForced) {
      String seedProp = System.getProperty("network.seed");
      if (networkMode && seedProp != null) {
        try {
          worldSeed = Long.parseLong(seedProp);
          worldSeedForced = true;
        } catch (NumberFormatException ignored) {
          worldSeed = TimeUtils.millis();
        }
      } else {
        worldSeed = TimeUtils.millis();
      }
    }
    DungeonGenerator.DungeonLayout layout =
        DungeonGenerator.generateInfinite(config.tileSize(), worldSeed);
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
    if (textureLoader != null) {
      String itemsPath = Gdx.files.internal("items.txt").file().getAbsolutePath();
      itemRegistry = ItemRegistry.loadWithLoader(itemsPath, textureLoader);
    } else {
      itemRegistry = ItemRegistry.loadDataOnly(Path.of("items.txt"));
    }
    GroundItemStore gs = new GroundItemStore(entityWorld, itemRegistry);
    this.groundStore = gs;
    this.chestStore = new ChestStore();
    enemySystem = new EnemySystem(grid, worldSeed, entityWorld, gs);
    contextFactory =
        new GameContextFactory(
            config,
            grid,
            spawnX,
            spawnY,
            worldSeed,
            itemRegistry,
            entityWorld,
            enemySystem,
            gs,
            chestStore);
    context = contextFactory.createContext();
    inventory = context.inventory();
    inventorySystem = context.inventorySystem();
    weaponSystem = context.weaponSystem();
    weaponState = weaponSystem.getState();
    mapOverlay.clearExplored();
    mapOverlay.revealAround(context.player(), 10);
    seedDemoItems();
    runStateManager = new RunStateManager();

    if (pendingSave != null) {
      applySave(pendingSave);
    }
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
    boolean mapOpen =
        mapController.update(input, mapOverlay, renderer.minimapBounds(), uiViewport, context);

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

          // send input with monotonic tick and keep it for prediction/replay
          long tick = ++clientTickCounter;
          pendingInputs.addLast(
              new SentInput(
                  tick,
                  input.movementIntent(),
                  input.weaponInput(),
                  input.dropRequested(),
                  input.pickUpRequested(),
                  input.mineRequested()));

          // local prediction: apply input immediately
          movementController.update(
              context.grid(), context.player(), context.entityWorld(), input.movementIntent());
          context.player().update(delta, config.playerSpeedTilesPerSecond());

          networkClient.sendInput(
              tick,
              input.movementIntent(),
              input.weaponInput(),
              input.dropRequested(),
              input.pickUpRequested(),
              input.mineRequested(),
              playerId);

          // Interpolate authoritative server snapshots based on server tick (avoid FPS hacks).
          final int interpolationDelayTicks = Integer.getInteger("network.interpDelayTicks", 2);
          long latestTick = snapshotBuffer.latestTick();
          if (latestTick >= 0) {
            double targetTick = (double) latestTick - interpolationDelayTicks;
            NetworkSnapshot snap = snapshotBuffer.interpolateForTick(targetTick);
            if (snap != null) {
              // only use server position for non-local smoothing—local player uses reconciliation
              // but we keep health and companion updates authoritative
              context.playerStats().setHealth(snap.playerHp());
              context
                  .companionSystem()
                  .updateFollowerTrail(context.player().getGridX(), context.player().getGridY());
              context.companionSystem().updateRender(delta);
            }
          }
          cameraController.update(context.grid(), context.player(), delta);
          float gridOriginX = cameraController.getGridOriginX();
          float gridOriginY = cameraController.getGridOriginY();
          GameUpdateResult netResult =
              new GameUpdateResult(
                  gridOriginX,
                  gridOriginY,
                  weaponState,
                  pendingInputs.size(),
                  lastProcessedTickAck);
          renderer.render(
              worldViewport,
              uiViewport,
              context,
              netResult,
              mapOverlay,
              debugTextBuilder,
              input,
              delta,
              runStateManager.isDead(),
              mapOpen);
          return;
        }
      }
    }

    GameUpdateResult updateResult =
        updater.update(
            delta,
            runStateManager.isDead(),
            input,
            context,
            context.grid().getTileSize(),
            mapOpen,
            !networkMode // simulate enemies locally only in non-network singleplayer
            );
    weaponState = updateResult.weaponState();

    boolean restartHovered =
        renderer.render(
            worldViewport,
            uiViewport,
            context,
            updateResult,
            mapOverlay,
            debugTextBuilder,
            input,
            delta,
            runStateManager.isDead(),
            mapOpen);

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
   * Rebuilds the entire runtime world from a server-provided seed so карта совпадает с
   * авторитетной.
   */
  private void rebuildWorldFromSeed(long newSeed) {
    worldSeed = newSeed;
    worldSeedForced = true;

    DungeonGenerator.DungeonLayout layout =
        DungeonGenerator.generateInfinite(config.tileSize(), worldSeed);
    Grid grid = layout.grid();
    spawnX = layout.spawnX();
    spawnY = layout.spawnY();

    enemySystem = new EnemySystem(grid, worldSeed, entityWorld, this.groundStore);
    chestStore = new ChestStore();

    contextFactory =
        new GameContextFactory(
            config,
            grid,
            spawnX,
            spawnY,
            worldSeed,
            itemRegistry,
            entityWorld,
            enemySystem,
            this.groundStore,
            chestStore);
    context = contextFactory.createContext();
    inventory = context.inventory();
    inventorySystem = context.inventorySystem();
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
          // reconcile client prediction with authoritative server state
          reconcilePlayerFromServer(
              p.lastProcessedTick(), p.x(), p.y(), p.gridX(), p.gridY(), p.hp());
        }
      }
    } else if (snap.player() != null) {
      reconcilePlayerFromServer(
          snap.player().lastProcessedTick(),
          snap.player().x(),
          snap.player().y(),
          snap.player().gridX(),
          snap.player().gridY(),
          snap.player().hp());
    }

    if (snap.weaponStates() != null && weaponState != null && myId != null) {
      for (WeaponStateSnapshotDto ws : snap.weaponStates()) {
        if (myId.equals(ws.playerId())) {
          weaponState =
              new WeaponSystem.WeaponState(
                  weaponState.active(),
                  ws.swinging(),
                  ws.aimAngleRad(),
                  weaponState.arcRad(),
                  weaponState.reachTiles(),
                  weaponState.innerHoleTiles(),
                  ws.swingProgress(),
                  weaponState.cooldownRatio(),
                  weaponState.damage(),
                  weaponState.swingIndex());
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
        if (chestStore != null) {
          chestStore.drain(bc.x(), bc.y());
          if (context.inventorySystem().isChestOpen()
              && context.inventorySystem().getChestSlotCount() > 0) {
            // close if we were viewing this chest
            context.inventorySystem().closeChest();
          }
        }
        grid.setBlock(bc.x(), bc.y(), null);
        continue;
      }
      try {
        BlockMaterial mat = BlockMaterial.valueOf(id);
        if (mat != BlockMaterial.CHEST && chestStore != null) {
          chestStore.drain(bc.x(), bc.y());
        }
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

  private void reconcilePlayerFromServer(
      long lastProcessedTick,
      float serverX,
      float serverY,
      int serverGridX,
      int serverGridY,
      float serverHp) {
    // remove acknowledged inputs
    while (!pendingInputs.isEmpty() && pendingInputs.peekFirst().tick() <= lastProcessedTick) {
      pendingInputs.removeFirst();
    }
    lastProcessedTickAck = lastProcessedTick;

    // compute distance between authoritative pos and local predicted pos
    float dx = context.player().getRenderX() - serverX;
    float dy = context.player().getRenderY() - serverY;
    float dist2 = dx * dx + dy * dy;
    float snapThreshold2 = 1.5f * 1.5f; // if >1.5 tiles, snap and replay

    if (dist2 > snapThreshold2) {
      // hard correction: set authoritative state then replay remaining inputs
      context.player().setServerPosition(serverX, serverY, serverGridX, serverGridY);
      context.playerStats().setHealth(serverHp);

      // ensure player is not mid-move
      context.player().update(0f, config.playerSpeedTilesPerSecond());

      float serverDt = Float.parseFloat(System.getProperty("network.serverTickDt", "0.05"));
      List<SentInput> toReplay = new ArrayList<>(pendingInputs);
      for (SentInput s : toReplay) {
        movementController.update(
            context.grid(), context.player(), context.entityWorld(), s.movement());
        context.player().update(serverDt, config.playerSpeedTilesPerSecond());
        // update weapon state locally as well
        float gridOriginX = cameraController.getGridOriginX();
        float gridOriginY = cameraController.getGridOriginY();
        weaponState =
            weaponSystem.update(
                serverDt,
                context.player(),
                context.inventorySystem().getEquippedStack(),
                s.weapon(),
                gridOriginX,
                gridOriginY,
                context.grid().getTileSize(),
                context.inventorySystem().isInventoryOpen(),
                false);
      }
    } else {
      // small deviation: smooth correction (lerp render position towards server over a few frames)
      float blend = 0.5f; // immediate small smoothing
      float newRenderX =
          context.player().getRenderX() + (serverX - context.player().getRenderX()) * blend;
      float newRenderY =
          context.player().getRenderY() + (serverY - context.player().getRenderY()) * blend;
      context.player().setServerPosition(newRenderX, newRenderY, serverGridX, serverGridY);
      context.playerStats().setHealth(serverHp);
    }
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
    ItemDefinition chestDef = itemRegistry.get("chest");
    if (chestDef != null) {
      inv.add(new ItemStack("chest", 1, chestDef.maxDurability()), itemRegistry);
    }
    ItemDefinition pickaxeDef = itemRegistry.get("steel_pickaxe");
    if (pickaxeDef != null) {
      inv.add(new ItemStack("steel_pickaxe", 1, pickaxeDef.maxDurability()), itemRegistry);
    }
    context
        .inventorySystem()
        .addGroundStack(
            context.player().getGridX() + 1,
            context.player().getGridY(),
            new ItemStack("test_chip", 5));
  }

  /** Capture a save snapshot of the current world. */
  public SaveGame snapshotSave(String worldName) {
    if (context == null) return null;
    context.inventorySystem().closeChest();

    var grid = context.grid();
    int minX = grid.getMinGeneratedX();
    int maxX = grid.getMaxGeneratedX();
    int minY = grid.getMinGeneratedY();
    int maxY = grid.getMaxGeneratedY();

    List<SaveGame.BlockCellState> blockCells = new ArrayList<>();
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        var material = grid.getBlockMaterial(x, y);
        float hp = grid.getBlockHealth(x, y);
        String matName = material != null ? material.name() : null;
        blockCells.add(new SaveGame.BlockCellState(x, y, matName, hp));
      }
    }

    var groundSnapshots = new ArrayList<SaveGame.GroundItemState>();
    for (var gi : groundStore.getGroundItems()) {
      List<SaveGame.ItemStackState> bundled = new ArrayList<>();
      for (var b : gi.getBundledItems()) {
        var bs = SaveGame.ItemStackState.from(b);
        if (bs != null) bundled.add(bs);
      }
      groundSnapshots.add(
          new SaveGame.GroundItemState(
              gi.id(),
              gi.getGridX(),
              gi.getGridY(),
              SaveGame.ItemStackState.from(gi.getStack()),
              bundled));
    }

    var invStates = new SaveGame.ItemStackState[inventory.size()];
    for (int i = 0; i < inventory.size(); i++) {
      invStates[i] = SaveGame.ItemStackState.from(inventory.get(i));
    }

    var ps =
        new SaveGame.PlayerState(
            context.player().getRenderX(),
            context.player().getRenderY(),
            context.player().getGridX(),
            context.player().getGridY(),
            context.playerStats().getHealth());
    var cs =
        new SaveGame.CompanionState(
            context.companionSystem().getRenderX(),
            context.companionSystem().getRenderY(),
            context.companionSystem().getGridX(),
            context.companionSystem().getGridY());
    var chestStates = context.chestStore().toSaveStates();

    return new SaveGame(
        worldName,
        worldSeed,
        Instant.now().toEpochMilli(),
        minX,
        maxX,
        minY,
        maxY,
        ps,
        cs,
        invStates,
        blockCells,
        chestStates,
        groundSnapshots,
        EntityIds.peek());
  }

  /** Apply a previously saved snapshot to the current runtime (after create). */
  private void applySave(SaveGame save) {
    if (save == null || context == null) return;
    context.inventorySystem().closeChest();
    if (save.seed != 0) {
      this.worldSeed = save.seed;
    }
    EntityIds.setNext(save.nextEntityId);

    var grid = context.grid();
    for (var cell : save.blocks) {
      BlockMaterial mat =
          cell.blockMaterial != null ? BlockMaterial.valueOf(cell.blockMaterial) : null;
      grid.setBlock(cell.x, cell.y, mat);
      if (mat != null) {
        float maxHp = mat.maxHealth();
        float damage = Math.max(0f, maxHp - cell.blockHp);
        if (damage > 0.0001f) {
          grid.damageBlock(cell.x, cell.y, damage);
        }
      }
    }

    groundStore.clear();
    for (var g : save.groundItems) {
      var stack = g.stack != null ? g.stack.toItemStack() : null;
      List<ItemStack> bundled = new ArrayList<>();
      if (g.bundled != null) {
        for (var b : g.bundled) {
          var bs = b.toItemStack();
          if (bs != null) bundled.add(bs);
        }
      }
      if (stack != null && bundled.isEmpty()) {
        groundStore.upsertGroundItem(g.id, g.x, g.y, stack);
      } else if (stack != null) {
        groundStore.addGroundBundle(g.x, g.y, stack, bundled);
      }
    }

    if (save.player != null) {
      context
          .player()
          .setServerPosition(
              save.player.renderX, save.player.renderY, save.player.gridX, save.player.gridY);
      context.playerStats().setHealth(save.player.health);
    }

    int playerGridX = save.player != null ? save.player.gridX : context.player().getGridX();
    int playerGridY = save.player != null ? save.player.gridY : context.player().getGridY();
    float playerRenderX = save.player != null ? save.player.renderX : context.player().getRenderX();
    float playerRenderY = save.player != null ? save.player.renderY : context.player().getRenderY();

    if (save.companion != null) {
      context
          .companionSystem()
          .resetState(
              save.companion.gridX,
              save.companion.gridY,
              save.companion.renderX,
              save.companion.renderY,
              playerGridX,
              playerGridY);
    } else if (save.player != null) {
      // Backwards compatibility for old saves: spawn Dotty next to the saved player.
      context
          .companionSystem()
          .resetState(
              save.player.gridX,
              save.player.gridY,
              playerRenderX,
              playerRenderY,
              playerGridX,
              playerGridY);
    }

    if (save.inventory != null) {
      for (int i = 0; i < inventory.size(); i++) {
        inventory.set(i, null);
      }
      for (int i = 0; i < Math.min(inventory.size(), save.inventory.length); i++) {
        var s = save.inventory[i];
        inventory.set(i, s != null ? s.toItemStack() : null);
      }
    }

    chestStore.clear();
    chestStore.loadFrom(save.chests);
  }
}
