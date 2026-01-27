package com.droiddungeon;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
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
import com.droiddungeon.systems.CompanionSystem;
import com.droiddungeon.systems.InventorySystem;
import com.droiddungeon.ui.HudRenderer;

public class DroidDungeonGame extends ApplicationAdapter {
    private Stage stage;
    private Viewport worldViewport;
    private OrthographicCamera worldCamera;
    private WorldRenderer worldRenderer;
    private HudRenderer hudRenderer;
    private HeldMovementController movementController;

    private Grid grid;
    private Player player;

    private Inventory inventory;
    private ItemRegistry itemRegistry;
    private InventorySystem inventorySystem;

    private final float cameraLerp = 6f;
    private final float cameraZoom = 1f;
    private CompanionSystem companionSystem;

    @Override
    public void create() {
        stage = new Stage(new ScreenViewport()); // UI stage
        worldCamera = new OrthographicCamera();
        worldViewport = new ScreenViewport(worldCamera);

        long seed = TimeUtils.millis();
        int columns = 80;
        int rows = 60;
        float tileSize = 48f;
        DungeonGenerator.DungeonLayout layout = DungeonGenerator.generate(columns, rows, tileSize, seed);
        grid = layout.grid();
        player = new Player(layout.spawnX(), layout.spawnY());
        companionSystem = new CompanionSystem(player.getGridX(), player.getGridY(), 3, 12f);

        worldRenderer = new WorldRenderer();
        hudRenderer = new HudRenderer();
        movementController = new HeldMovementController();

        inventory = new Inventory();
        itemRegistry = ItemRegistry.load("items.txt");
        inventorySystem = new InventorySystem(inventory, itemRegistry, grid);
        seedDemoItems();

        // Input is handled by polling (for held-key movement)
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        worldViewport.update(width, height, true);
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        inventorySystem.updateInput();
        if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            inventorySystem.dropCurrentStack(player);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            inventorySystem.pickUpItemsAtPlayer(player);
        }

        float delta = Gdx.graphics.getDeltaTime();
        stage.act(delta);

        int hoveredSlot = -1;
        if (inventorySystem.isInventoryOpen()) {
            hoveredSlot = hudRenderer.hitTestSlot(stage, Gdx.input.getX(), Gdx.input.getY(), true);
        }

        if (Gdx.input.justTouched()) {
            int clicked = hudRenderer.hitTestSlot(stage, Gdx.input.getX(), Gdx.input.getY(), inventorySystem.isInventoryOpen());
            if (clicked != -1) {
                inventorySystem.onSlotClicked(clicked);
            }
        }

        if (!inventorySystem.isInventoryOpen()) {
            movementController.update(grid, player);
        }
        companionSystem.updateFollowerTrail(player.getGridX(), player.getGridY());
        player.update(delta, 10f);
        companionSystem.updateRender(delta);

        float gridOriginX = (worldViewport.getWorldWidth() - grid.getWorldWidth()) * 0.5f;
        float gridOriginY = (worldViewport.getWorldHeight() - grid.getWorldHeight()) * 0.5f;
        updateCamera(delta, gridOriginX, gridOriginY);

        worldRenderer.render(worldViewport, grid, player, inventorySystem.getGroundItems(), itemRegistry, companionSystem.getRenderX(), companionSystem.getRenderY());
        String debugText = buildDebugText(gridOriginX, gridOriginY);
        hudRenderer.render(stage, inventory, itemRegistry, inventorySystem.getCursorStack(), inventorySystem.isInventoryOpen(), inventorySystem.getSelectedSlotIndex(), hoveredSlot, delta, debugText, grid, player, companionSystem.getRenderX(), companionSystem.getRenderY());
    }

    @Override
    public void dispose() {
        stage.dispose();
        worldRenderer.dispose();
        hudRenderer.dispose();
        RenderAssets.dispose();
        itemRegistry.dispose();
    }

    private void seedDemoItems() {
        inventory.add(new ItemStack("test_chip", 8), itemRegistry);
        int dropX = Math.min(grid.getColumns() - 1, player.getGridX() + 1);
        int dropY = player.getGridY();
        inventorySystem.addGroundStack(dropX, dropY, new ItemStack("test_chip", 5));
    }

    private void updateCamera(float deltaSeconds, float gridOriginX, float gridOriginY) {
        OrthographicCamera camera = worldCamera;
        float tileSize = grid.getTileSize();
        float targetX = gridOriginX + (player.getRenderX() + 0.5f) * tileSize;
        float targetY = gridOriginY + (player.getRenderY() + 0.5f) * tileSize;

        float lerp = 1f - (float) Math.exp(-cameraLerp * deltaSeconds);
        camera.position.x += (targetX - camera.position.x) * lerp;
        camera.position.y += (targetY - camera.position.y) * lerp;
        camera.zoom = cameraZoom;
        camera.update();
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
        return text.toString();
    }
}
