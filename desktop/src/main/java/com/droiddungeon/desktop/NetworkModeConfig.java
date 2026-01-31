package com.droiddungeon.desktop;

import java.net.URI;

public record NetworkModeConfig(boolean enabled, URI serverUri) {
    public static NetworkModeConfig fromSystemProps() {
        boolean enabled = Boolean.parseBoolean(System.getProperty("network", "false"));
        String host = System.getProperty("network.host", "localhost");
        int port = Integer.getInteger("network.port", 8080);
        return new NetworkModeConfig(enabled, URI.create("ws://" + host + ":" + port + "/ws"));
    }
}
