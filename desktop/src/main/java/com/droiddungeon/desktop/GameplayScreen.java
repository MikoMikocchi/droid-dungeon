package com.droiddungeon.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.droiddungeon.config.GameConfig;
import com.droiddungeon.runtime.GameRuntime;
import com.droiddungeon.runtime.NetworkSnapshotBuffer;
import com.droiddungeon.save.SaveGame;
import java.io.IOException;

final class GameplayScreen extends ScreenAdapter {
  private final GameApp game;
  private final SaveManager saves;
  private final String worldName;
  private final SaveGame initialSave;
  private GameRuntime runtime;

  GameplayScreen(GameApp game, SaveManager saves, String worldName, SaveGame initialSave) {
    this.game = game;
    this.saves = saves;
    this.worldName = worldName;
    this.initialSave = initialSave;
    initRuntime();
  }

  private void initRuntime() {
    runtime =
        new GameRuntime(
            GameConfig.defaults(),
            new GdxTextureLoader(),
            new DesktopAssets(),
            null,
            new NetworkSnapshotBuffer(),
            false);
    runtime.setPendingSave(initialSave);
    runtime.create();
    runtime.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
  }

  @Override
  public void render(float delta) {
    if (Gdx.input.isKeyJustPressed(Input.Keys.F10)) {
      exitToMenu();
      return;
    }
    runtime.render();
  }

  private void exitToMenu() {
    saveAndDispose();
    game.setScreen(new WorldListScreen(game, saves));
  }

  @Override
  public void resize(int width, int height) {
    runtime.resize(width, height);
  }

  @Override
  public void dispose() {
    saveAndDispose();
  }

  private void saveAndDispose() {
    if (runtime != null) {
      SaveGame snapshot = runtime.snapshotSave(worldName);
      try {
        if (snapshot != null) {
          saves.save(snapshot);
        }
      } catch (IOException e) {
        // log to stderr; no UI here
        System.err.println("Failed to save world " + worldName + ": " + e.getMessage());
      }
      runtime.dispose();
      runtime = null;
    }
  }
}
