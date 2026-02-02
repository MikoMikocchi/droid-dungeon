package com.droiddungeon.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

final class MainMenuScreen extends ScreenAdapter {
  private final GameApp game;
  private final SaveManager saves;
  private final Stage stage;

  MainMenuScreen(GameApp game, SaveManager saves) {
    this.game = game;
    this.saves = saves;
    this.stage = new Stage(new ScreenViewport());
    Gdx.input.setInputProcessor(stage);
    buildUi();
  }

  private void buildUi() {
    var skin = UiSkinFactory.create();
    Table root = new Table();
    root.setFillParent(true);
    root.defaults().pad(10f).width(260f).height(50f);

    Label title = new Label("Droid Dungeon", skin);
    root.add(title).colspan(1).padBottom(30f).row();

    TextButton single = new TextButton("Singleplayer", skin);
    single.addListener(
        e -> {
          if (single.isPressed()) {
            game.setScreen(new WorldListScreen(game, saves));
          }
          return true;
        });
    root.add(single).row();

    TextButton multi = new TextButton("Multiplayer (coming soon)", skin);
    multi.setDisabled(true);
    root.add(multi).row();

    TextButton exit = new TextButton("Quit", skin);
    exit.addListener(
        e -> {
          if (exit.isPressed()) {
            Gdx.app.exit();
          }
          return true;
        });
    root.add(exit);

    stage.addActor(root);
  }

  @Override
  public void render(float delta) {
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
