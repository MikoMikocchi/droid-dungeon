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
        boolean debugToggleRequested
){}
