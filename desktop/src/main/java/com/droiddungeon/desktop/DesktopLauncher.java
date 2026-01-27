package com.droiddungeon.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.droiddungeon.DroidDungeonGame;

public class DesktopLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Droid Dungeon");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        config.setForegroundFPS(60);
        // Enable stencil buffer so weapon fan masking works.
        config.setBackBufferConfig(
                8, 8, 8, 8,   // RGBA
                16,           // depth
                8,            // stencil
                0             // samples (no MSAA)
        );

        @SuppressWarnings("unused")
        Lwjgl3Application app = new Lwjgl3Application(new DroidDungeonGame(), config);
    }
}
