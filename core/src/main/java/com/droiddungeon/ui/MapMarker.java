package com.droiddungeon.ui;

/**
 * Marker shown on the world map overlay.
 */
public final class MapMarker {
    public enum Type { CUSTOM, DEATH }

    private final int id;
    private int x;
    private int y;
    private String label;
    private final Type type;
    private boolean tracked;

    public MapMarker(int id, int x, int y, String label, Type type) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.label = label == null || label.isBlank() ? defaultLabel(type) : label;
        this.type = type;
        this.tracked = false;
    }

    private static String defaultLabel(Type type) {
        return type == Type.DEATH ? "Death" : "Marker";
    }

    public int id() {
        return id;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public Type type() {
        return type;
    }

    public String label() {
        return label;
    }

    public void setLabel(String label) {
        if (label != null && !label.isBlank()) {
            this.label = label;
        }
    }

    public void moveTo(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean tracked() {
        return tracked;
    }

    public void setTracked(boolean tracked) {
        this.tracked = tracked;
    }
}
