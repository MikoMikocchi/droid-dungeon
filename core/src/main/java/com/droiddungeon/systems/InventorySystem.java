package com.droiddungeon.systems;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.inventory.ItemStack;
import com.droiddungeon.items.GroundItem;
import com.droiddungeon.items.ItemRegistry;

/**
 * Manages inventory UI state, cursor stack, hotbar selection and ground item interactions.
 */
public final class InventorySystem {
    private final Inventory inventory;
    private final ItemRegistry itemRegistry;
    private final Grid grid;
    private final List<GroundItem> groundItems = new ArrayList<>();

    private ItemStack cursorStack;
    private boolean inventoryOpen;
    private int selectedSlotIndex;

    public InventorySystem(Inventory inventory, ItemRegistry itemRegistry, Grid grid) {
        this.inventory = inventory;
        this.itemRegistry = itemRegistry;
        this.grid = grid;
        this.cursorStack = null;
        this.inventoryOpen = false;
        this.selectedSlotIndex = 0;
    }

    public void updateInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            toggleInventory();
        }
        int hotbarKeySlot = pollHotbarNumberKey();
        if (hotbarKeySlot != -1) {
            selectedSlotIndex = hotbarKeySlot;
        }
    }

    public void toggleInventory() {
        inventoryOpen = !inventoryOpen;
        if (!inventoryOpen && selectedSlotIndex >= Inventory.HOTBAR_SLOTS) {
            selectedSlotIndex %= Inventory.HOTBAR_SLOTS;
        }
    }

    public void dropCurrentStack(Player player) {
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

    public void pickUpItemsAtPlayer(Player player) {
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
            }
        }
    }

    public void onSlotClicked(int slotIndex) {
        ItemStack slotStack = inventory.get(slotIndex);

        if (cursorStack == null) {
            if (slotStack != null) {
                cursorStack = slotStack;
                inventory.set(slotIndex, null);
            }
            selectedSlotIndex = slotIndex;
            return;
        }

        if (slotStack == null) {
            inventory.set(slotIndex, cursorStack);
            cursorStack = null;
            selectedSlotIndex = slotIndex;
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
                selectedSlotIndex = slotIndex;
                return;
            }
        }

        inventory.set(slotIndex, cursorStack);
        cursorStack = slotStack;
        selectedSlotIndex = slotIndex;
    }

    public void addGroundStack(int gridX, int gridY, ItemStack stack) {
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

    public boolean isInventoryOpen() {
        return inventoryOpen;
    }

    public int getSelectedSlotIndex() {
        return selectedSlotIndex;
    }

    public ItemStack getCursorStack() {
        return cursorStack;
    }

    public List<GroundItem> getGroundItems() {
        return groundItems;
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
}
