package com.droiddungeon.systems;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;

/** Manages world camera tracking player and provides grid origin placement. */
public final class CameraController {
  private final OrthographicCamera camera;
  private final Viewport viewport;
  private final float cameraLerp;
  private final float cameraZoom;

  private float gridOriginX;
  private float gridOriginY;

  public CameraController(
      OrthographicCamera camera, Viewport viewport, float cameraLerp, float cameraZoom) {
    this.camera = camera;
    this.viewport = viewport;
    this.cameraLerp = cameraLerp;
    this.cameraZoom = cameraZoom;
  }

  public void resize(int width, int height) {
    viewport.update(width, height, true);
  }

  public void update(Grid grid, Player player, float deltaSeconds) {
    gridOriginX = 0f;
    gridOriginY = 0f;

    float tileSize = grid.getTileSize();
    float targetX = (player.getRenderX() + 0.5f) * tileSize;
    float targetY = (player.getRenderY() + 0.5f) * tileSize;

    float lerp = 1f - (float) Math.exp(-cameraLerp * deltaSeconds);
    camera.position.x += (targetX - camera.position.x) * lerp;
    camera.position.y += (targetY - camera.position.y) * lerp;
    camera.zoom = cameraZoom;
    camera.update();
  }

  public float getGridOriginX() {
    return gridOriginX;
  }

  public float getGridOriginY() {
    return gridOriginY;
  }
}
