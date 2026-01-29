package com.droiddungeon.items;

/**
 * Simple helper record describing how an item behaves as a tool.
 */
public record ToolInfo(ToolType type, float efficiencyMultiplier) {
    public static final ToolInfo NONE = new ToolInfo(ToolType.NONE, 0.25f);

    public ToolInfo {
        if (type == null) {
            type = ToolType.NONE;
        }
        if (efficiencyMultiplier <= 0f) {
            efficiencyMultiplier = 0.25f;
        }
    }
}
