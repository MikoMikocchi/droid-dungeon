package com.droiddungeon.input;

/**
 * Snapshot of input-derived state for the current frame.
 */
public record InputFrame(
        int slotUnderCursor,
        int hoveredSlot,
        int hoveredRecipeIcon,
        int recipeSelectClickIndex,
        boolean craftButtonHovered,
        boolean craftButtonClicked,
        boolean pointerOnUi,
        boolean slotClicked,
        boolean dropRequested,
        boolean pickUpRequested,
        boolean mapToggleRequested,
        boolean mapCloseRequested,
        boolean restartRequested,
        boolean mineRequested,
        boolean debugToggleRequested,
        MovementIntent movementIntent,
        WeaponInput weaponInput
){
    /**
     * Simplified frame for server/headless mode: UI/click fields are set to safe values.
     */
    public static InputFrame serverFrame(
            MovementIntent movement,
            WeaponInput weapon,
            boolean dropRequested,
            boolean pickUpRequested,
            boolean mineRequested
    ) {
        return new InputFrame(
                -1,
                -1,
                -1,
                -1,
                false,
                false,
                false,
                false,
                dropRequested,
                pickUpRequested,
                false,
                false,
                false,
                mineRequested,
                false,
                movement,
                weapon
        );
    }
}
