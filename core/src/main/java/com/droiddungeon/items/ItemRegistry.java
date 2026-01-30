package com.droiddungeon.items;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.droiddungeon.inventory.Inventory;
import com.droiddungeon.inventory.ItemStackSizer;

public final class ItemRegistry implements ItemStackSizer, AutoCloseable {
    private final Map<String, ItemDefinition> definitions = new HashMap<>();
    private final List<Texture> ownedTextures = new ArrayList<>();

    private final int fallbackMaxStack;

    public ItemRegistry() {
        this(Inventory.DEFAULT_MAX_STACK);
    }

    public ItemRegistry(int fallbackMaxStack) {
        this.fallbackMaxStack = fallbackMaxStack;
    }

    public static ItemRegistry load(String path) {
        ItemRegistry registry = new ItemRegistry();
        registry.loadFromFile(path);
        return registry;
    }

    public void loadFromFile(String path) {
        FileHandle handle = Gdx.files.internal(path);
        if (!handle.exists()) {
            throw new IllegalStateException("Items file not found: " + path);
        }
        String[] lines = handle.readString(StandardCharsets.UTF_8.name()).split("\\R");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split(";");
            if (parts.length < 4) {
                Gdx.app.error("ItemRegistry", "Invalid item line (expected id;name;maxStack;texturePath[;equippable;maxDurability;toolType]): " + line);
                continue;
            }

            String id = parts[0].trim();
            String displayName = parts[1].trim();
            int maxStack;
            try {
                maxStack = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException ex) {
                Gdx.app.error("ItemRegistry", "Invalid max stack for item " + id + ": " + parts[2]);
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
                    Gdx.app.error("ItemRegistry", "Invalid max durability for item " + id + ": " + parts[5]);
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
                    Gdx.app.error("ItemRegistry", "Invalid tool type for item " + id + ": " + parts[6]);
                    toolType = ToolType.NONE;
                }
            }
            if (definitions.containsKey(id)) {
                Gdx.app.log("ItemRegistry", "Skipping duplicate item id: " + id);
                continue;
            }
            Texture texture = new Texture(Gdx.files.internal(texturePath));
            texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            ownedTextures.add(texture);

            TextureRegion region = new TextureRegion(texture);
            definitions.put(id, new ItemDefinition(id, displayName, maxStack, region, equippable, maxDurability, toolType));
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
        for (Texture texture : ownedTextures) {
            texture.dispose();
        }
        ownedTextures.clear();
    }

    @Override
    public void close() {
        dispose();
    }
}
