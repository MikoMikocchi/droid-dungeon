package com.droiddungeon.items;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.inventory.ItemStackSizer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ItemRegistry implements ItemStackSizer, AutoCloseable {
  private final Map<String, ItemDefinition> definitions = new HashMap<>();
  private final TextureLoader textureLoader;

  private final int fallbackMaxStack;

  public ItemRegistry() {
    this(null, Inventory.DEFAULT_MAX_STACK);
  }

  public ItemRegistry(TextureLoader textureLoader, int fallbackMaxStack) {
    this.textureLoader = textureLoader;
    this.fallbackMaxStack = fallbackMaxStack;
  }

  public static ItemRegistry load(String path) {
    return loadWithLoader(path, null);
  }

  public static ItemRegistry loadWithLoader(String path, TextureLoader loader) {
    ItemRegistry registry = new ItemRegistry(loader, Inventory.DEFAULT_MAX_STACK);
    registry.loadFromFile(path, loader != null);
    return registry;
  }

  /**
   * Data-only loading for headless/server mode: textures are not read, icon = empty
   * TextureRegion().
   */
  public static ItemRegistry loadDataOnly(Path path) {
    List<String> lines;
    try {
      lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read items file: " + path, e);
    }
    return loadDataOnly(lines);
  }

  public static ItemRegistry loadDataOnly(List<String> lines) {
    ItemRegistry registry = new ItemRegistry(null, Inventory.DEFAULT_MAX_STACK);
    registry.loadFromLines(lines, false);
    return registry;
  }

  public void loadFromFile(String path, boolean loadTextures) {
    String content = readAll(path);
    String[] lines = content.split("\\R");
    loadFromLines(Arrays.asList(lines), loadTextures);
  }

  public void loadFromLines(List<String> lines, boolean loadTextures) {
    for (String rawLine : lines) {
      String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      String[] parts = line.split(";");
      if (parts.length < 4) {
        logError(
            "Invalid item line (expected id;name;maxStack;texturePath[;equippable;maxDurability;toolType]): "
                + line);
        continue;
      }

      String id = parts[0].trim();
      String displayName = parts[1].trim();
      int maxStack;
      try {
        maxStack = Integer.parseInt(parts[2].trim());
      } catch (NumberFormatException ex) {
        logError("Invalid max stack for item " + id + ": " + parts[2]);
        continue;
      }
      String texturePath = parts[3].trim();
      boolean equippable = false;
      int maxDurability = 0;
      ToolType toolType = ToolType.NONE;
      if (parts.length >= 5) {
        equippable = Boolean.parseBoolean(parts[4].trim());
      }
      if (parts.length >= 6) {
        try {
          maxDurability = Integer.parseInt(parts[5].trim());
        } catch (NumberFormatException ex) {
          logError("Invalid max durability for item " + id + ": " + parts[5]);
          maxDurability = 0;
        }
        if (maxDurability < 0) {
          maxDurability = 0;
        }
      }
      if (parts.length >= 7) {
        try {
          toolType = ToolType.valueOf(parts[6].trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
          logError("Invalid tool type for item " + id + ": " + parts[6]);
          toolType = ToolType.NONE;
        }
      }
      if (definitions.containsKey(id)) {
        logInfo("Skipping duplicate item id: " + id);
        continue;
      }
      TextureRegion region =
          (loadTextures && textureLoader != null)
              ? textureLoader.load(texturePath)
              : new TextureRegion(); // placeholder, no GL calls
      definitions.put(
          id,
          new ItemDefinition(
              id, displayName, maxStack, region, equippable, maxDurability, toolType));
    }
  }

  public ItemDefinition get(String id) {
    return definitions.get(id);
  }

  public boolean isEquippable(String id) {
    ItemDefinition def = definitions.get(id);
    return def != null && def.equippable();
  }

  public int maxDurability(String id) {
    ItemDefinition def = definitions.get(id);
    return def != null ? def.maxDurability() : 0;
  }

  public ToolType getToolType(String id) {
    ItemDefinition def = definitions.get(id);
    return def != null ? def.toolType() : ToolType.NONE;
  }

  @Override
  public int maxStackSize(String itemId) {
    ItemDefinition definition = definitions.get(itemId);
    if (definition != null) {
      return definition.maxStackSize();
    }
    return fallbackMaxStack;
  }

  public List<ItemDefinition> all() {
    return new ArrayList<>(definitions.values());
  }

  public void dispose() {
    if (textureLoader != null) {
      textureLoader.close();
    }
  }

  @Override
  public void close() {
    dispose();
  }

  private void logError(String message) {
    System.err.println("ItemRegistry ERROR: " + message);
  }

  private void logInfo(String message) {
    System.out.println("ItemRegistry: " + message);
  }

  private String readAll(String path) {
    try {
      return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Items file not found: " + path, e);
    }
  }
}
