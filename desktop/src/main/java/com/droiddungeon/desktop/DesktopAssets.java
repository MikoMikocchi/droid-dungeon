package com.droiddungeon.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.droiddungeon.render.ClientAssets;
import java.util.HashMap;
import java.util.Map;

/** GDX-backed asset provider for the desktop client. */
public final class DesktopAssets implements ClientAssets {
  private final Map<Integer, BitmapFont> fontsBySize = new HashMap<>();
  private Texture whiteTexture;
  private TextureRegion whiteRegion;
  private Texture playerTexture;
  private TextureRegion playerRegion;
  private Texture dottyTexture;
  private TextureRegion dottyRegion;
  private Texture catsterTexture;
  private TextureRegion catsterRegion;
  private Texture floorTexture;
  private TextureRegion floorRegion;
  private Texture wallAutoTexture;
  private TextureRegion[] wallAutoRegions;

  @Override
  public BitmapFont font(int size) {
    BitmapFont font = fontsBySize.get(size);
    if (font != null) {
      return font;
    }
    font = loadFont(size);
    fontsBySize.put(size, font);
    return font;
  }

  @Override
  public TextureRegion whiteRegion() {
    if (whiteRegion == null) {
      Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
      pixmap.setColor(Color.WHITE);
      pixmap.fill();
      whiteTexture = new Texture(pixmap);
      whiteTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
      whiteRegion = new TextureRegion(whiteTexture);
      pixmap.dispose();
    }
    return whiteRegion;
  }

  @Override
  public TextureRegion playerRegion() {
    if (playerRegion == null) {
      playerTexture = new Texture(Gdx.files.internal("textures/entities/humanoids/Player.png"));
      playerTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
      playerRegion = new TextureRegion(playerTexture);
    }
    return playerRegion;
  }

  @Override
  public TextureRegion dottyRegion() {
    if (dottyRegion == null) {
      dottyTexture = new Texture(Gdx.files.internal("textures/entities/humanoids/Dotty.png"));
      dottyTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
      dottyRegion = new TextureRegion(dottyTexture);
    }
    return dottyRegion;
  }

  @Override
  public TextureRegion catsterRegion() {
    if (catsterRegion == null) {
      catsterTexture = new Texture(Gdx.files.internal("textures/entities/hostile/catster.png"));
      catsterTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
      catsterRegion = new TextureRegion(catsterTexture);
    }
    return catsterRegion;
  }

  @Override
  public TextureRegion floorRegion() {
    if (floorRegion == null) {
      floorTexture = new Texture(Gdx.files.internal("textures/tiles/floor_base.png"));
      floorTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
      floorRegion = new TextureRegion(floorTexture);
    }
    return floorRegion;
  }

  @Override
  public TextureRegion[] wallAutoTiles() {
    if (wallAutoRegions == null) {
      wallAutoTexture = new Texture(Gdx.files.internal("textures/tiles/wall_autotile.png"));
      wallAutoTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

      TextureRegion[][] split = TextureRegion.split(wallAutoTexture, 32, 32);
      int rows = split.length;
      int cols = rows == 0 ? 0 : split[0].length;
      int total = rows * cols;

      wallAutoRegions = new TextureRegion[total];
      int idx = 0;
      for (int row = 0; row < rows; row++) {
        for (int col = 0; col < cols; col++) {
          wallAutoRegions[idx++] = split[row][col];
        }
      }
    }
    return wallAutoRegions;
  }

  @Override
  public void close() {
    for (BitmapFont font : fontsBySize.values()) {
      font.dispose();
    }
    fontsBySize.clear();
    disposeTexture(whiteTexture);
    disposeTexture(playerTexture);
    disposeTexture(dottyTexture);
    disposeTexture(catsterTexture);
    disposeTexture(floorTexture);
    disposeTexture(wallAutoTexture);
    whiteRegion = null;
    playerRegion = null;
    dottyRegion = null;
    catsterRegion = null;
    floorRegion = null;
    wallAutoRegions = null;
  }

  private void disposeTexture(Texture tex) {
    if (tex != null) {
      tex.dispose();
    }
  }

  private BitmapFont loadFont(int size) {
    FreeTypeFontGenerator generator = null;
    try {
      generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/minecraft_font.ttf"));
      FreeTypeFontParameter params = new FreeTypeFontParameter();
      params.size = size;
      params.borderWidth = 0.9f;
      params.borderColor = new Color(0f, 0f, 0f, 0.6f);
      params.minFilter = TextureFilter.Nearest;
      params.magFilter = TextureFilter.Nearest;
      params.color = Color.WHITE;
      BitmapFont font = generator.generateFont(params);
      font.setUseIntegerPositions(true);
      font.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
      return font;
    } catch (Exception e) {
      BitmapFont fallback = new BitmapFont();
      fallback.setUseIntegerPositions(true);
      fallback.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
      return fallback;
    } finally {
      if (generator != null) {
        generator.dispose();
      }
    }
  }
}
