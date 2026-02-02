package com.droiddungeon.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.droiddungeon.DroidDungeonGame;
import com.droiddungeon.runtime.NetworkSnapshotBuffer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class DesktopLauncher {
  public static void main(String[] args) {
    Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
    config.setTitle("Droid Dungeon");
    config.setWindowedMode(1920, 1080);
    config.useVsync(true);
    config.setForegroundFPS(60);
    // Enable stencil buffer so weapon fan masking works.
    config.setBackBufferConfig(
        8,
        8,
        8,
        8, // RGBA
        16, // depth
        8, // stencil
        0 // samples (no MSAA)
        );

    boolean network = Boolean.parseBoolean(System.getProperty("network", "false"));
    NetworkSnapshotBuffer buffer = new NetworkSnapshotBuffer();
    com.droiddungeon.net.NetworkClientAdapter netClient = null;
    if (network) {
      int port = Integer.parseInt(System.getProperty("network.port", "8080"));
      String requestedId = System.getProperty("network.playerId", PlayerIdStore.load());
      String query =
          requestedId != null && !requestedId.isBlank()
              ? "?playerId=" + URLEncoder.encode(requestedId, StandardCharsets.UTF_8)
              : "";
      var uri =
          java.net.URI.create(
              "ws://"
                  + System.getProperty("network.host", "localhost")
                  + ":"
                  + port
                  + "/ws"
                  + query);
      netClient = new NetworkClient(uri, buffer);
    }
    @SuppressWarnings("unused")
    Lwjgl3Application app =
        new Lwjgl3Application(
            new DroidDungeonGame(
                new GdxTextureLoader(), new DesktopAssets(), netClient, buffer, network),
            config);
  }
}
