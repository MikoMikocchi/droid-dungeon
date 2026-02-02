package com.droiddungeon.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Simple persistence for player identifiers across reconnects. */
public final class PlayerIdStore {
  private static final Path STORE_PATH =
      Paths.get(System.getProperty("user.home"), ".droiddungeon", "player-id.txt");

  private PlayerIdStore() {}

  public static String load() {
    try {
      if (Files.exists(STORE_PATH)) {
        String id = Files.readString(STORE_PATH, StandardCharsets.UTF_8).trim();
        return id.isEmpty() ? null : id;
      }
    } catch (IOException ignored) {
    }
    return null;
  }

  public static void save(String id) {
    if (id == null || id.isBlank()) return;
    try {
      Files.createDirectories(STORE_PATH.getParent());
      Files.writeString(STORE_PATH, id, StandardCharsets.UTF_8);
    } catch (IOException ignored) {
    }
  }
}
