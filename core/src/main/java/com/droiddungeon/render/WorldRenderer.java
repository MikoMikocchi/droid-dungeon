package com.droiddungeon.render;

import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.enemies.Enemy;
import com.droiddungeon.grid.BlockMaterial;
import com.droiddungeon.grid.DungeonGenerator.Room;
import com.droiddungeon.grid.DungeonGenerator.RoomType;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.grid.TileMaterial;
import com.droiddungeon.items.GroundItem;
import com.droiddungeon.items.ItemDefinition;
import com.droiddungeon.items.ItemRegistry;
import com.droiddungeon.systems.WeaponSystem.WeaponState;
import com.droiddungeon.ui.MapMarker;

public final class WorldRenderer {
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final SpriteBatch spriteBatch = new SpriteBatch();
    private final BitmapFont font;
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private final TextureRegion whiteRegion;
    private final TextureRegion playerRegion;
    private final TextureRegion dottyRegion;
    private final TextureRegion catsterRegion;
    private final TextureRegion floorRegion;
    private final TextureRegion[] wallAutoTiles;
    private final Color tempColor = new Color();
    private static final Color HIT_FLASH = new Color(1f, 0.35f, 0.35f, 1f);
    private static final Color SAFE_TINT = new Color(0.30f, 0.55f, 0.95f, 1f);
    private static final Color DANGER_TINT = new Color(0.82f, 0.25f, 0.25f, 1f);

    public WorldRenderer() {
        font = RenderAssets.font(13);
        whiteRegion = RenderAssets.whiteRegion();
        playerRegion = RenderAssets.playerRegion();
        dottyRegion = RenderAssets.dottyRegion();
        catsterRegion = RenderAssets.catsterRegion();
        floorRegion = RenderAssets.floorRegion();
        wallAutoTiles = RenderAssets.wallAutoTiles();
    }

    public void render(
            Viewport viewport,
            float gridOriginX,
            float gridOriginY,
            Grid grid,
            Player player,
            WeaponState weaponState,
            List<GroundItem> groundItems,
            ItemRegistry itemRegistry,
            List<Enemy> enemies,
            MapMarker trackedMarker,
            float companionX,
            float companionY
    ) {
        viewport.apply(false);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);

        float tileSize = grid.getTileSize();
        VisibleWindow visible = VisibleWindow.from(viewport, tileSize);

        spriteBatch.begin();
        renderTileFill(grid, tileSize, visible);
        spriteBatch.end();

        renderRoomCorners(grid, tileSize, visible);
        renderWeaponFan(weaponState, player, gridOriginX, gridOriginY, tileSize);

        spriteBatch.begin();
        spriteBatch.setColor(Color.WHITE);
        renderGroundItems(groundItems, itemRegistry, tileSize, gridOriginX, gridOriginY);
        renderEnemies(enemies, gridOriginX, gridOriginY, tileSize);
        renderCompanionDotty(gridOriginX, gridOriginY, tileSize, companionX, companionY);
        renderPlayer(player, gridOriginX, gridOriginY, tileSize);
        spriteBatch.end();

        renderTrackedPointer(viewport, grid, player, trackedMarker);
    }

    private void renderTrackedPointer(Viewport viewport, Grid grid, Player player, MapMarker tracked) {
        if (tracked == null) {
            return;
        }
        com.badlogic.gdx.graphics.OrthographicCamera cam = (com.badlogic.gdx.graphics.OrthographicCamera) viewport.getCamera();
        float halfW = cam.viewportWidth * cam.zoom * 0.5f;
        float halfH = cam.viewportHeight * cam.zoom * 0.5f;
        float tileSize = grid.getTileSize();

        float targetX = (tracked.x() + 0.5f) * tileSize;
        float targetY = (tracked.y() + 0.5f) * tileSize;
        float dx = targetX - cam.position.x;
        float dy = targetY - cam.position.y;

        if (Math.abs(dx) <= halfW && Math.abs(dy) <= halfH) {
            return; // on screen
        }

        float margin = 18f;
        float maxX = halfW - margin;
        float maxY = halfH - margin;
        float scale = Math.max(Math.abs(dx) / maxX, Math.abs(dy) / maxY);
        float px = cam.position.x + dx / scale;
        float py = cam.position.y + dy / scale;

        float angle = MathUtils.atan2(dy, dx);
        float size = 14f;
        float x1 = px + MathUtils.cos(angle) * size;
        float y1 = py + MathUtils.sin(angle) * size;
        float x2 = px + MathUtils.cos(angle + MathUtils.PI * 0.66f) * size * 0.7f;
        float y2 = py + MathUtils.sin(angle + MathUtils.PI * 0.66f) * size * 0.7f;
        float x3 = px + MathUtils.cos(angle - MathUtils.PI * 0.66f) * size * 0.7f;
        float y3 = py + MathUtils.sin(angle - MathUtils.PI * 0.66f) * size * 0.7f;

        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0.95f, 0.8f, 0.3f, 0.9f);
        shapeRenderer.triangle(x1, y1, x2, y2, x3, y3);
        shapeRenderer.end();
    }

    private void renderEnemies(List<Enemy> enemies, float gridOriginX, float gridOriginY, float tileSize) {
        if (enemies == null || enemies.isEmpty()) {
            return;
        }
        float drawSize = tileSize * 0.9f;
        for (Enemy enemy : enemies) {
            float centerX = gridOriginX + (enemy.getRenderX() + 0.5f) * tileSize;
            float centerY = gridOriginY + (enemy.getRenderY() + 0.5f) * tileSize;
            float drawX = centerX - drawSize * 0.5f;
            float drawY = centerY - drawSize * 0.5f;
            if (enemy.getHitFlashTimer() > 0f) {
                float t = Math.min(1f, enemy.getHitFlashTimer() / 0.18f);
                tempColor.set(HIT_FLASH).lerp(Color.WHITE, 1f - t);
                spriteBatch.setColor(tempColor);
            } else {
                spriteBatch.setColor(Color.WHITE);
            }
            spriteBatch.draw(catsterRegion, drawX, drawY, drawSize, drawSize);
        }
        spriteBatch.setColor(Color.WHITE);
    }

    private void renderCompanionDotty(float gridOriginX, float gridOriginY, float tileSize, float companionX, float companionY) {
        float centerX = gridOriginX + (companionX + 0.5f) * tileSize;
        float centerY = gridOriginY + (companionY + 0.5f) * tileSize;
        float drawSize = tileSize * 0.9f;
        float drawX = centerX - drawSize * 0.5f;
        float drawY = centerY - drawSize * 0.5f;

        spriteBatch.draw(dottyRegion, drawX, drawY, drawSize, drawSize);
    }

    private void renderWeaponFan(WeaponState weaponState, Player player, float gridOriginX, float gridOriginY, float tileSize) {
        if (weaponState == null || !weaponState.active()) {
            return;
        }

        float centerX = gridOriginX + (player.getRenderX() + 0.5f) * tileSize;
        float centerY = gridOriginY + (player.getRenderY() + 0.5f) * tileSize;
        float outerRadius = weaponState.reachTiles() * tileSize;
        float innerRadius = weaponState.innerHoleTiles() * tileSize;
        if (outerRadius <= 0f || outerRadius <= innerRadius) {
            return;
        }

        float swingScale = weaponState.swinging() ? MathUtils.lerp(1f, 1.08f, weaponState.swingProgress()) : 1f;
        outerRadius *= swingScale;

        float startAngle = weaponState.aimAngleRad() - weaponState.arcRad() * 0.5f;

        float cooldownFactor = 1f - MathUtils.clamp(weaponState.cooldownRatio(), 0f, 1f);
        float baseAlpha = MathUtils.lerp(0.16f, 0.32f, cooldownFactor);
        if (weaponState.swinging()) {
            baseAlpha = Math.max(baseAlpha, 0.44f);
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
        Gdx.gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);
        Gdx.gl.glStencilMask(0xFF);
        Gdx.gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xFF);
        Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);

        int fillSegments = Math.max(32, Math.round(weaponState.arcRad() * MathUtils.radDeg));

        // Write mask only.
        Gdx.gl.glColorMask(false, false, false, false);
        shapeRenderer.begin(ShapeType.Filled);
        drawFan(shapeRenderer, centerX, centerY, innerRadius, outerRadius, startAngle, weaponState.arcRad(), fillSegments);
        shapeRenderer.end();

        // Enable color writes and only draw where mask == 1.
        Gdx.gl.glColorMask(true, true, true, true);
        Gdx.gl.glStencilFunc(GL20.GL_EQUAL, 1, 0xFF);
        Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_KEEP);

        // Base fill for the whole fan.
        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0.62f, 0.78f, 1f, baseAlpha);
        drawFan(shapeRenderer, centerX, centerY, innerRadius, outerRadius, startAngle, weaponState.arcRad(), fillSegments);
        shapeRenderer.end();

        // Diagonal overlay stripes that do not rotate with the fan orientation, clipped to the fan wedge.
        float stripeSpacing = tileSize * 0.42f;
        float stripeThickness = Math.max(3.4f, tileSize * 0.18f);
        float diagAngle = MathUtils.PI / 4f; // 45° world-space
        float dx = MathUtils.cos(diagAngle);
        float dy = MathUtils.sin(diagAngle);
        float nx = -dy;
        float ny = dx;

        int kMax = (int) Math.ceil(outerRadius / stripeSpacing);
        shapeRenderer.begin(ShapeType.Filled);
        for (int k = -kMax; k <= kMax; k++) {
            float offset = k * stripeSpacing;
            float segAlpha = baseAlpha * 0.75f * ((k & 1) == 0 ? 1f : 0.86f);
            if (weaponState.swinging()) {
                segAlpha = Math.min(0.9f, segAlpha + 0.12f);
            }
            shapeRenderer.setColor(0.55f, 0.72f, 0.96f, segAlpha);

            // Draw a long stripe line; stencil will clip it to the fan, hiding endpoints.
            float halfLen = outerRadius * 3f;
            float cx = centerX + offset * nx;
            float cy = centerY + offset * ny;
            float x0 = cx - dx * halfLen;
            float y0 = cy - dy * halfLen;
            float x1 = cx + dx * halfLen;
            float y1 = cy + dy * halfLen;
            shapeRenderer.rectLine(x0, y0, x1, y1, stripeThickness);
        }
        shapeRenderer.end();

        shapeRenderer.begin(ShapeType.Line);
        float outlineAlpha = MathUtils.clamp(baseAlpha + 0.18f, 0f, 0.85f);
        shapeRenderer.setColor(0.82f, 0.9f, 1f, outlineAlpha);

        float prevX = centerX + outerRadius * MathUtils.cos(startAngle);
        float prevY = centerY + outerRadius * MathUtils.sin(startAngle);
        int outlineSegments = Math.max(40, fillSegments);
        for (int i = 1; i <= outlineSegments; i++) {
            float a = startAngle + weaponState.arcRad() * i / outlineSegments;
            float x = centerX + outerRadius * MathUtils.cos(a);
            float y = centerY + outerRadius * MathUtils.sin(a);
            shapeRenderer.line(prevX, prevY, x, y);
            prevX = x;
            prevY = y;
        }
        shapeRenderer.line(centerX, centerY, centerX + outerRadius * MathUtils.cos(startAngle), centerY + outerRadius * MathUtils.sin(startAngle));
        shapeRenderer.line(centerX, centerY, centerX + outerRadius * MathUtils.cos(startAngle + weaponState.arcRad()), centerY + outerRadius * MathUtils.sin(startAngle + weaponState.arcRad()));
        shapeRenderer.end();

        float cd = MathUtils.clamp(weaponState.cooldownRatio(), 0f, 1f);
        if (cd > 0f) {
            float readyProgress = 1f - cd; // 0 -> just started cooldown, 1 -> ready
            float cdRadius = innerRadius + (outerRadius - innerRadius) * readyProgress;
            float cdAlpha = MathUtils.lerp(0.5f, 0.25f, readyProgress); // fade as it fills
            shapeRenderer.setColor(1f, 1f, 1f, cdAlpha);

            shapeRenderer.begin(ShapeType.Filled);
            int cdSegments = Math.max(32, Math.round(weaponState.arcRad() * MathUtils.radDeg));
            for (int i = 0; i < cdSegments; i++) {
                float a0 = startAngle + weaponState.arcRad() * (i / (float) cdSegments);
                float a1 = startAngle + weaponState.arcRad() * ((i + 1f) / cdSegments);

                float c0 = MathUtils.cos(a0);
                float s0 = MathUtils.sin(a0);
                float c1 = MathUtils.cos(a1);
                float s1 = MathUtils.sin(a1);

                float x0 = centerX + innerRadius * c0;
                float y0 = centerY + innerRadius * s0;
                float x1 = centerX + cdRadius * c0;
                float y1 = centerY + cdRadius * s0;
                float x2 = centerX + innerRadius * c1;
                float y2 = centerY + innerRadius * s1;
                float x3 = centerX + cdRadius * c1;
                float y3 = centerY + cdRadius * s1;

                shapeRenderer.triangle(x0, y0, x1, y1, x2, y2);
                shapeRenderer.triangle(x2, y2, x1, y1, x3, y3);
            }
            shapeRenderer.end();
        }

        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
    }


    private void drawFan(
            ShapeRenderer renderer,
            float centerX,
            float centerY,
            float innerRadius,
            float outerRadius,
            float startAngle,
            float arcRad,
            int segments
    ) {
        for (int i = 0; i < segments; i++) {
            float a0 = startAngle + arcRad * (i / (float) segments);
            float a1 = startAngle + arcRad * ((i + 1f) / segments);

            float c0 = MathUtils.cos(a0);
            float s0 = MathUtils.sin(a0);
            float c1 = MathUtils.cos(a1);
            float s1 = MathUtils.sin(a1);

            float x0 = centerX + innerRadius * c0;
            float y0 = centerY + innerRadius * s0;
            float x1 = centerX + outerRadius * c0;
            float y1 = centerY + outerRadius * s0;
            float x2 = centerX + innerRadius * c1;
            float y2 = centerY + innerRadius * s1;
            float x3 = centerX + outerRadius * c1;
            float y3 = centerY + outerRadius * s1;

            renderer.triangle(x0, y0, x1, y1, x2, y2);
            renderer.triangle(x2, y2, x1, y1, x3, y3);
        }
    }


    private void renderTileFill(Grid grid, float tileSize, VisibleWindow visible) {
        for (int y = visible.minTileY; y <= visible.maxTileY; y++) {
            for (int x = visible.minTileX; x <= visible.maxTileX; x++) {
                TileMaterial floor = grid.getTileMaterial(x, y);
                com.droiddungeon.grid.DungeonGenerator.RoomType roomType = grid.getRoomType(x, y);
                float wx = x * tileSize;
                float wy = y * tileSize;

                // Floor base
                Color floorColor = tempColor.set(colorFor(floor, roomType, x + y));
                spriteBatch.setColor(floorColor);
                spriteBatch.draw(floorRegion, wx, wy, tileSize, tileSize);

                BlockMaterial block = grid.getBlockMaterial(x, y);
                if (block != null) {
                    int mask = exposedMask(grid, x, y);
                    TextureRegion blockRegion = wallAutoTiles[Math.min(mask, wallAutoTiles.length - 1)];
                    Color blockColor = tempColor.set(colorFor(block.floorMaterial(), roomType, x + y));
                    // Walls are now lit by the lighting system - use moderate base brightness
                    // to allow lighting to have proper effect while keeping walls visible
                    int exposedCardinal = Integer.bitCount(mask & 0b1111);
                    int exposedDiagonal = Integer.bitCount(mask >> 4);
                    // Raised base shade for better visibility, lighting system handles darkness
                    float shade = 0.65f
                            + 0.04f * exposedCardinal
                            + 0.03f * exposedDiagonal;
                    shade = Math.min(shade, 0.85f);
                    blockColor.mul(shade, shade, shade, 1f);
                    spriteBatch.setColor(blockColor);
                    spriteBatch.draw(blockRegion, wx, wy, tileSize, tileSize);
                }
            }
        }
        spriteBatch.setColor(Color.WHITE);
    }

    private void renderGridLines(float tileSize, VisibleWindow visible) {
        // Grid lines intentionally disabled (request: remove grid overlay). Keep method for quick re-enable if needed.
    }

    private void renderGroundItems(
            List<GroundItem> groundItems,
            ItemRegistry itemRegistry,
            float tileSize,
            float gridOriginX,
            float gridOriginY
    ) {
        if (groundItems == null || groundItems.isEmpty()) {
            return;
        }
        for (GroundItem groundItem : groundItems) {
            ItemDefinition def = itemRegistry.get(groundItem.getStack().itemId());
            if (def == null) {
                continue;
            }
            TextureRegion icon = def.icon();
            float drawSize = tileSize * 0.68f;
            float drawX = gridOriginX + groundItem.getGridX() * tileSize + (tileSize - drawSize) * 0.5f;
            float drawY = gridOriginY + groundItem.getGridY() * tileSize + (tileSize - drawSize) * 0.5f;
            spriteBatch.draw(icon, drawX, drawY, drawSize, drawSize);

            if (groundItem.getStack().count() > 1) {
                String countText = Integer.toString(groundItem.getStack().count());
                glyphLayout.setText(font, countText);
                float textX = drawX + drawSize - glyphLayout.width - 2f;
                float textY = drawY + glyphLayout.height + 2f;
                drawCount(spriteBatch, countText, textX, textY, glyphLayout.width, glyphLayout.height);
            }
        }
    }

    private void renderPlayer(Player player, float gridOriginX, float gridOriginY, float tileSize) {
        float centerX = gridOriginX + (player.getRenderX() + 0.5f) * tileSize;
        float centerY = gridOriginY + (player.getRenderY() + 0.5f) * tileSize;
        float drawSize = tileSize * 0.9f;
        float drawX = centerX - drawSize * 0.5f;
        float drawY = centerY - drawSize * 0.5f;

        spriteBatch.draw(playerRegion, drawX, drawY, drawSize, drawSize);
    }

    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
    }

    private void drawCount(SpriteBatch batch, String text, float x, float y, float textWidth, float textHeight) {
        float bgPadX = 3f;
        float bgPadY = 1.5f;
        float bgX = x - bgPadX;
        float bgY = y - textHeight - bgPadY * 0.5f;
        float bgWidth = textWidth + bgPadX * 2f;
        float bgHeight = textHeight + bgPadY * 2f;

        batch.setColor(0f, 0f, 0f, 0.65f);
        batch.draw(whiteRegion, Math.round(bgX), Math.round(bgY), Math.round(bgWidth), Math.round(bgHeight));
        batch.setColor(Color.WHITE);

        font.setColor(Color.WHITE);
        font.draw(batch, text, Math.round(x), Math.round(y));
    }

    private Color colorFor(TileMaterial material, RoomType roomType, int parity) {
        // RoomType no longer tints fill; only corners use it.
        return tempColor.set(material.colorForParity(parity));
    }

    private int exposedMask(Grid grid, int x, int y) {
        int mask = 0;
        boolean n = isAir(grid, x, y + 1);
        boolean e = isAir(grid, x + 1, y);
        boolean s = isAir(grid, x, y - 1);
        boolean w = isAir(grid, x - 1, y);
        boolean ne = isAir(grid, x + 1, y + 1);
        boolean se = isAir(grid, x + 1, y - 1);
        boolean sw = isAir(grid, x - 1, y - 1);
        boolean nw = isAir(grid, x - 1, y + 1);

        if (n) mask |= 1;   // N
        if (e) mask |= 2;   // E
        if (s) mask |= 4;   // S
        if (w) mask |= 8;   // W
        if (ne) mask |= 16; // NE
        if (se) mask |= 32; // SE
        if (sw) mask |= 64; // SW
        if (nw) mask |= 128;// NW
        return mask;
    }

    private boolean isAir(Grid grid, int x, int y) {
        return grid.getBlockMaterial(x, y) == null;
    }

    private record VisibleWindow(int minTileX, int minTileY, int maxTileX, int maxTileY) {
        static VisibleWindow from(Viewport viewport, float tileSize) {
            com.badlogic.gdx.graphics.OrthographicCamera cam = (com.badlogic.gdx.graphics.OrthographicCamera) viewport.getCamera();
            float halfW = cam.viewportWidth * cam.zoom * 0.5f;
            float halfH = cam.viewportHeight * cam.zoom * 0.5f;
            float minWorldX = cam.position.x - halfW;
            float maxWorldX = cam.position.x + halfW;
            float minWorldY = cam.position.y - halfH;
            float maxWorldY = cam.position.y + halfH;

            int minTileX = (int) Math.floor(minWorldX / tileSize) - 2;
            int maxTileX = (int) Math.ceil(maxWorldX / tileSize) + 2;
            int minTileY = (int) Math.floor(minWorldY / tileSize) - 2;
            int maxTileY = (int) Math.ceil(maxWorldY / tileSize) + 2;
            return new VisibleWindow(minTileX, minTileY, maxTileX, maxTileY);
        }
    }

    private void renderRoomCorners(Grid grid, float tileSize, VisibleWindow visible) {
        List<Room> rooms = grid.getRoomsInArea(
                visible.minTileX - 2,
                visible.minTileY - 2,
                visible.maxTileX + 2,
                visible.maxTileY + 2
        );
        if (rooms.isEmpty()) {
            return;
        }

        float pad = tileSize * 0.18f;
        float segment = tileSize * 1.15f;
        float thickness = Math.max(2f, tileSize * 0.12f);

        shapeRenderer.begin(ShapeType.Filled);
        for (Room room : rooms) {
            Color tint = room.type == RoomType.SAFE ? SAFE_TINT : DANGER_TINT;
            shapeRenderer.setColor(tint.r, tint.g, tint.b, 1f);

            float x0 = room.x * tileSize - pad;
            float y0 = room.y * tileSize - pad;
            float x1 = x0 + room.width * tileSize + pad * 2f;
            float y1 = y0 + room.height * tileSize + pad * 2f;

            // Top-left corner
            shapeRenderer.rect(x0, y1 - thickness, segment, thickness);
            shapeRenderer.rect(x0, y1 - segment, thickness, segment);
            // Top-right
            shapeRenderer.rect(x1 - segment, y1 - thickness, segment, thickness);
            shapeRenderer.rect(x1 - thickness, y1 - segment, thickness, segment);
            // Bottom-left
            shapeRenderer.rect(x0, y0, segment, thickness);
            shapeRenderer.rect(x0, y0, thickness, segment);
            // Bottom-right
            shapeRenderer.rect(x1 - segment, y0, segment, thickness);
            shapeRenderer.rect(x1 - thickness, y0, thickness, segment);
        }
        shapeRenderer.end();
    }
}
