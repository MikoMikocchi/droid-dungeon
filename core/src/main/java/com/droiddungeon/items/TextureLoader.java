package com.droiddungeon.items;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Pluggable texture loader so core logic stays data-only; client layer provides the implementation.
 */
public interface TextureLoader extends AutoCloseable {
    TextureRegion load(String path);

    /**
     * Dispose any native resources created by the loader.
     */
    @Override
    void close();
}
