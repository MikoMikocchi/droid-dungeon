package com.droiddungeon.systems;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.droiddungeon.crafting.CraftingSystem;
import com.droiddungeon.crafting.CraftingRecipes;
import com.droiddungeon.entity.EntityIds;
import com.droiddungeon.entity.EntityWorld;
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
    private final EntityWorld entityWorld;
    private final Grid grid;
    private final CraftingSystem craftingSystem;
    private final com.droiddungeon.items.GroundItemStore groundStore;
    private int selectedRecipeIndex;

    private ItemStack cursorStack;
    private boolean inventoryOpen;
    private int selectedSlotIndex;
    private int equippedSlotIndex;

    public InventorySystem(Inventory inventory, ItemRegistry itemRegistry, Grid grid, EntityWorld entityWorld, com.droiddungeon.items.GroundItemStore groundStore) {
        this.inventory = inventory;
        this.itemRegistry = itemRegistry;
        this.grid = grid;
        this.entityWorld = entityWorld;
        this.groundStore = groundStore;
        this.craftingSystem = new CraftingSystem(inventory, itemRegistry, CraftingRecipes.basic());
        this.selectedRecipeIndex = 0;
        this.cursorStack = null;
        this.inventoryOpen = false;
        this.selectedSlotIndex = 0;
        this.equippedSlotIndex = -1;
    }

    public void updateInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            toggleInventory();
        }
        int hotbarKeySlot = pollHotbarNumberKey();
        if (hotbarKeySlot != -1) {
            selectedSlotIndex = hotbarKeySlot;
            updateEquippedFromSelection();
        }
    }

    public void toggleInventory() {
        inventoryOpen = !inventoryOpen;
        if (!inventoryOpen && selectedSlotIndex >= Inventory.HOTBAR_SLOTS) {
            selectedSlotIndex %= Inventory.HOTBAR_SLOTS;
        }
        updateEquippedFromSelection();
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
        updateEquippedFromSelection();
    }

    public void pickUpItemsAtPlayer(Player player) {
        int playerX = player.getGridX();
        int playerY = player.getGridY();
        // operate on a copy to avoid concurrent modification on the store
        List<GroundItem> snapshot = groundStore.getGroundItems();
        for (GroundItem groundItem : snapshot) {
            if (!groundItem.isAt(playerX, playerY)) {
                continue;
            }
            if (groundItem.isBundle()) {
                List<ItemStack> leftovers = new ArrayList<>();
                for (ItemStack item : groundItem.getBundledItems()) {
                    ItemStack remaining = mergeIntoCursor(item);
                    if (remaining != null) {
                        remaining = inventory.add(remaining, itemRegistry);
                    }
                    if (remaining != null) {
                        leftovers.add(remaining);
                    }
                }

                ItemStack pouchRemain = mergeIntoCursor(groundItem.getStack());
                if (pouchRemain != null) {
                    pouchRemain = inventory.add(pouchRemain, itemRegistry);
                }

                // remove original bundle
                groundStore.removeGroundItem(groundItem.id());

                if (!leftovers.isEmpty() || pouchRemain != null) {
                    ItemStack pouchStack = pouchRemain != null ? pouchRemain : groundItem.getStack();
                    List<ItemStack> remainingContents = leftovers.isEmpty() ? List.of() : leftovers;
                    groundStore.addGroundBundle(playerX, playerY, pouchStack, remainingContents);
                }
            } else {
                ItemStack stack = groundItem.getStack();
                ItemStack remaining = mergeIntoCursor(stack);
                if (remaining != null) {
                    remaining = inventory.add(remaining, itemRegistry);
                }
                if (remaining == null) {
                    groundStore.removeGroundItem(groundItem.id());
                } else if (remaining.count() != stack.count()) {
                    groundStore.upsertGroundItem(groundItem.id(), groundItem.getGridX(), groundItem.getGridY(), remaining);
                }
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
            updateEquippedFromSelection();
            return;
        }

        if (slotStack == null) {
            inventory.set(slotIndex, cursorStack);
            cursorStack = null;
            selectedSlotIndex = slotIndex;
            updateEquippedFromSelection();
            return;
        }

        if (slotStack.canStackWith(cursorStack)) {
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
                updateEquippedFromSelection();
                return;
            }
        }

        inventory.set(slotIndex, cursorStack);
        cursorStack = slotStack;
        selectedSlotIndex = slotIndex;
        updateEquippedFromSelection();
    }

    public void addGroundStack(int gridX, int gridY, ItemStack stack) {
        if (stack == null) {
            return;
        }
        if (!grid.isInside(gridX, gridY)) {
            return;
        }
        groundStore.addGroundStack(gridX, gridY, stack);
    }

    public void addGroundBundle(int gridX, int gridY, ItemStack pouchStack, List<ItemStack> bundled) {
        groundStore.addGroundBundle(gridX, gridY, pouchStack, bundled);
    }

    public List<ItemStack> drainAllItems() {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if (stack != null) {
                result.add(stack);
                inventory.set(i, null);
            }
        }
        if (cursorStack != null) {
            result.add(cursorStack);
            cursorStack = null;
        }
        // clear all ground items via the shared store (it will remove from entity world)
        groundStore.clear();
        selectedSlotIndex = 0;
        updateEquippedFromSelection();
        inventoryOpen = false;
        return result;
    }

    public void forceCloseInventory() {
        inventoryOpen = false;
    }

    public CraftingSystem getCraftingSystem() {
        return craftingSystem;
    }

    public void selectRecipe(int index) {
        if (index < 0 || index >= craftingSystem.getRecipes().size()) {
            return;
        }
        selectedRecipeIndex = index;
    }

    public int getSelectedRecipeIndex() {
        int max = craftingSystem.getRecipes().size();
        if (max == 0) {
            return -1;
        }
        if (selectedRecipeIndex < 0 || selectedRecipeIndex >= max) {
            selectedRecipeIndex = 0;
        }
        return selectedRecipeIndex;
    }

    public CraftingSystem.CraftResult craftSelectedRecipe() {
        int index = getSelectedRecipeIndex();
        CraftingSystem.CraftResult result = craftingSystem.craft(index);
        // Crafted items may change what is equipped or selected, so recompute.
        updateEquippedFromSelection();
        return result;
    }

    public boolean isInventoryOpen() {
        return inventoryOpen;
    }

    public int getSelectedSlotIndex() {
        return selectedSlotIndex;
    }

    public int getEquippedSlotIndex() {
        return equippedSlotIndex;
    }

    public ItemStack getEquippedStack() {
        if (equippedSlotIndex < 0 || equippedSlotIndex >= Inventory.TOTAL_SLOTS) {
            return null;
        }
        return inventory.get(equippedSlotIndex);
    }

    /**
     * Decrease durability of the currently equipped item (if any) and remove it when it breaks.
     */
    public void damageEquippedItem(int amount) {
        if (amount <= 0 || equippedSlotIndex < 0 || equippedSlotIndex >= Inventory.TOTAL_SLOTS) {
            return;
        }
        ItemStack stack = inventory.get(equippedSlotIndex);
        if (stack == null) {
            updateEquippedFromSelection();
            return;
        }
        int maxDurability = itemRegistry.maxDurability(stack.itemId());
        if (maxDurability <= 0) {
            return; // Item has no durability; ignore.
        }
        int newDurability = Math.max(0, stack.durability() - amount);
        if (newDurability == stack.durability()) {
            return;
        }
        if (newDurability <= 0) {
            inventory.set(equippedSlotIndex, null); // tool broke
        } else {
            inventory.set(equippedSlotIndex, stack.withDurability(newDurability));
        }
        updateEquippedFromSelection();
    }

    public ItemStack getCursorStack() {
        return cursorStack;
    }

    public List<GroundItem> getGroundItems() {
        return groundStore.getGroundItems();
    }

    public void removeGroundItem(int id) {
        groundStore.removeGroundItem(id);
    }

    public void upsertGroundItem(int id, int x, int y, ItemStack stack) {
        groundStore.upsertGroundItem(id, x, y, stack);
    }

    public void clearGroundItems() {
        groundStore.clear();
    }

    private void updateEquippedFromSelection() {
        if (selectedSlotIndex < 0 || selectedSlotIndex >= Inventory.HOTBAR_SLOTS) {
            equippedSlotIndex = -1;
            return;
        }
        ItemStack stack = inventory.get(selectedSlotIndex);
        if (stack == null || !itemRegistry.isEquippable(stack.itemId())) {
            equippedSlotIndex = -1;
            return;
        }
        equippedSlotIndex = selectedSlotIndex;
    }

    private ItemStack mergeIntoCursor(ItemStack stack) {
        if (cursorStack == null) {
            return stack;
        }
        if (!cursorStack.canStackWith(stack)) {
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
