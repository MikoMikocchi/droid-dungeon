package com.droiddungeon.desktop;

import com.droiddungeon.config.GameConfig;
import com.droiddungeon.runtime.GameRuntime;

/**
 * Helper to construct a fully-wired GameRuntime for the desktop client.
 */
public final class DesktopRuntimeFactory {
    private DesktopRuntimeFactory() {}

    public static GameRuntime createDefault() {
        var textureLoader = new GdxTextureLoader();
        var clientAssets = new DesktopAssets();
        var buffer = new com.droiddungeon.runtime.NetworkSnapshotBuffer();
        return new GameRuntime(GameConfig.defaults(), textureLoader, clientAssets, null, buffer, false);
    }
}
