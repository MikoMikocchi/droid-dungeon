package com.droiddungeon.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.io.IOException;
import java.util.stream.Collectors;

final class WorldListScreen extends ScreenAdapter {
  private final GameApp game;
  private final SaveManager saves;
  private final Stage stage;
  private final List<String> worldList;
  private final com.badlogic.gdx.scenes.scene2d.ui.Skin skin;

  WorldListScreen(GameApp game, SaveManager saves) {
    this.game = game;
    this.saves = saves;
    this.stage = new Stage(new ScreenViewport());
    this.skin = UiSkinFactory.create();
    Gdx.input.setInputProcessor(stage);
    worldList = new List<>(skin);
    rebuildList();
    buildUi();
  }

  private void rebuildList() {
    var names =
        saves.listWorlds().stream().map(SaveManager.SaveSummary::name).collect(Collectors.toList());
    worldList.setItems(names.toArray(new String[0]));
  }

  private void buildUi() {
    Table root = new Table();
    root.setFillParent(true);
    root.defaults().pad(6f);

    // Top buttons
    Table buttons = new Table();
    TextButton create = new TextButton("Create", skin);
    create.addListener(
        new ClickListener() {
          @Override
          public void clicked(InputEvent event, float x, float y) {
            game.setScreen(new CreateWorldScreen(game, saves, WorldListScreen.this));
          }
        });
    TextButton rename = new TextButton("Rename", skin);
    rename.addListener(
        new ClickListener() {
          @Override
          public void clicked(InputEvent event, float x, float y) {
            promptRename();
          }
        });
    TextButton copy = new TextButton("Duplicate", skin);
    copy.addListener(
        new ClickListener() {
          @Override
          public void clicked(InputEvent event, float x, float y) {
            promptCopy();
          }
        });
    TextButton delete = new TextButton("Delete", skin);
    delete.addListener(
        new ClickListener() {
          @Override
          public void clicked(InputEvent event, float x, float y) {
            promptDelete();
          }
        });
    buttons.add(create).padRight(4f);
    buttons.add(rename).padRight(4f);
    buttons.add(copy).padRight(4f);
    buttons.add(delete);

    // List area
    ScrollPane scroll = new ScrollPane(worldList, skin);
    scroll.setFadeScrollBars(false);
    scroll.setScrollbarsOnTop(true);
    scroll.setScrollingDisabled(true, false);
    scroll.setSmoothScrolling(true);
    scroll.setForceScroll(false, true);
    scroll.setFadeScrollBars(false);
    scroll.setScrollingDisabled(true, false);

    // Bottom actions
    Table bottom = new Table();
    TextButton play = new TextButton("Play", skin);
    play.addListener(
        new ClickListener() {
          @Override
          public void clicked(InputEvent event, float x, float y) {
            startSelected();
          }
        });
    TextButton back = new TextButton("Back", skin);
    back.addListener(
        new ClickListener() {
          @Override
          public void clicked(InputEvent event, float x, float y) {
            game.setScreen(new MainMenuScreen(game, saves));
          }
        });
    bottom.add(play).padRight(10f);
    bottom.add(back);

    root.add(buttons).top().row();
    root.add(scroll).grow().row();
    root.add(bottom).padTop(10f);

    stage.addActor(root);
  }

  private String selectedWorld() {
    return worldList.getSelected();
  }

  private void startSelected() {
    String world = selectedWorld();
    if (world == null || world.isBlank()) return;
    try {
      var save = saves.load(world);
      game.setScreen(new GameplayScreen(game, saves, world, save));
    } catch (IOException ex) {
      showError("Failed to load world: " + ex.getMessage());
    }
  }

  private void promptRename() {
    String world = selectedWorld();
    if (world == null) return;
    TextField tf = new TextField(world, skin);
    Dialog dialog =
        new Dialog("Rename", skin) {
          @Override
          protected void result(Object object) {
            boolean ok = Boolean.TRUE.equals(object);
            if (ok) {
              try {
                saves.rename(world, tf.getText());
                rebuildList();
              } catch (IOException e) {
                showError("Error: " + e.getMessage());
              }
            }
          }
        };
    dialog.text("New name:");
    dialog.getContentTable().row();
    dialog.getContentTable().add(tf).width(240f);
    dialog.button("OK", true);
    dialog.button("Cancel", false);
    dialog.show(stage);
  }

  private void promptCopy() {
    String world = selectedWorld();
    if (world == null) return;
    TextField tf = new TextField(world + "_copy", skin);
    Dialog dialog =
        new Dialog("Duplicate world", skin) {
          @Override
          protected void result(Object object) {
            boolean ok = Boolean.TRUE.equals(object);
            if (ok) {
              try {
                saves.copy(world, tf.getText());
                rebuildList();
              } catch (IOException e) {
                showError("Error: " + e.getMessage());
              }
            }
          }
        };
    dialog.text("Copy name:");
    dialog.getContentTable().row();
    dialog.getContentTable().add(tf).width(240f);
    dialog.button("OK", true);
    dialog.button("Cancel", false);
    dialog.show(stage);
  }

  private void promptDelete() {
    String world = selectedWorld();
    if (world == null) return;
    Dialog dialog =
        new Dialog("Delete world", skin) {
          @Override
          protected void result(Object object) {
            boolean ok = Boolean.TRUE.equals(object);
            if (ok) {
              try {
                saves.delete(world);
                rebuildList();
              } catch (IOException e) {
                showError("Error: " + e.getMessage());
              }
            }
          }
        };
    dialog.text("Delete \"" + world + "\"?");
    dialog.button("Yes", true);
    dialog.button("No", false);
    dialog.show(stage);
  }

  private void showError(String message) {
    Dialog dialog = new Dialog("Error", skin);
    dialog.text(message);
    dialog.button("OK");
    dialog.show(stage);
  }

  @Override
  public void render(float delta) {
    Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    stage.act(delta);
    stage.draw();
  }

  @Override
  public void resize(int width, int height) {
    stage.getViewport().update(width, height, true);
  }

  @Override
  public void dispose() {
    stage.dispose();
  }
}
