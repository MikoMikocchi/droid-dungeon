package com.droiddungeon.control;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.debug.DebugTextBuilder;
import com.droiddungeon.input.InputFrame;
import com.droiddungeon.render.ClientAssets;
import com.droiddungeon.render.WorldRenderer;
import com.droiddungeon.render.effects.ScreenEffectRenderer;
import com.droiddungeon.render.effects.VignetteEffect;
import com.droiddungeon.render.lighting.LightingSystem;
import com.droiddungeon.runtime.GameContext;
import com.droiddungeon.runtime.GameUpdateResult;
import com.droiddungeon.ui.DebugOverlay;
import com.droiddungeon.ui.HudRenderer;
import com.droiddungeon.ui.MapOverlay;
import com.droiddungeon.ui.MinimapRenderer;

/** Coordinates rendering of world, HUD, debug, and map overlay. */
public final class GameRenderCoordinator {
  private final WorldRenderer worldRenderer;
  private final HudRenderer hudRenderer;
  private final MinimapRenderer minimapRenderer;
  private final DebugOverlay debugOverlay;
  private final ScreenEffectRenderer screenEffectRenderer;
  private LightingSystem lightingSystem;
  private boolean lightingEnabled = true;

  // Toggled with F3
  private boolean debugVisible = false;

  public GameRenderCoordinator(ClientAssets assets) {
    worldRenderer = new WorldRenderer(assets);
    hudRenderer = new HudRenderer(assets);
    minimapRenderer = new MinimapRenderer();
    debugOverlay = new DebugOverlay(assets);
    screenEffectRenderer = new ScreenEffectRenderer();
    // Reduce vignette intensity since we have proper lighting now
    VignetteEffect vignette = new VignetteEffect();
    vignette.setAlpha(0.12f); // Even lighter vignette so edges don't crush blacks
    screenEffectRenderer.addEffect(vignette);
  }

  /** Initialize the lighting system. Call once after game context is ready. */
  public void initLighting(float tileSize, long worldSeed) {
    if (lightingSystem != null) {
      lightingSystem.dispose();
    }
    lightingSystem = new LightingSystem(tileSize, worldSeed);
    // Configure for warm torch-like lighting
    // Ambient is the base darkness level - higher = brighter shadows
    // Push ambient up so rooms never go pitch-black, but keep a warm mood
    lightingSystem.getRenderer().setAmbientColor(0.36f, 0.32f, 0.28f);
    lightingSystem.getRenderer().setAmbientIntensity(1.0f);

    // Slightly boost all light contributions for better visibility
    lightingSystem.getRenderer().setGlobalBrightness(1.25f);
    lightingSystem.getRenderer().setShadowsEnabled(true);
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
      boolean mapOpen) {
    if (lightingSystem != null && lightingEnabled) {
      float tileSize = ctx.grid().getTileSize();
      float playerWorldX = (ctx.player().getRenderX() + 0.5f) * tileSize;
      float playerWorldY = (ctx.player().getRenderY() + 0.5f) * tileSize;

      lightingSystem.update(delta, playerWorldX, playerWorldY);

      OrthographicCamera cam = (OrthographicCamera) worldViewport.getCamera();
      float halfW = cam.viewportWidth * cam.zoom * 0.5f;
      float halfH = cam.viewportHeight * cam.zoom * 0.5f;
      int minX = (int) Math.floor((cam.position.x - halfW) / tileSize) - 2;
      int maxX = (int) Math.ceil((cam.position.x + halfW) / tileSize) + 2;
      int minY = (int) Math.floor((cam.position.y - halfH) / tileSize) - 2;
      int maxY = (int) Math.ceil((cam.position.y + halfH) / tileSize) + 2;
      lightingSystem.generateLightsForArea(ctx.grid(), minX, minY, maxX, maxY);

      lightingSystem.getRenderer().renderLightMap(worldViewport, ctx.grid(), tileSize);
    }

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
        ctx.companionSystem().getRenderY(),
        ctx.miningSystem().getTarget());

    // Apply lighting after world rendering, before screen effects
    if (lightingSystem != null && lightingEnabled) {
      lightingSystem.getRenderer().compositeLightMap();
    }

    // Post-process style screen effects should sit between world and UI so the HUD stays crisp.
    screenEffectRenderer.render(uiViewport, delta);

    int lightCount =
        (lightingSystem != null && lightingEnabled)
            ? lightingSystem.getRenderer().getLights().size()
            : 0;

    // Toggle debug overlay visibility on F3
    if (input.debugToggleRequested()) {
      debugVisible = !debugVisible;
    }

    String debugText = null;
    if (debugVisible) {
      debugText =
          debugTextBuilder.build(
              worldViewport,
              ctx.grid(),
              ctx.player(),
              ctx.companionSystem(),
              ctx.enemySystem(),
              ctx.inventorySystem(),
              ctx.itemRegistry(),
              ctx.playerStats(),
              update.gridOriginX(),
              update.gridOriginY(),
              delta,
              lightCount,
              update.pendingInputsCount(),
              update.lastProcessedTick());
    }

    hudRenderer.render(
        uiViewport,
        ctx.inventory(),
        ctx.itemRegistry(),
        ctx.inventorySystem().getCraftingSystem(),
        ctx.inventorySystem().getCursorStack(),
        ctx.inventorySystem().isInventoryOpen(),
        ctx.inventorySystem().getSelectedSlotIndex(),
        input.hoveredSlot(),
        input.hoveredRecipeIcon(),
        ctx.inventorySystem().getSelectedRecipeIndex(),
        input.craftButtonHovered(),
        delta,
        ctx.playerStats());

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
        (x, y) -> mapOverlay.isExplored(x, y));
    debugOverlay.render(uiViewport, debugText);

    if (mapOpen) {
      mapOverlay.update(delta, uiViewport, ctx.grid());
      mapOverlay.render(
          uiViewport,
          ctx.grid(),
          ctx.player(),
          ctx.companionSystem().getRenderX(),
          ctx.companionSystem().getRenderY(),
          ctx.enemySystem().getEnemies());
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
    if (lightingSystem != null) {
      lightingSystem.getRenderer().resize(width, height);
    }
  }

  public void dispose() {
    worldRenderer.dispose();
    hudRenderer.dispose();
    minimapRenderer.dispose();
    debugOverlay.dispose();
    screenEffectRenderer.dispose();
    if (lightingSystem != null) {
      lightingSystem.dispose();
    }
  }

  public LightingSystem getLightingSystem() {
    return lightingSystem;
  }

  public void setLightingEnabled(boolean enabled) {
    this.lightingEnabled = enabled;
  }

  public boolean isLightingEnabled() {
    return lightingEnabled;
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
    var world =
        uiViewport.unproject(
            new com.badlogic.gdx.math.Vector2(
                com.badlogic.gdx.Gdx.input.getX(), com.badlogic.gdx.Gdx.input.getY()));
    return rect.contains(world);
  }
}
