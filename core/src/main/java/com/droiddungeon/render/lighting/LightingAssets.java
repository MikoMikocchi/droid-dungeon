package com.droiddungeon.render.lighting;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

/**
 * Manages lighting-related textures and assets.
 * Loads textures from assets/textures/lighting/ directory.
 */
public final class LightingAssets implements Disposable {
    private static LightingAssets instance;

    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<String, TextureRegion> regions = new HashMap<>();

    public static final String GRADIENT_SOFT_64 = "light_gradient_64_soft";
    public static final String GRADIENT_SOFT_128 = "light_gradient_128_soft";
    public static final String GRADIENT_SOFT_256 = "light_gradient_256_soft";
    public static final String GRADIENT_MEDIUM_64 = "light_gradient_64_medium";
    public static final String GRADIENT_MEDIUM_128 = "light_gradient_128_medium";
    public static final String GRADIENT_MEDIUM_256 = "light_gradient_256_medium";
    public static final String GRADIENT_FOCUSED_64 = "light_gradient_64_focused";
    public static final String GRADIENT_FOCUSED_128 = "light_gradient_128_focused";
    public static final String GRADIENT_FOCUSED_256 = "light_gradient_256_focused";
    public static final String GRADIENT_WHITE = "light_gradient_white";
    public static final String NOISE_A = "noise_a";
    public static final String NOISE_B = "noise_b";
    public static final String NOISE_C = "noise_c";
    public static final String DITHER = "dither";
    public static final String SOFT_EDGE_64 = "soft_edge_64";
    public static final String SOFT_EDGE_128 = "soft_edge_128";
    public static final String SHADOW_PENUMBRA = "shadow_penumbra";

    private static final String BASE_PATH = "textures/lighting/";

    private LightingAssets() {
        // Private constructor for singleton
    }

    /**
     * Get the singleton instance, creating it if necessary.
     */
    public static LightingAssets getInstance() {
        if (instance == null) {
            instance = new LightingAssets();
        }
        return instance;
    }

    /**
     * Load a texture from the lighting assets directory.
     */
    public Texture getTexture(String name) {
        Texture tex = textures.get(name);
        if (tex != null) {
            return tex;
        }

        String path = BASE_PATH + name + ".png";
        if (!Gdx.files.internal(path).exists()) {
            Gdx.app.log("LightingAssets", "Texture not found: " + path);
            return null;
        }

        tex = new Texture(Gdx.files.internal(path));
        tex.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        textures.put(name, tex);
        return tex;
    }

    /**
     * Get a texture region for a lighting asset.
     */
    public TextureRegion getRegion(String name) {
        TextureRegion region = regions.get(name);
        if (region != null) {
            return region;
        }

        Texture tex = getTexture(name);
        if (tex == null) {
            return null;
        }

        region = new TextureRegion(tex);
        regions.put(name, region);
        return region;
    }

    /**
     * Get the appropriate gradient texture for a given light radius.
     * Chooses size based on radius for optimal quality/performance.
     */
    public TextureRegion getGradientForRadius(float radius, String quality) {
        int size;
        if (radius < 64) {
            size = 64;
        } else if (radius < 128) {
            size = 128;
        } else {
            size = 256;
        }

        String name = "light_gradient_" + size + "_" + quality;
        TextureRegion region = getRegion(name);

        // Fall back to white gradient if specific one doesn't exist
        if (region == null) {
            region = getRegion(GRADIENT_WHITE);
        }

        return region;
    }

    /**
     * Get a random torch sprite for variety.
     */
    public TextureRegion getRandomTorch(java.util.Random random) {
        int variant = random.nextInt(3) + 1;
        return getRegion("torch_" + variant);
    }

    /**
     * Preload commonly used textures.
     */
    public void preload() {
        getTexture(GRADIENT_SOFT_256);
        getTexture(GRADIENT_MEDIUM_256);
        getTexture(GRADIENT_WHITE);

        getTexture(NOISE_A);
    }

    /**
     * Check if generated assets exist.
     */
    public boolean assetsExist() {
        return Gdx.files.internal(BASE_PATH + GRADIENT_WHITE + ".png").exists();
    }

    @Override
    public void dispose() {
        for (Texture tex : textures.values()) {
            tex.dispose();
        }
        textures.clear();
        regions.clear();
        instance = null;
    }

    /**
     * Dispose the singleton instance.
     */
    public static void disposeInstance() {
        if (instance != null) {
            instance.dispose();
        }
    }
}
