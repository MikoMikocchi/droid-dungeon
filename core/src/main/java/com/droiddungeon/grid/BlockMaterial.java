package com.droiddungeon.grid;

import com.badlogic.gdx.graphics.Color;
import com.droiddungeon.items.ToolType;

/**
 * Solid blocks that sit above a floor tile. Should mirror available TileMaterial entries so
 * floor and block can share a material identity.
 */
public enum BlockMaterial {
    STONE(TileMaterial.STONE, 12f, ToolType.PICKAXE, null, 0, false),
    DIRT(TileMaterial.DIRT, 6f, ToolType.SHOVEL, null, 0, false),
    WOOD(TileMaterial.WOOD, 6f, ToolType.AXE, null, 0, false),
    GRAVEL(TileMaterial.GRAVEL, 5f, ToolType.SHOVEL, null, 0, false),
    PLANKS(TileMaterial.PLANKS, 5f, ToolType.AXE, null, 0, false);

    private final TileMaterial floorMaterial;
    private final float maxHealth;
    private final ToolType preferredTool;
    private final String dropItemId;
    private final int dropCount;
    private final boolean transparent;

    BlockMaterial(TileMaterial floorMaterial,
                  float maxHealth,
                  ToolType preferredTool,
                  String dropItemId,
                  int dropCount,
                  boolean transparent) {
        this.floorMaterial = floorMaterial;
        this.maxHealth = Math.max(1f, maxHealth);
        this.preferredTool = preferredTool != null ? preferredTool : ToolType.NONE;
        this.dropItemId = dropItemId;
        this.dropCount = Math.max(0, dropCount);
        this.transparent = transparent;
    }

    public TileMaterial floorMaterial() {
        return floorMaterial;
    }

    public Color colorForParity(int parity) {
        return floorMaterial.colorForParity(parity);
    }

    public float maxHealth() {
        return maxHealth;
    }

    public ToolType preferredTool() {
        return preferredTool;
    }

    public float efficiencyFor(ToolType toolType) {
        if (toolType == null || toolType == ToolType.NONE) {
            return 0.25f;
        }
        if (toolType == preferredTool) {
            return 1f;
        }
        // Wood can be chopped a bit faster by pickaxe than bare hands, etc.
        return 0.5f;
    }

    public String dropItemId() {
        return dropItemId;
    }

    public int dropCount() {
        return dropCount;
    }

    public boolean transparent() {
        return transparent;
    }
}
