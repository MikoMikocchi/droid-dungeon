package com.droiddungeon.render.lighting;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.math.Vector2;
import com.droiddungeon.grid.Grid;

/**
 * Casts 2D shadows from light sources based on wall/block geometry.
 * Uses ray casting to determine shadow edges and creates shadow geometry.
 */
public class ShadowCaster {
    private static final int RAY_COUNT = 180;  // Rays per full circle
    private static final float RAY_STEP = (float) (Math.PI * 2.0 / RAY_COUNT);

    private final List<Vector2> tempPoints = new ArrayList<>();

    /**
     * Result of shadow casting for a single light.
     */
    public static class ShadowResult {
        /** Vertices forming the lit polygon (in world coordinates) */
        public final float[] vertices;
        /** Number of valid vertices */
        public final int vertexCount;

        public ShadowResult(float[] vertices, int vertexCount) {
            this.vertices = vertices;
            this.vertexCount = vertexCount;
        }
    }

    /**
     * Cast shadows from a light source and return the lit area polygon.
     * The result is a polygon that represents the area visible from the light.
     *
     * @param light   The light source
     * @param grid    The world grid for collision detection
     * @param tileSize Size of each tile in world units
     * @return ShadowResult containing the lit polygon vertices
     */
    public ShadowResult castShadows(Light light, Grid grid, float tileSize) {
        tempPoints.clear();

        float lightX = light.getX();
        float lightY = light.getY();
        float radius = light.getCurrentRadius();

        // Cast rays in a circle around the light
        for (int i = 0; i < RAY_COUNT; i++) {
            float angle = i * RAY_STEP;
            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);

            // Ray march to find collision
            float hitDist = raycast(lightX, lightY, dx, dy, radius, grid, tileSize);

            float hitX = lightX + dx * hitDist;
            float hitY = lightY + dy * hitDist;
            tempPoints.add(new Vector2(hitX, hitY));
        }

        // Convert to float array
        float[] vertices = new float[tempPoints.size() * 2];
        for (int i = 0; i < tempPoints.size(); i++) {
            Vector2 p = tempPoints.get(i);
            vertices[i * 2] = p.x;
            vertices[i * 2 + 1] = p.y;
        }

        return new ShadowResult(vertices, tempPoints.size());
    }

    /**
     * Cast a ray and return distance to first blocking cell.
     */
    private float raycast(float startX, float startY, float dirX, float dirY,
                          float maxDist, Grid grid, float tileSize) {
        // DDA-style ray marching through grid cells
        float step = tileSize * 0.25f;  // Quarter-tile steps for accuracy
        float dist = 0f;

        while (dist < maxDist) {
            float checkX = startX + dirX * dist;
            float checkY = startY + dirY * dist;

            int tileX = (int) Math.floor(checkX / tileSize);
            int tileY = (int) Math.floor(checkY / tileSize);

            if (grid.hasBlock(tileX, tileY) && !grid.isTransparent(tileX, tileY)) {
                return findTileEdgeIntersection(startX, startY, dirX, dirY, tileX, tileY, tileSize, dist);
            }

            dist += step;
        }

        return maxDist;
    }

    /**
     * Find the exact point where ray hits tile edge.
     */
    private float findTileEdgeIntersection(float startX, float startY, float dirX, float dirY,
                                           int tileX, int tileY, float tileSize, float approxDist) {
        float tileLeft = tileX * tileSize;
        float tileRight = (tileX + 1) * tileSize;
        float tileBottom = tileY * tileSize;
        float tileTop = (tileY + 1) * tileSize;

        float minDist = approxDist;

        // Check intersection with each edge
        if (dirX > 0.0001f) {
            float t = (tileLeft - startX) / dirX;
            if (t > 0 && t < minDist) {
                float y = startY + dirY * t;
                if (y >= tileBottom && y <= tileTop) {
                    minDist = t;
                }
            }
        }
        if (dirX < -0.0001f) {
            float t = (tileRight - startX) / dirX;
            if (t > 0 && t < minDist) {
                float y = startY + dirY * t;
                if (y >= tileBottom && y <= tileTop) {
                    minDist = t;
                }
            }
        }
        if (dirY > 0.0001f) {
            float t = (tileBottom - startY) / dirY;
            if (t > 0 && t < minDist) {
                float x = startX + dirX * t;
                if (x >= tileLeft && x <= tileRight) {
                    minDist = t;
                }
            }
        }
        if (dirY < -0.0001f) {
            float t = (tileTop - startX) / dirY;
            if (t > 0 && t < minDist) {
                float x = startX + dirX * t;
                if (x >= tileLeft && x <= tileRight) {
                    minDist = t;
                }
            }
        }

        return Math.max(0f, minDist - tileSize * 0.02f);  // Small offset to avoid z-fighting
    }

    /**
     * Check if there's a clear line of sight between two points.
     */
    public boolean hasLineOfSight(float x1, float y1, float x2, float y2, Grid grid, float tileSize) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.001f) return true;

        dx /= dist;
        dy /= dist;

        float hitDist = raycast(x1, y1, dx, dy, dist, grid, tileSize);
        return hitDist >= dist - tileSize * 0.1f;
    }
}
