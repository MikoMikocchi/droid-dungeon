package com.droiddungeon;

import com.badlogic.gdx.ApplicationAdapter;
import com.droiddungeon.config.GameConfig;
import com.droiddungeon.runtime.GameRuntime;

public class DroidDungeonGame extends ApplicationAdapter {
  private GameRuntime runtime;
  private final com.droiddungeon.items.TextureLoader textureLoader;
  private final com.droiddungeon.render.ClientAssets clientAssets;
  private final com.droiddungeon.net.NetworkClientAdapter networkClient;
  private final com.droiddungeon.runtime.NetworkSnapshotBuffer snapshotBuffer;
  private final boolean networkMode;

  public DroidDungeonGame(
      com.droiddungeon.items.TextureLoader textureLoader,
      com.droiddungeon.render.ClientAssets clientAssets,
      com.droiddungeon.net.NetworkClientAdapter networkClient,
      com.droiddungeon.runtime.NetworkSnapshotBuffer buffer,
      boolean networkMode) {
    this.textureLoader = textureLoader;
    this.clientAssets = clientAssets;
    this.networkClient = networkClient;
    this.snapshotBuffer = buffer;
    this.networkMode = networkMode;
  }

  public DroidDungeonGame() {
    this(null, null, null, new com.droiddungeon.runtime.NetworkSnapshotBuffer(), false);
  }

  @Override
  public void create() {
    runtime =
        new GameRuntime(
            GameConfig.defaults(),
            textureLoader,
            clientAssets,
            networkClient,
            snapshotBuffer,
            networkMode);
    runtime.create();
  }

  @Override
  public void resize(int width, int height) {
    if (runtime != null) {
      runtime.resize(width, height);
    }
  }

  @Override
  public void render() {
    if (runtime != null) {
      runtime.render();
    }
  }

  @Override
  public void dispose() {
    if (runtime != null) {
      runtime.dispose();
    }
  }
}
