package com.droiddungeon.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.droiddungeon.items.TextureLoader;
import java.util.ArrayList;
import java.util.List;

/** GDX-backed texture loader for desktop client. */
public final class GdxTextureLoader implements TextureLoader {
  private final List<Texture> owned = new ArrayList<>();

  @Override
  public TextureRegion load(String path) {
    Texture texture = new Texture(Gdx.files.internal(path));
    texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
    owned.add(texture);
    return new TextureRegion(texture);
  }

  @Override
  public void close() {
    for (Texture texture : owned) {
      texture.dispose();
    }
    owned.clear();
  }
}
