package com.droiddungeon.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;
import com.droiddungeon.grid.TileMaterial;

/**
 * Full-map overlay with panning/zooming and markers.
 */
public final class MapOverlay {
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final SpriteBatch spriteBatch = new SpriteBatch();
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private final AtomicInteger idGen = new AtomicInteger(1);
    private final List<MapMarker> markers = new ArrayList<>();

    private boolean open;
    private float camX;
    private float camY;
    private float zoom = 12f; // pixels per tile
    private final float minZoom = 4f;
    private final float maxZoom = 48f;
    private boolean dragging;
    private float lastDragX;
    private float lastDragY;
    private long lastClickMs;
    private boolean lastClickOnMap;
    private int selectedMarkerId = -1;
    private int trackedMarkerId = -1;
    private boolean awaitingPlacement;
    private String pendingPlacementLabel = "Marker";

    public MapOverlay(BitmapFont font) {
        this.font = font;
    }

    public boolean isOpen() {
        return open;
    }

    public void toggle(Grid grid, Player player) {
        if (open) {
            close();
        } else {
            open(grid, player);
        }
    }

    public void open(Grid grid, Player player) {
        open = true;
        camX = player != null ? player.getRenderX() : 0f;
        camY = player != null ? player.getRenderY() : 0f;
    }

    public void close() {
        open = false;
        dragging = false;
    }

    public void addDeathMarker(int x, int y) {
        MapMarker marker = new MapMarker(idGen.getAndIncrement(), x, y, "Death", MapMarker.Type.DEATH);
        markers.add(marker);
    }

    public void addCustomMarker(int x, int y, String label) {
        markers.add(new MapMarker(idGen.getAndIncrement(), x, y, label, MapMarker.Type.CUSTOM));
    }

    public List<MapMarker> getMarkers() {
        return Collections.unmodifiableList(markers);
    }

    public MapMarker getTracked() {
        return trackedMarkerId < 0 ? null : markers.stream().filter(m -> m.id() == trackedMarkerId).findFirst().orElse(null);
    }

    public void update(float deltaSeconds, Viewport uiViewport, Grid grid) {
        if (!open) {
            return;
        }

        handleInput(deltaSeconds, uiViewport);
        clampCamera(grid);
    }

    private void handleInput(float deltaSeconds, Viewport uiViewport) {
        Rectangle mapArea = mapRect(uiViewport);
        Rectangle listArea = listRect(uiViewport, mapArea);
        Vector2 world = uiViewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));

        // Pan with WASD.
        float panSpeed = 30f; // tiles per second
        float dx = 0f;
        float dy = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) dx -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) dx += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) dy += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) dy -= 1f;
        if (dx != 0f || dy != 0f) {
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            camX += (dx / len) * panSpeed * deltaSeconds;
            camY += (dy / len) * panSpeed * deltaSeconds;
        }

        // Zoom with +/- keys
        if (Gdx.input.isKeyJustPressed(Input.Keys.EQUALS) || Gdx.input.isKeyJustPressed(Input.Keys.PAGE_UP)) {
            zoom = MathUtils.clamp(zoom * 1.1f, minZoom, maxZoom);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS) || Gdx.input.isKeyJustPressed(Input.Keys.PAGE_DOWN)) {
            zoom = MathUtils.clamp(zoom * 0.9f, minZoom, maxZoom);
        }

        // Drag pan.
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            if (!dragging && mapArea.contains(world)) {
                dragging = true;
                lastDragX = world.x;
                lastDragY = world.y;
            }
        } else {
            dragging = false;
        }

        if (dragging) {
            float dxWorld = world.x - lastDragX;
            float dyWorld = world.y - lastDragY;
            camX -= dxWorld / zoom;
            camY -= dyWorld / zoom;
            lastDragX = world.x;
            lastDragY = world.y;
        }

        // Place pending marker with single click.
        if (awaitingPlacement && Gdx.input.justTouched() && mapArea.contains(world)) {
            int tileX = (int) Math.floor(screenToTileX(world.x, mapArea));
            int tileY = (int) Math.floor(screenToTileY(world.y, mapArea));
            addCustomMarker(tileX, tileY, pendingPlacementLabel);
            awaitingPlacement = false;
            selectedMarkerId = markers.isEmpty() ? -1 : markers.getLast().id();
        }

        // Double-click to add marker on map area.
        if (Gdx.input.justTouched()) {
            boolean inside = mapArea.contains(world);
            long now = TimeUtils.millis();
            if (inside && lastClickOnMap && now - lastClickMs < 280) {
                int tileX = (int) Math.floor(screenToTileX(world.x, mapArea));
                int tileY = (int) Math.floor(screenToTileY(world.y, mapArea));
                addCustomMarker(tileX, tileY, "Marker " + idGen.get());
            }
            lastClickOnMap = inside;
            lastClickMs = now;

            // UI gets priority
            if (handleButtonsClick(world, listArea)) {
                return;
            }
            if (handleListClick(world, listArea)) {
                return;
            }
            // Select markers by click.
            if (inside) {
                selectMarkerAt(world.x, world.y, mapArea);
            } else {
                selectedMarkerId = -1;
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.FORWARD_DEL) || Gdx.input.isKeyJustPressed(Input.Keys.DEL)) {
            deleteSelected();
        }

        if (selectedMarkerId >= 0 && (Gdx.input.isKeyJustPressed(Input.Keys.T))) {
            toggleTrackSelected();
        }
    }

    private float screenToTileX(float screenX, Rectangle mapArea) {
        float centerX = mapArea.x + mapArea.width * 0.5f;
        return camX + (screenX - centerX) / zoom;
    }

    private float screenToTileY(float screenY, Rectangle mapArea) {
        float centerY = mapArea.y + mapArea.height * 0.5f;
        return camY + (screenY - centerY) / zoom;
    }

    private void clampCamera(Grid grid) {
        int minX = grid.getMinGeneratedX();
        int maxX = grid.getMaxGeneratedX();
        int minY = grid.getMinGeneratedY();
        int maxY = grid.getMaxGeneratedY();
        camX = MathUtils.clamp(camX, minX, maxX);
        camY = MathUtils.clamp(camY, minY, maxY);
    }

    public void render(Viewport uiViewport, Grid grid, Player player, float companionX, float companionY) {
        if (!open) {
            return;
        }
        Rectangle mapArea = mapRect(uiViewport);
        Rectangle listArea = listRect(uiViewport, mapArea);

        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(uiViewport.getCamera().combined);
        spriteBatch.setProjectionMatrix(uiViewport.getCamera().combined);

        float buttonHeight = 26f;
        float margin = 8f;
        float wBtn = listArea.width - margin * 2f;
        float yBtnStart = listArea.y + margin;

        // Background + panels + buttons
        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.6f);
        shapeRenderer.rect(0f, 0f, uiViewport.getWorldWidth(), uiViewport.getWorldHeight());

        shapeRenderer.setColor(0.08f, 0.1f, 0.12f, 0.95f);
        shapeRenderer.rect(mapArea.x, mapArea.y, mapArea.width, mapArea.height);

        shapeRenderer.setColor(0.1f, 0.12f, 0.14f, 0.95f);
        shapeRenderer.rect(listArea.x, listArea.y, listArea.width, listArea.height);
        shapeRenderer.setColor(0.2f, 0.24f, 0.28f, 1f);
        shapeRenderer.rect(listArea.x, listArea.y + listArea.height - 36f, listArea.width, 36f);

        shapeRenderer.setColor(0.22f, 0.36f, 0.52f, 1f);
        float yBtn = yBtnStart;
        for (int i = 0; i < 4; i++) {
            shapeRenderer.rect(listArea.x + margin, yBtn, wBtn, buttonHeight);
            yBtn += buttonHeight + 6f;
        }
        shapeRenderer.end();

        // Clip map content to its rectangle
        com.badlogic.gdx.graphics.GL20 gl = Gdx.graphics.getGL20();
        gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_SCISSOR_TEST);
        int sx = Math.round(mapArea.x * Gdx.graphics.getBackBufferWidth() / uiViewport.getWorldWidth());
        int sy = Math.round(mapArea.y * Gdx.graphics.getBackBufferHeight() / uiViewport.getWorldHeight());
        int sw = Math.round(mapArea.width * Gdx.graphics.getBackBufferWidth() / uiViewport.getWorldWidth());
        int sh = Math.round(mapArea.height * Gdx.graphics.getBackBufferHeight() / uiViewport.getWorldHeight());
        gl.glScissor(sx, sy, sw, sh);

        renderTiles(grid, mapArea);
        renderMarkers(mapArea, player, companionX, companionY);

        gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_SCISSOR_TEST);

        Rectangle selectedRect = selectedListRow(listArea);
        if (selectedRect != null) {
            shapeRenderer.begin(ShapeType.Line);
            shapeRenderer.setColor(1f, 1f, 1f, 0.7f);
            shapeRenderer.rect(selectedRect.x, selectedRect.y, selectedRect.width, selectedRect.height);
            shapeRenderer.end();
        }

        shapeRenderer.begin(ShapeType.Line);
        shapeRenderer.setColor(0.4f, 0.4f, 0.44f, 1f);
        shapeRenderer.rect(mapArea.x - 1f, mapArea.y - 1f, mapArea.width + 2f, mapArea.height + 2f);
        shapeRenderer.end();

        spriteBatch.begin();
        font.setColor(Color.WHITE);
        layout.setText(font, "Markers");
        float titleX = listArea.x + (listArea.width - layout.width) * 0.5f;
        float titleY = listArea.y + listArea.height - 12f;
        font.draw(spriteBatch, layout, titleX, titleY);

        float rowY = listArea.y + listArea.height - 52f;
        int row = 0;
        for (MapMarker marker : markers) {
            float y = rowY - row * 22f;
            if (y < listArea.y + 50f) {
                break;
            }
            boolean selected = marker.id() == selectedMarkerId;
            Color c = colorFor(marker.type());
            font.setColor(selected ? Color.WHITE : c);
            layout.setText(font, marker.label() + (marker.tracked() ? " (T)" : ""));
            float tx = listArea.x + 12f;
            float ty = y;
            font.draw(spriteBatch, layout, tx, ty);
            row++;
        }

        yBtn = yBtnStart;
        String[] labels = {"Add", "Rename", "Delete", "Track"};
        for (String label : labels) {
            layout.setText(font, label);
            float tx = listArea.x + margin + (wBtn - layout.width) * 0.5f;
            float ty = yBtn + (buttonHeight + layout.height) * 0.5f;
            font.setColor(Color.WHITE);
            font.draw(spriteBatch, layout, tx, ty);
            yBtn += buttonHeight + 6f;
        }
        spriteBatch.end();

        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
    }

    private void renderTiles(Grid grid, Rectangle mapArea) {
        float centerX = mapArea.x + mapArea.width * 0.5f;
        float centerY = mapArea.y + mapArea.height * 0.5f;
        float halfTilesX = mapArea.width * 0.5f / zoom;
        float halfTilesY = mapArea.height * 0.5f / zoom;

        int minTileX = MathUtils.floor(camX - halfTilesX) - 1;
        int maxTileX = MathUtils.ceil(camX + halfTilesX) + 1;
        int minTileY = MathUtils.floor(camY - halfTilesY) - 1;
        int maxTileY = MathUtils.ceil(camY + halfTilesY) + 1;

        shapeRenderer.begin(ShapeType.Filled);
        for (int y = minTileY; y <= maxTileY; y++) {
            for (int x = minTileX; x <= maxTileX; x++) {
                TileMaterial mat = grid.getTileMaterial(x, y);
                Color base = mat.colorForParity(x + y);
                float sx = centerX + (x - camX) * zoom;
                float sy = centerY + (y - camY) * zoom;
                shapeRenderer.setColor(base.r, base.g, base.b, 1f);
                shapeRenderer.rect(sx, sy, zoom, zoom);
            }
        }
        shapeRenderer.end();

        renderRoomCorners(grid, minTileX, minTileY, maxTileX, maxTileY, centerX, centerY);
    }

    private void renderRoomCorners(Grid grid, int minTileX, int minTileY, int maxTileX, int maxTileY, float centerX, float centerY) {
        java.util.List<com.droiddungeon.grid.DungeonGenerator.Room> rooms = grid.getRoomsInArea(minTileX, minTileY, maxTileX, maxTileY);
        if (rooms.isEmpty()) {
            return;
        }
        float thickness = Math.max(1.6f, zoom * 0.12f);
        float segment = zoom * 1.1f;
        float pad = zoom * 0.18f;

        shapeRenderer.begin(ShapeType.Filled);
        for (com.droiddungeon.grid.DungeonGenerator.Room room : rooms) {
            Color tint = room.type == com.droiddungeon.grid.DungeonGenerator.RoomType.SAFE ? new Color(0.30f, 0.55f, 0.95f, 1f) : new Color(0.82f, 0.25f, 0.25f, 1f);
            shapeRenderer.setColor(tint);
            float x0 = centerX + (room.x - camX) * zoom - pad;
            float y0 = centerY + (room.y - camY) * zoom - pad;
            float x1 = x0 + room.width * zoom + pad * 2f;
            float y1 = y0 + room.height * zoom + pad * 2f;

            // top-left
            shapeRenderer.rect(x0, y1 - thickness, segment, thickness);
            shapeRenderer.rect(x0, y1 - segment, thickness, segment);
            // top-right
            shapeRenderer.rect(x1 - segment, y1 - thickness, segment, thickness);
            shapeRenderer.rect(x1 - thickness, y1 - segment, thickness, segment);
            // bottom-left
            shapeRenderer.rect(x0, y0, segment, thickness);
            shapeRenderer.rect(x0, y0, thickness, segment);
            // bottom-right
            shapeRenderer.rect(x1 - segment, y0, segment, thickness);
            shapeRenderer.rect(x1 - thickness, y0, thickness, segment);
        }
        shapeRenderer.end();
    }

    private void renderMarkers(Rectangle mapArea, Player player, float companionX, float companionY) {
        float centerX = mapArea.x + mapArea.width * 0.5f;
        float centerY = mapArea.y + mapArea.height * 0.5f;

        shapeRenderer.begin(ShapeType.Filled);
        for (MapMarker marker : markers) {
            float sx = centerX + (marker.x() - camX) * zoom;
            float sy = centerY + (marker.y() - camY) * zoom;
            Color c = colorFor(marker.type());
            shapeRenderer.setColor(c);
            shapeRenderer.rect(sx - 4f, sy - 4f, 8f, 8f);
            if (marker.tracked()) {
                shapeRenderer.setColor(1f, 1f, 1f, 1f);
                shapeRenderer.rect(sx - 6f, sy - 6f, 12f, 1.4f);
                shapeRenderer.rect(sx - 6f, sy + 4.6f, 12f, 1.4f);
                shapeRenderer.rect(sx - 6f, sy - 6f, 1.4f, 12f);
                shapeRenderer.rect(sx + 4.6f, sy - 6f, 1.4f, 12f);
            }
        }
        shapeRenderer.end();

        spriteBatch.begin();
        for (MapMarker marker : markers) {
            float sx = centerX + (marker.x() - camX) * zoom;
            float sy = centerY + (marker.y() - camY) * zoom + 12f;
            font.setColor(marker.tracked() ? Color.WHITE : new Color(0.9f, 0.9f, 0.9f, 1f));
            font.draw(spriteBatch, marker.label(), sx + 6f, sy);
        }
        spriteBatch.end();

        // Player & companion markers (tile coords)
        float px = centerX + (player.getRenderX() - camX + 0.5f) * zoom;
        float py = centerY + (player.getRenderY() - camY + 0.5f) * zoom;
        float cx = centerX + (companionX - camX + 0.5f) * zoom;
        float cy = centerY + (companionY - camY + 0.5f) * zoom;
        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0.98f, 0.86f, 0.3f, 1f);
        shapeRenderer.rect(px - 4f, py - 4f, 8f, 8f);
        shapeRenderer.setColor(0.35f, 0.85f, 0.95f, 1f);
        shapeRenderer.rect(cx - 3f, cy - 3f, 6f, 6f);
        shapeRenderer.end();
    }

    private Rectangle mapRect(Viewport uiViewport) {
        float baseW = uiViewport.getWorldWidth() * 0.8f;
        float baseH = uiViewport.getWorldHeight() * 0.8f;
        float listWidth = Math.min(260f, uiViewport.getWorldWidth() * 0.22f);
        float gap = 12f;
        float mapWidth = Math.max(200f, baseW - listWidth - gap);
        float x = (uiViewport.getWorldWidth() - (mapWidth + gap + listWidth)) * 0.5f;
        float y = (uiViewport.getWorldHeight() - baseH) * 0.5f;
        return new Rectangle(x, y, mapWidth, baseH);
    }

    private Rectangle listRect(Viewport uiViewport, Rectangle mapRect) {
        float listWidth = Math.min(260f, uiViewport.getWorldWidth() * 0.22f);
        float gap = 12f;
        float x = mapRect.x + mapRect.width + gap;
        float y = mapRect.y;
        return new Rectangle(x, y, listWidth, mapRect.height);
    }

    private void selectMarkerAt(float screenX, float screenY, Rectangle mapArea) {
        float centerX = mapArea.x + mapArea.width * 0.5f;
        float centerY = mapArea.y + mapArea.height * 0.5f;
        int nearest = -1;
        float bestDist2 = 100000f;
        for (MapMarker marker : markers) {
            float sx = centerX + (marker.x() - camX) * zoom;
            float sy = centerY + (marker.y() - camY) * zoom;
            float dx = screenX - sx;
            float dy = screenY - sy;
            float d2 = dx * dx + dy * dy;
            if (d2 < 12f * 12f && d2 < bestDist2) {
                bestDist2 = d2;
                nearest = marker.id();
            }
        }
        selectedMarkerId = nearest;
    }

    private boolean handleListClick(Vector2 world, Rectangle listArea) {
        if (!listArea.contains(world)) {
            return false;
        }
        float startY = listArea.y + listArea.height - 52f;
        int index = (int) ((startY - world.y) / 22f);
        if (index < 0) {
            return true;
        }
        if (index < markers.size()) {
            selectedMarkerId = markers.get(index).id();
        }
        return true;
    }

    private boolean handleButtonsClick(Vector2 world, Rectangle listArea) {
        float buttonHeight = 26f;
        float margin = 8f;
        float w = listArea.width - margin * 2f;
        float y = listArea.y + margin;
        String[] labels = {"Add", "Rename", "Delete", "Track"};
        for (String label : labels) {
            Rectangle r = new Rectangle(listArea.x + margin, y, w, buttonHeight);
            if (r.contains(world)) {
                switch (label) {
                    case "Add" -> promptAdd();
                    case "Rename" -> promptRename();
                    case "Delete" -> deleteSelected();
                    case "Track" -> toggleTrackSelected();
                }
                return true;
            }
            y += buttonHeight + 6f;
        }
        return false;
    }

    private void promptRename() {
        MapMarker marker = selectedMarker();
        if (marker == null) {
            return;
        }
        Gdx.input.getTextInput(new Input.TextInputListener() {
            @Override
            public void input(String text) {
                marker.setLabel(text);
            }

            @Override
            public void canceled() {
            }
        }, "Rename marker", marker.label(), marker.label());
    }

    private void promptAdd() {
        Gdx.input.getTextInput(new Input.TextInputListener() {
            @Override
            public void input(String text) {
                if (text == null || text.isBlank()) {
                    pendingPlacementLabel = "Marker " + idGen.get();
                } else {
                    pendingPlacementLabel = text;
                }
                awaitingPlacement = true;
            }

            @Override
            public void canceled() {
                // place default without prompt if user cancels? do nothing
            }
        }, "New marker label", "Marker " + idGen.get(), "Marker " + idGen.get());
    }

    private void deleteSelected() {
        if (selectedMarkerId < 0) {
            return;
        }
        markers.removeIf(m -> m.id() == selectedMarkerId);
        if (trackedMarkerId == selectedMarkerId) {
            trackedMarkerId = -1;
        }
        selectedMarkerId = -1;
    }

    private MapMarker selectedMarker() {
        for (MapMarker m : markers) {
            if (m.id() == selectedMarkerId) {
                return m;
            }
        }
        return null;
    }

    private void toggleTrackSelected() {
        MapMarker marker = selectedMarker();
        if (marker == null) {
            return;
        }
        if (trackedMarkerId == marker.id()) {
            trackedMarkerId = -1;
            marker.setTracked(false);
        } else {
            // clear previous
            for (MapMarker m : markers) {
                m.setTracked(false);
            }
            trackedMarkerId = marker.id();
            marker.setTracked(true);
        }
    }

    private Rectangle selectedListRow(Rectangle listArea) {
        if (selectedMarkerId < 0) {
            return null;
        }
        float rowY = listArea.y + listArea.height - 52f;
        int row = 0;
        for (MapMarker marker : markers) {
            float y = rowY - row * 22f;
            if (y < listArea.y + 50f) {
                break;
            }
            if (marker.id() == selectedMarkerId) {
                layout.setText(font, marker.label());
                float h = layout.height + 4f;
                return new Rectangle(listArea.x + 6f, y - layout.height + 2f, listArea.width - 12f, h);
            }
            row++;
        }
        return null;
    }

    private Color colorFor(MapMarker.Type type) {
        return switch (type) {
            case DEATH -> new Color(0.9f, 0.25f, 0.25f, 1f);
            default -> new Color(0.32f, 0.7f, 0.95f, 1f);
        };
    }

    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
    }
}
