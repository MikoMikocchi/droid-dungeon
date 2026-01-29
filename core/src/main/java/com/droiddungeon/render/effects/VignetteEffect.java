package com.droiddungeon.render.effects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Soft concave vignette overlay for the screen edges.
 */
public final class VignetteEffect implements ScreenEffect {
    private Texture texture;
    private TextureRegion region;

    /** Multiplier applied at draw time so we can tweak strength without regenerating texture. */
    private float alpha = 0.32f;

    /** Fraction of the smaller screen dimension used for the fade band width. */
    private float edgeWidth = 0.20f;

    /** Shapes the concave curve: lower values curve inward more. */
    private float concavePower = 1.4f;

    public VignetteEffect() {
        rebuild(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    }

    @Override
    public void render(Viewport viewport, SpriteBatch batch, float deltaSeconds) {
        if (region == null) {
            return;
        }
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        float w = viewport.getWorldWidth();
        float h = viewport.getWorldHeight();
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(region, 0f, 0f, w, h);
        batch.setColor(Color.WHITE);

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void resize(int width, int height) {
        rebuild(width, height);
    }

    @Override
    public void dispose() {
        if (texture != null) {
            texture.dispose();
            texture = null;
            region = null;
        }
    }

    public void setAlpha(float alpha) {
        this.alpha = Math.max(0f, Math.min(1f, alpha));
    }

    public void setEdgeWidth(float edgeWidth) {
        this.edgeWidth = Math.max(0.05f, Math.min(0.5f, edgeWidth));
        rebuild(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    }

    public void setConcavePower(float concavePower) {
        this.concavePower = Math.max(0.8f, Math.min(3f, concavePower));
        rebuild(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    }

    private void rebuild(int width, int height) {
        dispose();

        int w = Math.max(2, width);
        int h = Math.max(2, height);
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);

        float minDim = Math.min(w, h);
        float fadeDist = Math.max(8f, minDim * edgeWidth);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float nx = Math.abs((x + 0.5f) / w * 2f - 1f);
                float ny = Math.abs((y + 0.5f) / h * 2f - 1f);

                // Concave shaping: value rises faster near edges than near center.
                float concave = (float) Math.pow(Math.max(nx, ny), concavePower);

                float distToEdge = Math.min(Math.min(x, w - 1 - x), Math.min(y, h - 1 - y));
                float t = Math.min(1f, distToEdge / fadeDist); // 0 at edge → 1 deeper inside
                float fade = 1f - Interpolation.smooth.apply(t); // 1 at edge → 0 inside

                float intensity = fade * (0.65f + 0.35f * concave); // slightly stronger near corners
                pm.setColor(0f, 0f, 0f, intensity);
                pm.drawPixel(x, y);
            }
        }

        texture = new Texture(pm);
        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        region = new TextureRegion(texture);
        pm.dispose();
    }
}
