package com.droiddungeon.control;

import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.debug.DebugTextBuilder;
import com.droiddungeon.input.InputFrame;
import com.droiddungeon.runtime.GameContext;
import com.droiddungeon.runtime.GameUpdateResult;
import com.droiddungeon.render.WorldRenderer;
import com.droiddungeon.ui.DebugOverlay;
import com.droiddungeon.ui.HudRenderer;
import com.droiddungeon.ui.MapOverlay;

/**
 * Coordinates rendering of world, HUD, debug, and map overlay.
 */
public final class GameRenderCoordinator {
    private final WorldRenderer worldRenderer = new WorldRenderer();
    private final HudRenderer hudRenderer = new HudRenderer();
    private final DebugOverlay debugOverlay = new DebugOverlay();

    public boolean render(
            Viewport worldViewport,
            Viewport uiViewport,
            GameContext ctx,
            GameUpdateResult update,
            MapOverlay mapOverlay,
            DebugTextBuilder debugTextBuilder,
            InputFrame input,
            float delta,
            boolean dead,
            boolean mapOpen
    ) {
        worldRenderer.render(
                worldViewport,
                update.gridOriginX(),
                update.gridOriginY(),
                ctx.grid(),
                ctx.player(),
                update.weaponState(),
                ctx.inventorySystem().getGroundItems(),
                ctx.itemRegistry(),
                ctx.enemySystem().getEnemies(),
                mapOverlay.getTracked(),
                ctx.companionSystem().getRenderX(),
                ctx.companionSystem().getRenderY()
        );

        String debugText = debugTextBuilder.build(
                worldViewport,
                ctx.grid(),
                ctx.player(),
                ctx.companionSystem(),
                ctx.enemySystem(),
                ctx.inventorySystem(),
                ctx.itemRegistry(),
                ctx.playerStats(),
                update.gridOriginX(),
                update.gridOriginY()
        );

        hudRenderer.render(
                uiViewport,
                ctx.inventory(),
                ctx.itemRegistry(),
                ctx.inventorySystem().getCursorStack(),
                ctx.inventorySystem().isInventoryOpen(),
                ctx.inventorySystem().getSelectedSlotIndex(),
                input.hoveredSlot(),
                delta,
                ctx.playerStats()
        );
        debugOverlay.render(
                uiViewport,
                ctx.grid(),
                ctx.player(),
                ctx.companionSystem().getRenderX(),
                ctx.companionSystem().getRenderY(),
                debugText,
                mapOverlay.getMarkers(),
                mapOverlay.getTracked()
        );

        if (mapOpen) {
            mapOverlay.update(delta, uiViewport, ctx.grid());
            mapOverlay.render(uiViewport, ctx.grid(), ctx.player(), ctx.companionSystem().getRenderX(), ctx.companionSystem().getRenderY());
        }

        boolean restartHovered = false;
        if (dead) {
            restartHovered = isRestartHovered(uiViewport);
            hudRenderer.renderDeathOverlay(uiViewport, restartHovered);
        }
        return restartHovered;
    }

    public void dispose() {
        worldRenderer.dispose();
        hudRenderer.dispose();
        debugOverlay.dispose();
    }

    public HudRenderer hudRenderer() {
        return hudRenderer;
    }

    public DebugOverlay debugOverlay() {
        return debugOverlay;
    }

    private boolean isRestartHovered(Viewport uiViewport) {
        var rect = hudRenderer.getRestartButtonBounds(uiViewport);
        var world = uiViewport.unproject(new com.badlogic.gdx.math.Vector2(com.badlogic.gdx.Gdx.input.getX(), com.badlogic.gdx.Gdx.input.getY()));
        return rect.contains(world);
    }
}
