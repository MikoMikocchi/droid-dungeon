package com.droiddungeon;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.grid.TileMaterial;
import com.droiddungeon.input.HeldMovementController;
import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.inventory.ItemStack;
import com.droiddungeon.items.GroundItem;
import com.droiddungeon.items.ItemRegistry;
import com.droiddungeon.render.WorldRenderer;
import com.droiddungeon.ui.HudRenderer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DroidDungeonGame extends ApplicationAdapter {
    private Stage stage;
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

    @Override
    public void create() {
        stage = new Stage(new ScreenViewport());

        grid = new Grid(20, 12, 48f);
        seedDemoTerrain();
        player = new Player(grid.getColumns() / 2, grid.getRows() / 2);

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
        player.update(delta, 10f);

        worldRenderer.render(stage, grid, player, groundItems, itemRegistry);
        hudRenderer.render(stage, inventory, itemRegistry, cursorStack, inventoryOpen, selectedSlotIndex, hoveredSlot, delta);

        stage.draw();
    }

    @Override
    public void dispose() {
        stage.dispose();
        worldRenderer.dispose();
        hudRenderer.dispose();
        itemRegistry.dispose();
    }

    private void seedDemoTerrain() {
        grid.fill(TileMaterial.DIRT);

        int lastColumn = grid.getColumns() - 1;
        int lastRow = grid.getRows() - 1;

        for (int x = 0; x <= lastColumn; x++) {
            grid.setTileMaterial(x, 0, TileMaterial.STONE);
            grid.setTileMaterial(x, lastRow, TileMaterial.STONE);
        }
        for (int y = 0; y <= lastRow; y++) {
            grid.setTileMaterial(0, y, TileMaterial.STONE);
            grid.setTileMaterial(lastColumn, y, TileMaterial.STONE);
        }

        int hallY = grid.getRows() / 2;
        for (int x = 1; x < lastColumn; x++) {
            grid.setTileMaterial(x, hallY, TileMaterial.PLANKS);
        }

        int gravelStartY = Math.max(1, hallY - 3);
        int gravelStartX = Math.min(2, lastColumn);
        int gravelEndX = Math.max(gravelStartX, lastColumn - 2);
        for (int y = gravelStartY; y < hallY; y++) {
            for (int x = gravelStartX; x <= gravelEndX; x++) {
                grid.setTileMaterial(x, y, TileMaterial.GRAVEL);
            }
        }

        int woodStartX = Math.min(2, lastColumn);
        int woodStartY = Math.min(Math.max(1, hallY + 1), lastRow);
        int woodEndX = Math.min(woodStartX + 3, lastColumn);
        int woodEndY = Math.min(woodStartY + 2, lastRow);
        if (woodStartX <= woodEndX && woodStartY <= woodEndY) {
            for (int y = woodStartY; y <= woodEndY; y++) {
                for (int x = woodStartX; x <= woodEndX; x++) {
                    grid.setTileMaterial(x, y, TileMaterial.WOOD);
                }
            }
        }
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
}
