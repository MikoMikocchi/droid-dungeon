package com.droiddungeon.desktop;

import com.droiddungeon.save.SaveGame;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Handles single-player save files on disk. */
public final class SaveManager {
  private final ObjectMapper mapper =
      new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
  private final Path root;

  public SaveManager() {
    this(Path.of(System.getProperty("user.home"), ".droiddungeon", "worlds"));
  }

  public SaveManager(Path root) {
    this.root = root;
  }

  public List<SaveSummary> listWorlds() {
    if (!Files.exists(root)) return List.of();
    try {
      List<SaveSummary> out = new ArrayList<>();
      Files.list(root)
          .filter(Files::isDirectory)
          .forEach(
              dir -> {
                Path file = dir.resolve("save.json");
                if (Files.exists(file)) {
                  try {
                    SaveGame save = mapper.readValue(file.toFile(), SaveGame.class);
                    out.add(
                        new SaveSummary(dir.getFileName().toString(), save.seed, save.updatedAt));
                  } catch (IOException ignored) {
                  }
                }
              });
      out.sort(Comparator.comparingLong(SaveSummary::updatedAt).reversed());
      return out;
    } catch (IOException e) {
      return List.of();
    }
  }

  public SaveGame load(String worldName) throws IOException {
    Path file = worldDir(worldName).resolve("save.json");
    return mapper.readValue(file.toFile(), SaveGame.class);
  }

  public void save(SaveGame save) throws IOException {
    Objects.requireNonNull(save, "save");
    Path dir = worldDir(save.name);
    Files.createDirectories(dir);
    Path file = dir.resolve("save.json");
    mapper.writeValue(file.toFile(), save);
  }

  public void delete(String worldName) throws IOException {
    Path dir = worldDir(worldName);
    if (!Files.exists(dir)) return;
    Files.walk(dir)
        .sorted(Comparator.reverseOrder())
        .forEach(
            p -> {
              try {
                Files.deleteIfExists(p);
              } catch (IOException ignored) {
              }
            });
  }

  public void rename(String from, String to) throws IOException {
    if (from == null || to == null || from.isBlank() || to.isBlank()) return;
    Path src = worldDir(from);
    Path dst = worldDir(to);
    if (!Files.exists(src)) return;
    Files.createDirectories(dst.getParent());
    Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
  }

  public void copy(String from, String to) throws IOException {
    if (from == null || to == null || from.isBlank() || to.isBlank()) return;
    Path src = worldDir(from);
    Path dst = worldDir(to);
    if (!Files.exists(src)) return;
    Files.walk(src)
        .forEach(
            p -> {
              try {
                Path relative = src.relativize(p);
                Path target = dst.resolve(relative);
                if (Files.isDirectory(p)) {
                  Files.createDirectories(target);
                } else {
                  Files.createDirectories(target.getParent());
                  Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                }
              } catch (IOException ignored) {
              }
            });
  }

  public SaveGame createEmpty(String worldName, long seed) throws IOException {
    SaveGame save =
        new SaveGame(
            worldName,
            seed,
            System.currentTimeMillis(),
            0,
            0,
            0,
            0,
            null,
            null,
            new SaveGame.ItemStackState[0],
            List.of(),
            List.of(),
            1);
    save(save);
    return save;
  }

  private Path worldDir(String name) {
    return root.resolve(name);
  }

  public record SaveSummary(String name, long seed, long updatedAt) {}
}
