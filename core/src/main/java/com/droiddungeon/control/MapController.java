package com.droiddungeon.control;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.input.InputFrame;
import com.droiddungeon.runtime.GameContext;
import com.droiddungeon.ui.DebugOverlay;
import com.droiddungeon.ui.MapOverlay;

/**
 * Manages map overlay toggling and minimap double-click behavior.
 */
public final class MapController {
    private long lastMinimapClickMs;

    public boolean update(
            InputFrame input,
            MapOverlay mapOverlay,
            DebugOverlay debugOverlay,
            Viewport uiViewport,
            GameContext ctx
    ) {
        if (input.mapToggleRequested()) {
            mapOverlay.toggle(ctx.grid(), ctx.player());
        }
        if (mapOverlay.isOpen() && input.mapCloseRequested()) {
            mapOverlay.close();
        }

        if (!mapOverlay.isOpen() && Gdx.input.justTouched()) {
            Rectangle bounds = debugOverlay.getLastMinimapBounds();
            Vector2 ui = uiViewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
            if (bounds.contains(ui)) {
                long now = TimeUtils.millis();
                if (now - lastMinimapClickMs < 280) {
                    mapOverlay.open(ctx.grid(), ctx.player());
                }
                lastMinimapClickMs = now;
            }
        }
        return mapOverlay.isOpen();
    }
}
