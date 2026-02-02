package com.droiddungeon.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** Abstraction over client-side assets so core logic doesn't load textures/fonts directly. */
public interface ClientAssets extends AutoCloseable {
  BitmapFont font(int size);

  TextureRegion whiteRegion();

  TextureRegion playerRegion();

  TextureRegion dottyRegion();

  TextureRegion catsterRegion();

  TextureRegion floorRegion();

  TextureRegion[] wallAutoTiles();

  @Override
  void close();
}
