package com.droiddungeon.control;

import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.debug.DebugTextBuilder;
import com.droiddungeon.input.InputFrame;
import com.droiddungeon.runtime.GameContext;
import com.droiddungeon.runtime.GameUpdateResult;
import com.droiddungeon.render.WorldRenderer;
import com.droiddungeon.render.effects.ScreenEffectRenderer;
import com.droiddungeon.render.effects.VignetteEffect;
import com.droiddungeon.ui.DebugOverlay;
import com.droiddungeon.ui.HudRenderer;
import com.droiddungeon.ui.MinimapRenderer;
import com.droiddungeon.ui.MapOverlay;

/**
 * Coordinates rendering of world, HUD, debug, and map overlay.
 */
public final class GameRenderCoordinator {
    private final WorldRenderer worldRenderer;
    private final HudRenderer hudRenderer;
    private final MinimapRenderer minimapRenderer;
    private final DebugOverlay debugOverlay;
    private final ScreenEffectRenderer screenEffectRenderer;

    public GameRenderCoordinator() {
        worldRenderer = new WorldRenderer();
        hudRenderer = new HudRenderer();
        minimapRenderer = new MinimapRenderer();
        debugOverlay = new DebugOverlay();
        screenEffectRenderer = new ScreenEffectRenderer();
        screenEffectRenderer.addEffect(new VignetteEffect());
    }

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

        // Post-process style screen effects should sit between world and UI so the HUD stays crisp.
        screenEffectRenderer.render(uiViewport, delta);

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
        // keep minimap fog in sync even when full map is closed
        mapOverlay.revealAround(ctx.player(), 10);
        minimapRenderer.render(
                uiViewport,
                ctx.grid(),
                ctx.player(),
                ctx.companionSystem().getRenderX(),
                ctx.companionSystem().getRenderY(),
                ctx.enemySystem().getEnemies(),
                mapOverlay.getMarkers(),
                mapOverlay.getTracked(),
                (x, y) -> mapOverlay.isExplored(x, y)
        );
        debugOverlay.render(uiViewport, debugText);

        if (mapOpen) {
            mapOverlay.update(delta, uiViewport, ctx.grid());
            mapOverlay.render(
                    uiViewport,
                    ctx.grid(),
                    ctx.player(),
                    ctx.companionSystem().getRenderX(),
                    ctx.companionSystem().getRenderY(),
                    ctx.enemySystem().getEnemies()
            );
        }

        boolean restartHovered = false;
        if (dead) {
            restartHovered = isRestartHovered(uiViewport);
            hudRenderer.renderDeathOverlay(uiViewport, restartHovered);
        }
        return restartHovered;
    }

    public void resize(int width, int height) {
        screenEffectRenderer.resize(width, height);
    }

    public void dispose() {
        worldRenderer.dispose();
        hudRenderer.dispose();
        minimapRenderer.dispose();
        debugOverlay.dispose();
        screenEffectRenderer.dispose();
    }

    public HudRenderer hudRenderer() {
        return hudRenderer;
    }

    public DebugOverlay debugOverlay() {
        return debugOverlay;
    }

    public com.badlogic.gdx.math.Rectangle minimapBounds() {
        return minimapRenderer.getLastBounds();
    }

    private boolean isRestartHovered(Viewport uiViewport) {
        var rect = hudRenderer.getRestartButtonBounds(uiViewport);
        var world = uiViewport.unproject(new com.badlogic.gdx.math.Vector2(com.badlogic.gdx.Gdx.input.getX(), com.badlogic.gdx.Gdx.input.getY()));
        return rect.contains(world);
    }
}
