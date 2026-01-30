package com.droiddungeon.render.effects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * A small, self contained post-process style overlay drawn in screen space.
 */
public interface ScreenEffect extends Disposable {
    /**
     * Render the effect in screen-space using the provided viewport.
     */
    void render(Viewport viewport, SpriteBatch batch, float deltaSeconds);

    /**
     * Override if the effect keeps buffers or cached data that depends on size.
     */
    default void resize(int width, int height) {
    }

    /**
     * Allow effects to be toggled without removing them from the renderer.
     */
    default boolean isEnabled() {
        return true;
    }
}
