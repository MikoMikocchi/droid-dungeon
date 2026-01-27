package com.droiddungeon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

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
import com.droiddungeon.render.WorldRenderer;
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
    private ItemStack cursorStack;
    private boolean inventoryOpen;
    private int selectedSlotIndex;
    private List<GroundItem> groundItems;

    private float cameraLerp = 6f;
    private float cameraZoom = 1f;
    private int companionGridX;
    private int companionGridY;
    private float companionRenderX;
    private float companionRenderY;
    private final int companionDelayTiles = 3;
    private float companionSpeedTilesPerSecond = 12f;
    private final Deque<int[]> followerTrail = new ArrayDeque<>();
    private int lastPlayerGridX;
    private int lastPlayerGridY;

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
        companionGridX = player.getGridX();
        companionGridY = player.getGridY();
        companionRenderX = companionGridX;
        companionRenderY = companionGridY;
        lastPlayerGridX = player.getGridX();
        lastPlayerGridY = player.getGridY();
        // Seed the trail so Doro starts behind after a few steps.
        followerTrail.clear();
        for (int i = 0; i < companionDelayTiles; i++) {
            followerTrail.addLast(new int[]{companionGridX, companionGridY});
        }

        worldRenderer = new WorldRenderer();
        hudRenderer = new HudRenderer();
        movementController = new HeldMovementController();

        inventory = new Inventory();
        itemRegistry = ItemRegistry.load("items.txt");
        cursorStack = null;
        inventoryOpen = false;
        selectedSlotIndex = 0;
        groundItems = new ArrayList<>();
        seedDemoItems();

        // Input is handled by polling (for held-key movement)
        Gdx.input.setInputProcessor(stage);
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

        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            inventoryOpen = !inventoryOpen;
            if (!inventoryOpen && selectedSlotIndex >= Inventory.HOTBAR_SLOTS) {
                selectedSlotIndex %= Inventory.HOTBAR_SLOTS;
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            dropCurrentStack();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            pickUpItemsAtPlayer();
        }

        int hotbarKeySlot = pollHotbarNumberKey();
        if (hotbarKeySlot != -1) {
            selectedSlotIndex = hotbarKeySlot;
        }

        float delta = Gdx.graphics.getDeltaTime();
        stage.act(delta);

        int hoveredSlot = -1;
        if (inventoryOpen) {
            hoveredSlot = hudRenderer.hitTestSlot(stage, Gdx.input.getX(), Gdx.input.getY(), true);
        }

        if (Gdx.input.justTouched()) {
            int clicked = hudRenderer.hitTestSlot(stage, Gdx.input.getX(), Gdx.input.getY(), inventoryOpen);
            if (clicked != -1) {
                selectedSlotIndex = clicked;
                handleSlotClick(clicked);
            }
        }

        if (!inventoryOpen) {
            movementController.update(grid, player);
        }
        updateFollowerTrail();
        player.update(delta, 10f);
        updateCompanionRender(delta);

        float gridOriginX = (worldViewport.getWorldWidth() - grid.getWorldWidth()) * 0.5f;
        float gridOriginY = (worldViewport.getWorldHeight() - grid.getWorldHeight()) * 0.5f;
        updateCamera(delta, gridOriginX, gridOriginY);

        worldRenderer.render(worldViewport, grid, player, groundItems, itemRegistry, companionRenderX, companionRenderY);
        String debugText = buildDebugText(gridOriginX, gridOriginY);
        hudRenderer.render(stage, inventory, itemRegistry, cursorStack, inventoryOpen, selectedSlotIndex, hoveredSlot, delta, debugText, grid, player, companionRenderX, companionRenderY);
    }

    @Override
    public void dispose() {
        stage.dispose();
        worldRenderer.dispose();
        hudRenderer.dispose();
        itemRegistry.dispose();
    }

    private void seedDemoItems() {
        inventory.add(new ItemStack("test_chip", 8), itemRegistry);
        int dropX = Math.min(grid.getColumns() - 1, player.getGridX() + 1);
        int dropY = player.getGridY();
        addGroundStack(dropX, dropY, new ItemStack("test_chip", 5));
    }

    private static int pollHotbarNumberKey() {
        // 1..9 -> slots 0..8, 0 -> slot 9.
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_1)) return 0;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_2)) return 1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_3)) return 2;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_4)) return 3;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_5) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_5)) return 4;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_6) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_6)) return 5;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_7) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_7)) return 6;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_8) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_8)) return 7;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_9) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_9)) return 8;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_0) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_0)) return 9;
        return -1;
    }

    private void dropCurrentStack() {
        ItemStack toDrop = cursorStack;
        boolean fromCursor = true;
        if (toDrop == null) {
            toDrop = inventory.get(selectedSlotIndex);
            fromCursor = false;
        }
        if (toDrop == null) {
            return;
        }
        addGroundStack(player.getGridX(), player.getGridY(), toDrop);
        if (fromCursor) {
            cursorStack = null;
        } else {
            inventory.set(selectedSlotIndex, null);
        }
    }

    private void pickUpItemsAtPlayer() {
        int playerX = player.getGridX();
        int playerY = player.getGridY();
        Iterator<GroundItem> iterator = groundItems.iterator();
        while (iterator.hasNext()) {
            GroundItem groundItem = iterator.next();
            if (!groundItem.isAt(playerX, playerY)) {
                continue;
            }
            ItemStack stack = groundItem.getStack();
            ItemStack remaining = mergeIntoCursor(stack);
            if (remaining != null) {
                remaining = inventory.add(remaining, itemRegistry);
            }
            if (remaining == null) {
                iterator.remove();
            } else if (remaining.count() != stack.count()) {
                groundItem.setStack(remaining);
            } else {
                break;
            }
        }
    }

    private ItemStack mergeIntoCursor(ItemStack stack) {
        if (cursorStack == null) {
            return stack;
        }
        if (!cursorStack.itemId().equals(stack.itemId())) {
            return stack;
        }
        int max = itemRegistry.maxStackSize(cursorStack.itemId());
        int space = max - cursorStack.count();
        if (space <= 0) {
            return stack;
        }
        int toMove = Math.min(space, stack.count());
        cursorStack = cursorStack.withCount(cursorStack.count() + toMove);
        if (toMove == stack.count()) {
            return null;
        }
        return stack.withCount(stack.count() - toMove);
    }

    private void addGroundStack(int gridX, int gridY, ItemStack stack) {
        if (stack == null) {
            return;
        }
        if (!grid.isInside(gridX, gridY)) {
            return;
        }
        int maxStack = itemRegistry.maxStackSize(stack.itemId());
        ItemStack remaining = stack;

        for (GroundItem groundItem : groundItems) {
            if (!groundItem.isAt(gridX, gridY)) {
                continue;
            }
            if (!groundItem.getStack().itemId().equals(remaining.itemId())) {
                continue;
            }
            int space = maxStack - groundItem.getStack().count();
            if (space <= 0) {
                continue;
            }
            int toMove = Math.min(space, remaining.count());
            groundItem.setStack(groundItem.getStack().withCount(groundItem.getStack().count() + toMove));
            if (toMove == remaining.count()) {
                remaining = null;
                break;
            }
            remaining = remaining.withCount(remaining.count() - toMove);
        }

        while (remaining != null) {
            int chunk = Math.min(remaining.count(), maxStack);
            groundItems.add(new GroundItem(gridX, gridY, new ItemStack(remaining.itemId(), chunk)));
            if (remaining.count() <= maxStack) {
                remaining = null;
            } else {
                remaining = remaining.withCount(remaining.count() - chunk);
            }
        }
    }

    private void handleSlotClick(int slotIndex) {
        ItemStack slotStack = inventory.get(slotIndex);

        if (cursorStack == null) {
            if (slotStack != null) {
                cursorStack = slotStack;
                inventory.set(slotIndex, null);
            }
            return;
        }

        if (slotStack == null) {
            inventory.set(slotIndex, cursorStack);
            cursorStack = null;
            return;
        }

        if (slotStack.itemId().equals(cursorStack.itemId())) {
            int maxStack = itemRegistry.maxStackSize(slotStack.itemId());
            int space = maxStack - slotStack.count();
            if (space > 0) {
                int toTransfer = Math.min(space, cursorStack.count());
                inventory.set(slotIndex, slotStack.withCount(slotStack.count() + toTransfer));
                if (toTransfer == cursorStack.count()) {
                    cursorStack = null;
                } else {
                    cursorStack = cursorStack.withCount(cursorStack.count() - toTransfer);
                }
                return;
            }
        }

        inventory.set(slotIndex, cursorStack);
        cursorStack = slotStack;
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

    private void updateFollowerTrail() {
        int px = player.getGridX();
        int py = player.getGridY();
        if (px != lastPlayerGridX || py != lastPlayerGridY) {
            followerTrail.addLast(new int[]{px, py});
            while (followerTrail.size() > companionDelayTiles) {
                int[] next = followerTrail.removeFirst();
                companionGridX = next[0];
                companionGridY = next[1];
            }
            lastPlayerGridX = px;
            lastPlayerGridY = py;
        }
    }

    private void updateCompanionRender(float deltaSeconds) {
        float targetX = companionGridX;
        float targetY = companionGridY;
        float dx = targetX - companionRenderX;
        float dy = targetY - companionRenderY;
        float dist2 = dx * dx + dy * dy;
        if (dist2 < 0.000001f || deltaSeconds <= 0f) {
            companionRenderX = targetX;
            companionRenderY = targetY;
            return;
        }
        float dist = (float) Math.sqrt(dist2);
        float step = companionSpeedTilesPerSecond * deltaSeconds;
        if (step >= dist) {
            companionRenderX = targetX;
            companionRenderY = targetY;
            return;
        }
        companionRenderX += (dx / dist) * step;
        companionRenderY += (dy / dist) * step;
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

            int doroX = companionGridX;
            int doroY = companionGridY;
            if (doroX == tileX && doroY == tileY) {
                text.append(hasEntities ? ", " : "\nEntity: ").append("Doro");
                hasEntities = true;
            }

            if (groundItems != null) {
                for (GroundItem groundItem : groundItems) {
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
