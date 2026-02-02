package com.droiddungeon.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.io.IOException;

final class CreateWorldScreen extends ScreenAdapter {
  private final GameApp game;
  private final SaveManager saves;
  private final WorldListScreen backScreen;
  private final Stage stage;
  private final com.badlogic.gdx.scenes.scene2d.ui.Skin skin;

  CreateWorldScreen(GameApp game, SaveManager saves, WorldListScreen backScreen) {
    this.game = game;
    this.saves = saves;
    this.backScreen = backScreen;
    this.stage = new Stage(new ScreenViewport());
    this.skin = UiSkinFactory.create();
    Gdx.input.setInputProcessor(stage);
    buildUi();
  }

  private void buildUi() {
    Table root = new Table();
    root.setFillParent(true);
    root.defaults().pad(8f);

    Label title = new Label("Create World", skin);
    TextField nameField = new TextField("world", skin);
    TextField seedField = new TextField(String.valueOf(System.currentTimeMillis()), skin);

    TextButton back = new TextButton("Back", skin);
    back.addListener(
        e -> {
          if (back.isPressed()) game.setScreen(backScreen);
          return true;
        });

    TextButton start = new TextButton("Start", skin);
    start.addListener(
        e -> {
          if (!start.isPressed()) return true;
          String name = nameField.getText().trim();
          if (name.isEmpty()) return true;
          long seed;
          try {
            seed = Long.parseLong(seedField.getText().trim());
          } catch (NumberFormatException ex) {
            seed = System.currentTimeMillis();
          }
          try {
            saves.createEmpty(name, seed);
            var save = saves.load(name);
            game.setScreen(new GameplayScreen(game, saves, name, save));
          } catch (IOException ex) {
            // show simple error dialog
            com.badlogic.gdx.scenes.scene2d.ui.Dialog d =
                new com.badlogic.gdx.scenes.scene2d.ui.Dialog("Error", skin);
            d.text("Failed to create: " + ex.getMessage());
            d.button("OK");
            d.show(stage);
          }
          return true;
        });

    root.add(title).colspan(2).row();
    root.add(new Label("Name:", skin)).right();
    root.add(nameField).width(260f).row();
    root.add(new Label("Seed:", skin)).right();
    root.add(seedField).width(260f).row();
    root.add(start).width(140f).padTop(12f);
    root.add(back).width(140f).padTop(12f);

    stage.addActor(root);
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
