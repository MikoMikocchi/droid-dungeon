package com.droiddungeon.render;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.droiddungeon.grid.Grid;
import com.droiddungeon.grid.Player;

public final class WorldRenderer {
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();

    public void render(Stage stage, Grid grid, Player player) {
        stage.getViewport().apply();
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);

        float gridOriginX = (stage.getViewport().getWorldWidth() - grid.getWorldWidth()) * 0.5f;
        float gridOriginY = (stage.getViewport().getWorldHeight() - grid.getWorldHeight()) * 0.5f;
        float tileSize = grid.getTileSize();

        renderTileFill(grid, gridOriginX, gridOriginY, tileSize);
        renderGridLines(grid, gridOriginX, gridOriginY, tileSize);
        renderPlayer(player, gridOriginX, gridOriginY, tileSize);
    }

    private void renderTileFill(Grid grid, float gridOriginX, float gridOriginY, float tileSize) {
        shapeRenderer.begin(ShapeType.Filled);
        for (int y = 0; y < grid.getRows(); y++) {
            for (int x = 0; x < grid.getColumns(); x++) {
                if (((x + y) & 1) == 0) {
                    shapeRenderer.setColor(0.06f, 0.06f, 0.07f, 1f);
                } else {
                    shapeRenderer.setColor(0.04f, 0.04f, 0.05f, 1f);
                }
                shapeRenderer.rect(
                        gridOriginX + x * tileSize,
                        gridOriginY + y * tileSize,
                        tileSize,
                        tileSize
                );
            }
        }
        shapeRenderer.end();
    }

    private void renderGridLines(Grid grid, float gridOriginX, float gridOriginY, float tileSize) {
        shapeRenderer.begin(ShapeType.Line);
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 1f);

        for (int x = 0; x <= grid.getColumns(); x++) {
            float worldX = gridOriginX + x * tileSize;
            shapeRenderer.line(worldX, gridOriginY, worldX, gridOriginY + grid.getWorldHeight());
        }
        for (int y = 0; y <= grid.getRows(); y++) {
            float worldY = gridOriginY + y * tileSize;
            shapeRenderer.line(gridOriginX, worldY, gridOriginX + grid.getWorldWidth(), worldY);
        }
        shapeRenderer.end();
    }

    private void renderPlayer(Player player, float gridOriginX, float gridOriginY, float tileSize) {
        float centerX = gridOriginX + (player.getRenderX() + 0.5f) * tileSize;
        float centerY = gridOriginY + (player.getRenderY() + 0.5f) * tileSize;
        float radius = tileSize * 0.42f;

        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0.15f, 0.75f, 1f, 1f);
        shapeRenderer.circle(centerX, centerY, radius, MathUtils.clamp((int) (radius * 2.5f), 16, 64));
        shapeRenderer.end();
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
