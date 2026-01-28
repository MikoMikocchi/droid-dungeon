package com.droiddungeon;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.config.GameConfig;
import com.droiddungeon.grid.DungeonGenerator;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.grid.TileMaterial;
import com.droiddungeon.input.HeldMovementController;
import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.inventory.ItemStack;
import com.droiddungeon.items.GroundItem;
import com.droiddungeon.items.ItemDefinition;
import com.droiddungeon.items.ItemRegistry;
import com.droiddungeon.render.RenderAssets;
import com.droiddungeon.render.WorldRenderer;
import com.droiddungeon.systems.CameraController;
import com.droiddungeon.systems.CompanionSystem;
import com.droiddungeon.systems.EnemySystem;
import com.droiddungeon.systems.InventorySystem;
import com.droiddungeon.systems.WeaponSystem;
import com.droiddungeon.player.PlayerStats;
import com.droiddungeon.ui.DebugOverlay;
import com.droiddungeon.ui.HudRenderer;
import java.util.List;

public class DroidDungeonGame extends ApplicationAdapter {
    private Viewport worldViewport;
    private Viewport uiViewport;
    private OrthographicCamera worldCamera;
    private OrthographicCamera uiCamera;
    private WorldRenderer worldRenderer;
    private HudRenderer hudRenderer;
    private DebugOverlay debugOverlay;
    private HeldMovementController movementController;
    private CameraController cameraController;

    private Grid grid;
    private Player player;
    private PlayerStats playerStats;
    private EnemySystem enemySystem;
    private long worldSeed;
    private int spawnX;
    private int spawnY;
    private boolean dead;
    private int deathGridX;
    private int deathGridY;

    private Inventory inventory;
    private ItemRegistry itemRegistry;
    private InventorySystem inventorySystem;

    private CompanionSystem companionSystem;
    private WeaponSystem weaponSystem;
    private WeaponSystem.WeaponState weaponState;
    private GameConfig config;

    @Override
    public void create() {
        config = GameConfig.defaults();
        worldCamera = new OrthographicCamera();
        worldViewport = new ScreenViewport(worldCamera);
        uiCamera = new OrthographicCamera();
        uiViewport = new ScreenViewport(uiCamera);
        cameraController = new CameraController(worldCamera, worldViewport, config.cameraLerp(), config.cameraZoom());

        worldSeed = TimeUtils.millis();
        DungeonGenerator.DungeonLayout layout = DungeonGenerator.generateInfinite(config.tileSize(), worldSeed);
        grid = layout.grid();
        spawnX = layout.spawnX();
        spawnY = layout.spawnY();
        player = new Player(spawnX, spawnY);
        playerStats = new PlayerStats(100f);
        companionSystem = new CompanionSystem(player.getGridX(), player.getGridY(), config.companionDelayTiles(), config.companionSpeedTilesPerSecond());
        enemySystem = new EnemySystem(grid, worldSeed);

        worldRenderer = new WorldRenderer();
        hudRenderer = new HudRenderer();
        debugOverlay = new DebugOverlay();
        movementController = new HeldMovementController();

        inventory = new Inventory();
        itemRegistry = ItemRegistry.load("items.txt");
        inventorySystem = new InventorySystem(inventory, itemRegistry, grid);
        setupWeapons();
        seedDemoItems();

        // Input is handled by polling (for held-key movement)
    }

    @Override
    public void resize(int width, int height) {
        uiViewport.update(width, height, true);
        cameraController.resize(width, height);
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float delta = Gdx.graphics.getDeltaTime();
        playerStats.update(delta);
        if (playerStats.isDead() && !dead) {
            onPlayerDeath();
        }

        int slotUnderCursor = -1;
        int hoveredSlot = -1;
        boolean pointerOnUi = false;

        if (!dead) {
            inventorySystem.updateInput();
            if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
                inventorySystem.dropCurrentStack(player);
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
                inventorySystem.pickUpItemsAtPlayer(player);
            }

            slotUnderCursor = hudRenderer.hitTestSlot(uiViewport, Gdx.input.getX(), Gdx.input.getY(), inventorySystem.isInventoryOpen());
            hoveredSlot = inventorySystem.isInventoryOpen() ? slotUnderCursor : -1;
            if (Gdx.input.justTouched() && slotUnderCursor != -1) {
                inventorySystem.onSlotClicked(slotUnderCursor);
            }
            pointerOnUi = slotUnderCursor != -1;

            if (!inventorySystem.isInventoryOpen()) {
                movementController.update(grid, player, enemySystem);
            }
            companionSystem.updateFollowerTrail(player.getGridX(), player.getGridY());
            player.update(delta, config.playerSpeedTilesPerSecond());
            companionSystem.updateRender(delta);

            cameraController.update(grid, player, delta);
            float gridOriginX = cameraController.getGridOriginX();
            float gridOriginY = cameraController.getGridOriginY();

            weaponState = weaponSystem.update(
                    delta,
                    player,
                    inventorySystem.getEquippedStack(),
                    worldViewport,
                    gridOriginX,
                    gridOriginY,
                    grid.getTileSize(),
                    inventorySystem.isInventoryOpen(),
                    pointerOnUi
            );

            enemySystem.update(delta, player, playerStats, weaponState);

            worldRenderer.render(worldViewport, gridOriginX, gridOriginY, grid, player, weaponState, inventorySystem.getGroundItems(), itemRegistry, enemySystem.getEnemies(), companionSystem.getRenderX(), companionSystem.getRenderY());
            String debugText = buildDebugText(gridOriginX, gridOriginY);
            hudRenderer.render(uiViewport, inventory, itemRegistry, inventorySystem.getCursorStack(), inventorySystem.isInventoryOpen(), inventorySystem.getSelectedSlotIndex(), hoveredSlot, delta, playerStats);
            debugOverlay.render(uiViewport, grid, player, companionSystem.getRenderX(), companionSystem.getRenderY(), debugText);
        } else {
            cameraController.update(grid, player, delta);
            float gridOriginX = cameraController.getGridOriginX();
            float gridOriginY = cameraController.getGridOriginY();
            weaponState = WeaponSystem.WeaponState.inactive();

            worldRenderer.render(worldViewport, gridOriginX, gridOriginY, grid, player, weaponState, inventorySystem.getGroundItems(), itemRegistry, enemySystem.getEnemies(), companionSystem.getRenderX(), companionSystem.getRenderY());
            String debugText = buildDebugText(gridOriginX, gridOriginY);
            hudRenderer.render(uiViewport, inventory, itemRegistry, null, false, inventorySystem.getSelectedSlotIndex(), hoveredSlot, delta, playerStats);
            debugOverlay.render(uiViewport, grid, player, companionSystem.getRenderX(), companionSystem.getRenderY(), debugText);
        }

        if (dead) {
            boolean restartHovered = isRestartHovered();
            if (Gdx.input.justTouched() && restartHovered) {
                restartRun();
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
                restartRun();
            }
            hudRenderer.renderDeathOverlay(uiViewport, restartHovered);
        }
    }

    @Override
    public void dispose() {
        worldRenderer.dispose();
        hudRenderer.dispose();
        debugOverlay.dispose();
        RenderAssets.dispose();
        itemRegistry.dispose();
    }

    private void seedDemoItems() {
        inventory.add(new ItemStack("test_chip", 8), itemRegistry);
        ItemDefinition rapierDef = itemRegistry.get("steel_rapier");
        if (rapierDef != null) {
            inventory.add(new ItemStack("steel_rapier", 1, rapierDef.maxDurability()), itemRegistry);
        }
        ItemDefinition pickaxeDef = itemRegistry.get("steel_pickaxe");
        if (pickaxeDef != null) {
            inventory.add(new ItemStack("steel_pickaxe", 1, pickaxeDef.maxDurability()), itemRegistry);
        }
        inventorySystem.addGroundStack(player.getGridX() + 1, player.getGridY(), new ItemStack("test_chip", 5));
    }

    private void setupWeapons() {
        weaponSystem = new WeaponSystem();
        weaponSystem.register("steel_rapier", new WeaponSystem.WeaponSpec(
                MathUtils.degreesToRadians * 20f,
                3.4f,
                0.42f,
                0.22f,
                0.55f,
                12f
        ));
        weaponState = weaponSystem.getState();
    }

    private void onPlayerDeath() {
        dead = true;
        deathGridX = player.getGridX();
        deathGridY = player.getGridY();
        inventorySystem.forceCloseInventory();

        List<ItemStack> loot = inventorySystem.drainAllItems();
        if (!loot.isEmpty()) {
            inventorySystem.addGroundBundle(deathGridX, deathGridY, new ItemStack("pouch", 1), loot);
        }
    }

    private boolean isRestartHovered() {
        Rectangle rect = hudRenderer.getRestartButtonBounds(uiViewport);
        Vector2 world = uiViewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
        return rect.contains(world);
    }

    private void restartRun() {
        dead = false;
        player = new Player(spawnX, spawnY);
        playerStats = new PlayerStats(playerStats.getMaxHealth());
        companionSystem = new CompanionSystem(player.getGridX(), player.getGridY(), config.companionDelayTiles(), config.companionSpeedTilesPerSecond());
        enemySystem = new EnemySystem(grid, worldSeed);
        setupWeapons();
        inventorySystem.forceCloseInventory();
    }

    private String buildDebugText(float gridOriginX, float gridOriginY) {
        float tileSize = grid.getTileSize();
        Vector2 world = worldViewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
        float localX = world.x - gridOriginX;
        float localY = world.y - gridOriginY;

        int tileX = (int) Math.floor(localX / tileSize);
        int tileY = (int) Math.floor(localY / tileSize);

        StringBuilder text = new StringBuilder();
        if (grid.isInside(tileX, tileY)) {
            TileMaterial material = grid.getTileMaterial(tileX, tileY);
            text.append("Tile ").append(tileX).append(", ").append(tileY)
                    .append(" — ").append(material.displayName());

            DungeonGenerator.RoomType roomType = grid.getRoomType(tileX, tileY);
            text.append("\nRoom: ").append(roomType == null ? "Corridor" : roomType);

            boolean hasEntities = false;
            if (player.getGridX() == tileX && player.getGridY() == tileY) {
                text.append("\nEntity: Player");
                hasEntities = true;
            }

            int doroX = companionSystem.getGridX();
            int doroY = companionSystem.getGridY();
            if (doroX == tileX && doroY == tileY) {
                text.append(hasEntities ? ", " : "\nEntity: ").append("Doro");
                hasEntities = true;
            }

            if (enemySystem != null) {
                for (com.droiddungeon.enemies.Enemy enemy : enemySystem.getEnemies()) {
                    if (enemy.getGridX() == tileX && enemy.getGridY() == tileY) {
                        text.append(hasEntities ? ", " : "\nEntity: ").append("Catster");
                        hasEntities = true;
                    }
                }
            }

            for (GroundItem groundItem : inventorySystem.getGroundItems()) {
                if (groundItem.isAt(tileX, tileY)) {
                    ItemDefinition def = itemRegistry.get(groundItem.getStack().itemId());
                    String name = def != null ? def.displayName() : groundItem.getStack().itemId();
                    int count = groundItem.getStack().count();
                    text.append(hasEntities ? ", " : "\nEntity: ");
                    text.append(name);
                    if (count > 1) {
                        text.append(" x").append(count);
                    }
                    hasEntities = true;
                }
            }

            if (!hasEntities) {
                text.append("\nEntity: —");
            }
        } else {
            text.append("Cursor: out of bounds");
        }

        ItemStack equipped = inventorySystem.getEquippedStack();
        if (equipped != null) {
            ItemDefinition def = itemRegistry.get(equipped.itemId());
            String name = def != null ? def.displayName() : equipped.itemId();
            text.append("\nEquipped: ").append(name);
            if (def != null && def.maxDurability() > 0) {
                int current = Math.min(equipped.durability(), def.maxDurability());
                text.append(" (").append(current).append("/").append(def.maxDurability()).append(")");
            }
        } else {
            text.append("\nEquipped: —");
        }

        text.append("\nHP: ").append(Math.round(playerStats.getHealth())).append("/").append((int) playerStats.getMaxHealth());
        if (enemySystem != null) {
            int total = enemySystem.getEnemies().size();
            int alert = 0;
            for (com.droiddungeon.enemies.Enemy enemy : enemySystem.getEnemies()) {
                if (enemy.hasLineOfSight()) {
                    alert++;
                }
            }
            text.append("\nEnemies: ").append(total);
            if (alert > 0) {
                text.append(" (alert: ").append(alert).append(")");
            }
        }
        return text.toString();
    }
}
