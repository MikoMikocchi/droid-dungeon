package com.droiddungeon.grid.room;

import com.droiddungeon.grid.DungeonGenerator;
import java.util.List;

/** Registry of built-in room templates. */
public final class RoomTemplates {
  private RoomTemplates() {}

  public static List<RoomTemplate> defaults() {
    return List.of(
        // Basic rectangle SAFE/DANGER
        new RoomTemplate(
            "safe_rect",
            DungeonGenerator.RoomType.SAFE,
            new IntRange(8, 14),
            new IntRange(8, 14),
            (x, y, w, h, rng) -> new RectShape(x, y, w, h),
            1f),
        new RoomTemplate(
            "danger_rect",
            DungeonGenerator.RoomType.DANGER,
            new IntRange(6, 14),
            new IntRange(6, 14),
            (x, y, w, h, rng) -> new RectShape(x, y, w, h),
            2.5f),
        // Roundish
        new RoomTemplate(
            "danger_ellipse",
            DungeonGenerator.RoomType.DANGER,
            new IntRange(8, 14),
            new IntRange(8, 14),
            (x, y, w, h, rng) -> new EllipseShape(x, y, w, h),
            1.4f),
        // Diamond
        new RoomTemplate(
            "danger_diamond",
            DungeonGenerator.RoomType.DANGER,
            new IntRange(8, 14),
            new IntRange(8, 14),
            (x, y, w, h, rng) -> new DiamondShape(x, y, w, h),
            1.2f),
        // L shaped
        new RoomTemplate(
            "danger_l",
            DungeonGenerator.RoomType.DANGER,
            new IntRange(9, 14),
            new IntRange(9, 14),
            (x, y, w, h, rng) -> new LShape(x, y, w, h, Math.max(3, Math.min(w, h) / 3)),
            0.9f),
        // Gentle safe circle grove
        new RoomTemplate(
            "safe_circle",
            DungeonGenerator.RoomType.SAFE,
            new IntRange(8, 12),
            new IntRange(8, 12),
            (x, y, w, h, rng) -> new EllipseShape(x, y, w, h),
            0.8f));
  }
}
