package com.droiddungeon.desktop;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/** Top-level LibGDX Game that switches between menu and gameplay screens. */
public final class GameApp extends Game {
  SpriteBatch batch;
  SaveManager saveManager;

  @Override
  public void create() {
    batch = new SpriteBatch();
    saveManager = new SaveManager();
    setScreen(new MainMenuScreen(this, saveManager));
  }

  @Override
  public void dispose() {
    if (getScreen() != null) {
      getScreen().dispose();
    }
    if (batch != null) batch.dispose();
  }
}
