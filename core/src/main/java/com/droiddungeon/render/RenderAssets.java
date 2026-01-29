package com.droiddungeon.render;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

public final class RenderAssets {
    private static final Map<Integer, BitmapFont> fontsBySize = new HashMap<>();
    private static Texture whiteTexture;
    private static TextureRegion whiteRegion;
    private static Texture playerTexture;
    private static TextureRegion playerRegion;
    private static Texture dottyTexture;
    private static TextureRegion dottyRegion;
    private static Texture catsterTexture;
    private static TextureRegion catsterRegion;
    private static Texture floorTexture;
    private static TextureRegion floorRegion;
    private static Texture wallAutoTexture;
    private static TextureRegion[] wallAutoRegions;

    private RenderAssets() {
    }

    public static BitmapFont font(int size) {
        BitmapFont font = fontsBySize.get(size);
        if (font != null) {
            return font;
        }
        font = loadFont(size);
        fontsBySize.put(size, font);
        return font;
    }

    public static TextureRegion whiteRegion() {
        if (whiteRegion == null) {
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.WHITE);
            pixmap.fill();
            whiteTexture = new Texture(pixmap);
            whiteTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            whiteRegion = new TextureRegion(whiteTexture);
            pixmap.dispose();
        }
        return whiteRegion;
    }

    public static void dispose() {
        for (BitmapFont font : fontsBySize.values()) {
            font.dispose();
        }
        fontsBySize.clear();
        if (whiteTexture != null) {
            whiteTexture.dispose();
            whiteTexture = null;
            whiteRegion = null;
        }
        if (playerTexture != null) {
            playerTexture.dispose();
            playerTexture = null;
            playerRegion = null;
        }
        if (dottyTexture != null) {
            dottyTexture.dispose();
            dottyTexture = null;
            dottyRegion = null;
        }
        if (catsterTexture != null) {
            catsterTexture.dispose();
            catsterTexture = null;
            catsterRegion = null;
        }
        if (floorTexture != null) {
            floorTexture.dispose();
            floorTexture = null;
            floorRegion = null;
        }
        if (wallAutoTexture != null) {
            wallAutoTexture.dispose();
            wallAutoTexture = null;
            wallAutoRegions = null;
        }
    }

    public static TextureRegion playerRegion() {
        if (playerRegion == null) {
            playerTexture = new Texture(Gdx.files.internal("textures/entities/humanoids/Player.png"));
            playerTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            playerRegion = new TextureRegion(playerTexture);
        }
        return playerRegion;
    }

    public static TextureRegion dottyRegion() {
        if (dottyRegion == null) {
            dottyTexture = new Texture(Gdx.files.internal("textures/entities/humanoids/Dotty.png"));
            dottyTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            dottyRegion = new TextureRegion(dottyTexture);
        }
        return dottyRegion;
    }

    public static TextureRegion catsterRegion() {
        if (catsterRegion == null) {
            catsterTexture = new Texture(Gdx.files.internal("textures/entities/hostile/catster.png"));
            catsterTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            catsterRegion = new TextureRegion(catsterTexture);
        }
        return catsterRegion;
    }

    public static TextureRegion floorRegion() {
        if (floorRegion == null) {
            floorTexture = new Texture(Gdx.files.internal("textures/tiles/floor_base.png"));
            floorTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            floorRegion = new TextureRegion(floorTexture);
        }
        return floorRegion;
    }

    /**
     * Returns a 16-entry autotile set laid out in the order of a 4-bit mask:
     * bit 0 = north, bit 1 = east, bit 2 = south, bit 3 = west.
     */
    public static TextureRegion[] wallAutoTiles() {
        if (wallAutoRegions == null) {
            wallAutoTexture = new Texture(Gdx.files.internal("textures/tiles/wall_autotile.png"));
            wallAutoTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            TextureRegion[][] split = TextureRegion.split(wallAutoTexture, 32, 32);
            wallAutoRegions = new TextureRegion[16];
            int idx = 0;
            for (int row = 0; row < split.length && idx < 16; row++) {
                for (int col = 0; col < split[row].length && idx < 16; col++) {
                    wallAutoRegions[idx++] = split[row][col];
                }
            }
        }
        return wallAutoRegions;
    }

    private static BitmapFont loadFont(int size) {
        FreeTypeFontGenerator generator = null;
        try {
            generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/minecraft_font.ttf"));
            FreeTypeFontParameter params = new FreeTypeFontParameter();
            params.size = size;
            params.borderWidth = 0.9f;
            params.borderColor = new Color(0f, 0f, 0f, 0.6f);
            params.minFilter = TextureFilter.Nearest;
            params.magFilter = TextureFilter.Nearest;
            params.color = Color.WHITE;
            BitmapFont font = generator.generateFont(params);
            font.setUseIntegerPositions(true);
            font.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            return font;
        } catch (Exception e) {
            Gdx.app.error("RenderAssets", "Failed to load custom font, falling back to default", e);
            BitmapFont fallback = new BitmapFont();
            fallback.setUseIntegerPositions(true);
            fallback.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            return fallback;
        } finally {
            if (generator != null) {
                generator.dispose();
            }
        }
    }
}
