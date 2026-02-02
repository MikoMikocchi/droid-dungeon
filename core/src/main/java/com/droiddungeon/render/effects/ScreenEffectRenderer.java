package com.droiddungeon.render.effects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.util.ArrayList;
import java.util.List;

/** Keeps a small list of lightweight screen-space effects and renders them in order. */
public final class ScreenEffectRenderer implements Disposable {
  private final List<ScreenEffect> effects = new ArrayList<>();
  private final SpriteBatch batch = new SpriteBatch();

  public void addEffect(ScreenEffect effect) {
    effects.add(effect);
  }

  public void render(Viewport viewport, float deltaSeconds) {
    if (effects.isEmpty()) {
      return;
    }
    viewport.apply();
    batch.setProjectionMatrix(viewport.getCamera().combined);

    batch.begin();
    for (ScreenEffect effect : effects) {
      if (effect.isEnabled()) {
        effect.render(viewport, batch, deltaSeconds);
      }
    }
    batch.end();
  }

  public void resize(int width, int height) {
    for (ScreenEffect effect : effects) {
      effect.resize(width, height);
    }
  }

  @Override
  public void dispose() {
    batch.dispose();
    for (ScreenEffect effect : effects) {
      effect.dispose();
    }
    effects.clear();
  }
}
