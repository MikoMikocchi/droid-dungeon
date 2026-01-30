package com.droiddungeon.render.lighting;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.grid.Grid;

/**
 * Main lighting system renderer.
 * Renders all lights to a light map framebuffer, then composites with the scene.
 *
 * Pipeline:
 * 1. Clear light map to ambient darkness
 * 2. For each light: render shadow-masked light contribution additively
 * 3. Composite light map over scene using multiply blending
 */
public class LightRenderer implements Disposable {
    // Render targets
    private FrameBuffer lightMapFbo;
    private TextureRegion lightMapRegion;
    private int fboWidth;
    private int fboHeight;

    // Rendering resources
    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private ShaderProgram lightShader;
    private ShaderProgram compositeShader;

    // Light texture (radial gradient)
    private Texture lightGradientTexture;
    private TextureRegion lightGradientRegion;

    // Noise texture for variation
    private Texture noiseTexture;

    // Shadow casting
    private final ShadowCaster shadowCaster;

    // Light sources
    private final List<Light> lights = new ArrayList<>();
    private Light playerLight;

    // Configuration
    // For multiply blend: 1.0 = full scene brightness, 0.0 = complete darkness
    // Ambient ~0.85 means shadows show 85% of scene color (very subtle darkening)
    private final Color ambientColor = new Color(0.82f, 0.80f, 0.78f, 1f);  // Overwritten by coordinator
    private float ambientIntensity = 1.0f;  // Multiplier for ambient
    private float globalBrightness = 1.5f;  // Master brightness control - boosted more
    private float maxLightIntensity = 1.0f;  // Max light contribution
    private boolean shadowsEnabled = false;  // Temporarily disabled for debugging
    private boolean softShadowsEnabled = false;  // Disabled for performance

    // Temporary matrices
    private final Matrix4 tempMatrix = new Matrix4();

    public LightRenderer() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        shadowCaster = new ShadowCaster();

        createLightGradient();
        createNoiseTexture();
        loadShaders();
    }

    /**
     * Initialize or resize the light map framebuffer.
     */
    public void resize(int width, int height) {
        if (lightMapFbo != null && fboWidth == width && fboHeight == height) {
            return;
        }

        if (lightMapFbo != null) {
            lightMapFbo.dispose();
        }

        // Use lower resolution for performance (half res is usually sufficient)
        fboWidth = Math.max(64, width);
        fboHeight = Math.max(64, height);

        lightMapFbo = new FrameBuffer(Pixmap.Format.RGBA8888, fboWidth, fboHeight, false);
        lightMapFbo.getColorBufferTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);
        lightMapRegion = new TextureRegion(lightMapFbo.getColorBufferTexture());
        lightMapRegion.flip(false, true);  // FBO is upside down
    }

    /**
     * Create the radial gradient texture for light rendering.
     * White in center, fading to black at edges (for additive blending onto ambient).
     */
    private void createLightGradient() {
        int size = 256;
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);

        float center = size / 2f;
        float maxDist = center;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - center;
                float dy = y - center;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float t = Math.min(1f, dist / maxDist);

                // Smooth falloff - bright in center, dark at edge
                float intensity;
                if (t < 0.15f) {
                    intensity = 1f;  // Full brightness in core
                } else if (t > 1f) {
                    intensity = 0f;
                } else {
                    // Smooth cubic falloff
                    float normalizedT = (t - 0.15f) / 0.85f;
                    intensity = 1f - normalizedT * normalizedT * (3f - 2f * normalizedT);
                }

                // Store as RGB color (white = lit, black = unlit)
                pm.setColor(intensity, intensity, intensity, 1f);
                pm.drawPixel(x, y);
            }
        }

        lightGradientTexture = new Texture(pm);
        lightGradientTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        lightGradientRegion = new TextureRegion(lightGradientTexture);
        pm.dispose();
    }

    /**
     * Create noise texture for light variation.
     */
    private void createNoiseTexture() {
        int size = 128;
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);

        java.util.Random random = new java.util.Random(12345);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                // Perlin-like noise approximation
                float noise = 0.5f + 0.5f * (random.nextFloat() - 0.5f);
                // Smoothing by averaging nearby samples
                float smoothed = noise * 0.6f + 0.4f * (0.5f + 0.3f * (float) Math.sin(x * 0.2f) * (float) Math.cos(y * 0.15f));
                pm.setColor(smoothed, smoothed, smoothed, 1f);
                pm.drawPixel(x, y);
            }
        }

        noiseTexture = new Texture(pm);
        noiseTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        noiseTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        pm.dispose();
    }

    /**
     * Load shaders for lighting.
     */
    private void loadShaders() {
        // Light rendering shader with smooth falloff
        String lightVertexShader =
                "attribute vec4 a_position;\n" +
                "attribute vec4 a_color;\n" +
                "attribute vec2 a_texCoord0;\n" +
                "uniform mat4 u_projTrans;\n" +
                "varying vec4 v_color;\n" +
                "varying vec2 v_texCoords;\n" +
                "void main() {\n" +
                "    v_color = a_color;\n" +
                "    v_texCoords = a_texCoord0;\n" +
                "    gl_Position = u_projTrans * a_position;\n" +
                "}\n";

        String lightFragmentShader =
                "#ifdef GL_ES\n" +
                "precision mediump float;\n" +
                "#endif\n" +
                "varying vec4 v_color;\n" +
                "varying vec2 v_texCoords;\n" +
                "uniform sampler2D u_texture;\n" +
                "uniform float u_intensity;\n" +
                "uniform float u_maxIntensity;\n" +
                "void main() {\n" +
                "    float alpha = texture2D(u_texture, v_texCoords).a;\n" +
                "    float intensity = alpha * u_intensity;\n" +
                "    intensity = min(intensity, u_maxIntensity);\n" +
                "    gl_FragColor = vec4(v_color.rgb * intensity, intensity);\n" +
                "}\n";

        lightShader = new ShaderProgram(lightVertexShader, lightFragmentShader);
        if (!lightShader.isCompiled()) {
            Gdx.app.error("LightRenderer", "Light shader compilation failed: " + lightShader.getLog());
            lightShader = null;
        }

        // Composite shader for combining light map with scene
        String compositeVertexShader =
                "attribute vec4 a_position;\n" +
                "attribute vec4 a_color;\n" +
                "attribute vec2 a_texCoord0;\n" +
                "uniform mat4 u_projTrans;\n" +
                "varying vec4 v_color;\n" +
                "varying vec2 v_texCoords;\n" +
                "void main() {\n" +
                "    v_color = a_color;\n" +
                "    v_texCoords = a_texCoord0;\n" +
                "    gl_Position = u_projTrans * a_position;\n" +
                "}\n";

        String compositeFragmentShader =
                "#ifdef GL_ES\n" +
                "precision mediump float;\n" +
                "#endif\n" +
                "varying vec4 v_color;\n" +
                "varying vec2 v_texCoords;\n" +
                "uniform sampler2D u_texture;\n" +
                "uniform float u_brightness;\n" +
                "void main() {\n" +
                "    vec4 lightColor = texture2D(u_texture, v_texCoords);\n" +
                "    // Ensure minimum visibility\n" +
                "    float luminance = max(lightColor.r, max(lightColor.g, lightColor.b));\n" +
                "    luminance = luminance * u_brightness;\n" +
                "    // Soft clamp to prevent harsh cutoffs\n" +
                "    luminance = clamp(luminance, 0.0, 1.0);\n" +
                "    gl_FragColor = vec4(lightColor.rgb * u_brightness, luminance);\n" +
                "}\n";

        compositeShader = new ShaderProgram(compositeVertexShader, compositeFragmentShader);
        if (!compositeShader.isCompiled()) {
            Gdx.app.error("LightRenderer", "Composite shader compilation failed: " + compositeShader.getLog());
            compositeShader = null;
        }
    }

    /**
     * Add a light to the scene.
     */
    public void addLight(Light light) {
        if (!lights.contains(light)) {
            lights.add(light);
        }
    }

    /**
     * Remove a light from the scene.
     */
    public void removeLight(Light light) {
        lights.remove(light);
    }

    /**
     * Clear all lights except player light.
     */
    public void clearLights() {
        lights.clear();
        if (playerLight != null) {
            lights.add(playerLight);
        }
    }

    /**
     * Set or create the player's light.
     */
    public void setPlayerLight(float x, float y, float tileSize) {
        if (playerLight == null) {
            playerLight = LightType.PLAYER_AURA.createLight(x, y, tileSize);
            lights.add(playerLight);
        } else {
            playerLight.setPosition(x, y);
        }
    }

    /**
     * Get the player's light for customization.
     */
    public Light getPlayerLight() {
        return playerLight;
    }

    /**
     * Update all lights (for flicker animations).
     */
    public void update(float delta) {
        for (Light light : lights) {
            light.update(delta);
        }
    }

    /**
     * Render the light map.
     * Light map contains brightness values: ambient in shadows, brighter where lights shine.
     *
     * @param viewport   The world viewport
     * @param grid       The world grid for shadow casting
     * @param tileSize   Size of each tile
     */
    public void renderLightMap(Viewport viewport, Grid grid, float tileSize) {
        if (lightMapFbo == null) {
            resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        }

        OrthographicCamera cam = (OrthographicCamera) viewport.getCamera();

        // Bind light map FBO
        lightMapFbo.begin();

        // Clear to ambient color - this is the base darkness level
        // These values will be multiplied with the scene, so:
        // - 1.0 = full brightness (no darkening)
        // - 0.0 = complete darkness
        float ar = ambientColor.r * ambientIntensity;
        float ag = ambientColor.g * ambientIntensity;
        float ab = ambientColor.b * ambientIntensity;
        Gdx.gl.glClearColor(ar, ag, ab, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Set up projection to match viewport
        tempMatrix.set(cam.combined);
        batch.setProjectionMatrix(tempMatrix);
        shapeRenderer.setProjectionMatrix(tempMatrix);

        // Use additive blending - lights add brightness on top of ambient
        // GL_ONE, GL_ONE = src + dst (additive)
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);

        // Render each light
        float viewHalfW = cam.viewportWidth * cam.zoom * 0.5f;
        float viewHalfH = cam.viewportHeight * cam.zoom * 0.5f;

        for (Light light : lights) {
            if (!light.isActive()) continue;

            float lightRadius = light.getCurrentRadius();

            // Frustum culling
            if (light.getX() + lightRadius < cam.position.x - viewHalfW ||
                light.getX() - lightRadius > cam.position.x + viewHalfW ||
                light.getY() + lightRadius < cam.position.y - viewHalfH ||
                light.getY() - lightRadius > cam.position.y + viewHalfH) {
                continue;
            }

            renderSingleLight(light, grid, tileSize);
        }

        lightMapFbo.end();

        // IMPORTANT: Fully reset GL state after FBO rendering
        // This ensures world renderer (including weapon fan stencil) works correctly
        Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
        Gdx.gl.glStencilMask(0xFF);
        Gdx.gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glColorMask(true, true, true, true);
    }

    /**
     * Render a single light to the light map.
     */
    private void renderSingleLight(Light light, Grid grid, float tileSize) {
        float x = light.getX();
        float y = light.getY();
        float radius = light.getCurrentRadius();
        float intensity = light.getCurrentIntensity();
        Color color = light.getColor();

        if (shadowsEnabled) {
            // Render light with shadow masking using stencil buffer
            renderLightWithShadows(light, grid, tileSize);
        } else {
            // Simple light rendering without shadows
            renderLightSimple(x, y, radius, intensity, color);
        }
    }

    /**
     * Render light with shadow casting using stencil masking.
     */
    private void renderLightWithShadows(Light light, Grid grid, float tileSize) {
        float x = light.getX();
        float y = light.getY();
        float radius = light.getCurrentRadius();
        float intensity = light.getCurrentIntensity();
        Color color = light.getColor();

        // Cast shadows and get lit polygon
        ShadowCaster.ShadowResult shadowResult = shadowCaster.castShadows(light, grid, tileSize);

        // Use stencil buffer to mask light to visible area
        Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
        Gdx.gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);

        // Write to stencil only
        Gdx.gl.glColorMask(false, false, false, false);
        Gdx.gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xFF);
        Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);
        Gdx.gl.glStencilMask(0xFF);

        // Draw lit polygon to stencil
        shapeRenderer.begin(ShapeType.Filled);
        if (shadowResult.vertexCount >= 3) {
            for (int i = 1; i < shadowResult.vertexCount - 1; i++) {
                float x0 = shadowResult.vertices[0];
                float y0 = shadowResult.vertices[1];
                float x1 = shadowResult.vertices[i * 2];
                float y1 = shadowResult.vertices[i * 2 + 1];
                float x2 = shadowResult.vertices[(i + 1) * 2];
                float y2 = shadowResult.vertices[(i + 1) * 2 + 1];
                shapeRenderer.triangle(x0, y0, x1, y1, x2, y2);
            }
        }
        shapeRenderer.end();

        // Enable color writes, only where stencil == 1
        Gdx.gl.glColorMask(true, true, true, true);
        Gdx.gl.glStencilFunc(GL20.GL_EQUAL, 1, 0xFF);
        Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_KEEP);
        Gdx.gl.glStencilMask(0x00);

        // Render the light
        renderLightSimple(x, y, radius, intensity, color);

        // Add soft shadow edges if enabled
        if (softShadowsEnabled) {
            renderSoftShadowEdges(light, shadowResult);
        }

        Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
    }

    /**
     * Render basic light sprite - adds brightness to the light map.
     */
    private void renderLightSimple(float x, float y, float radius, float intensity, Color color) {
        batch.begin();
        // IMPORTANT: Set additive blend AFTER begin(), as begin() resets blend mode
        batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE);
        
        // Light adds brightness on top of ambient to reach ~1.0 (full brightness)
        // Slightly higher multiplier so lights punch through ambient more
        float strength = intensity * globalBrightness * 0.55f;
        batch.setColor(
            color.r * strength,
            color.g * strength,
            color.b * strength,
            1f
        );
        float size = radius * 2f;
        batch.draw(lightGradientRegion, x - radius, y - radius, size, size);
        batch.end();
        
        // Reset blend mode for next operations
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.setColor(Color.WHITE);
    }

    /**
     * Render soft edges at shadow boundaries for smoother look.
     */
    private void renderSoftShadowEdges(Light light, ShadowCaster.ShadowResult shadowResult) {
        // Soft shadow edge rendering - subtle glow along shadow boundaries
        // This creates a softer transition instead of hard shadow edges
        float softEdgeWidth = light.getCurrentRadius() * 0.08f;

        shapeRenderer.begin(ShapeType.Filled);
        Color edgeColor = new Color(light.getColor());
        edgeColor.a = 0.15f * light.getCurrentIntensity();
        shapeRenderer.setColor(edgeColor);

        // Draw small gradient strips along shadow edges
        for (int i = 0; i < shadowResult.vertexCount; i++) {
            int nextI = (i + 1) % shadowResult.vertexCount;
            float x1 = shadowResult.vertices[i * 2];
            float y1 = shadowResult.vertices[i * 2 + 1];
            float x2 = shadowResult.vertices[nextI * 2];
            float y2 = shadowResult.vertices[nextI * 2 + 1];

            // Only draw edge strip if this edge is significantly closer than max radius
            float dist1 = (float) Math.sqrt(
                    (x1 - light.getX()) * (x1 - light.getX()) +
                    (y1 - light.getY()) * (y1 - light.getY())
            );
            float dist2 = (float) Math.sqrt(
                    (x2 - light.getX()) * (x2 - light.getX()) +
                    (y2 - light.getY()) * (y2 - light.getY())
            );

            float maxR = light.getCurrentRadius();
            if (dist1 < maxR * 0.95f || dist2 < maxR * 0.95f) {
                shapeRenderer.rectLine(x1, y1, x2, y2, softEdgeWidth);
            }
        }
        shapeRenderer.end();
    }

    /**
     * Composite the light map over the scene.
     * Uses multiply blending: scene color * light map color.
     * White in light map = no change, darker = scene gets darker.
     * 
     * The FBO already contains the correctly transformed light map,
     * so we draw it directly to screen coordinates (1:1 pixel mapping).
     */
    public void compositeLightMap() {
        if (lightMapFbo == null || lightMapRegion == null) {
            return;
        }

        // Use screen-space projection (FBO is already in screen space)
        int screenW = Gdx.graphics.getBackBufferWidth();
        int screenH = Gdx.graphics.getBackBufferHeight();
        
        tempMatrix.setToOrtho2D(0, 0, screenW, screenH);
        batch.setProjectionMatrix(tempMatrix);

        batch.begin();
        // IMPORTANT: Set multiply blend AFTER begin(), as begin() resets blend mode
        // Multiply blend: result = dst * src (scene * lightmap)
        batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO);
        batch.setColor(Color.WHITE);
        batch.draw(
            lightMapRegion,
            0, 0,
            screenW, screenH
        );
        // Reset blend before ending batch
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.end();

        // Also reset global GL blend mode to ensure other renderers work correctly
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    // Configuration methods

    public void setAmbientColor(Color color) {
        this.ambientColor.set(color);
    }

    public void setAmbientColor(float r, float g, float b) {
        this.ambientColor.set(r, g, b, 1f);
    }

    public Color getAmbientColor() {
        return ambientColor;
    }

    public void setAmbientIntensity(float intensity) {
        this.ambientIntensity = Math.max(0f, Math.min(1f, intensity));
    }

    public float getAmbientIntensity() {
        return ambientIntensity;
    }

    public void setGlobalBrightness(float brightness) {
        this.globalBrightness = Math.max(0f, Math.min(2f, brightness));
    }

    public float getGlobalBrightness() {
        return globalBrightness;
    }

    public void setMaxLightIntensity(float max) {
        this.maxLightIntensity = Math.max(0.1f, Math.min(1f, max));
    }

    public float getMaxLightIntensity() {
        return maxLightIntensity;
    }

    public void setShadowsEnabled(boolean enabled) {
        this.shadowsEnabled = enabled;
    }

    public boolean isShadowsEnabled() {
        return shadowsEnabled;
    }

    public void setSoftShadowsEnabled(boolean enabled) {
        this.softShadowsEnabled = enabled;
    }

    public boolean isSoftShadowsEnabled() {
        return softShadowsEnabled;
    }

    public List<Light> getLights() {
        return lights;
    }

    @Override
    public void dispose() {
        if (lightMapFbo != null) {
            lightMapFbo.dispose();
            lightMapFbo = null;
        }
        if (lightGradientTexture != null) {
            lightGradientTexture.dispose();
            lightGradientTexture = null;
        }
        if (noiseTexture != null) {
            noiseTexture.dispose();
            noiseTexture = null;
        }
        if (lightShader != null) {
            lightShader.dispose();
            lightShader = null;
        }
        if (compositeShader != null) {
            compositeShader.dispose();
            compositeShader = null;
        }
        batch.dispose();
        shapeRenderer.dispose();
        lights.clear();
        playerLight = null;
    }
}
