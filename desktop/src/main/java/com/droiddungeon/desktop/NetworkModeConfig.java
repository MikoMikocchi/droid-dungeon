package com.droiddungeon.desktop;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public record NetworkModeConfig(boolean enabled, URI serverUri) {
  public static NetworkModeConfig fromSystemProps() {
    boolean enabled = Boolean.parseBoolean(System.getProperty("network", "false"));
    String host = System.getProperty("network.host", "localhost");
    int port = Integer.getInteger("network.port", 8080);
    String requestedId = System.getProperty("network.playerId", PlayerIdStore.load());
    String query =
        requestedId != null && !requestedId.isBlank()
            ? "?playerId=" + URLEncoder.encode(requestedId, StandardCharsets.UTF_8)
            : "";
    return new NetworkModeConfig(enabled, URI.create("ws://" + host + ":" + port + "/ws" + query));
  }
}
