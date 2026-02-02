package com.droiddungeon.desktop;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;

/** Builds a tiny runtime-generated skin to avoid shipping external assets. */
public final class UiSkinFactory {
  private UiSkinFactory() {}

  public static Skin create() {
    Skin skin = new Skin();
    BitmapFont font = new BitmapFont();
    skin.add("default-font", font);

    skin.add("white", new TextureRegion(singlePixel(Color.WHITE)));
    skin.add("gray", new TextureRegion(singlePixel(new Color(0.25f, 0.25f, 0.25f, 1f))));
    skin.add("light", new TextureRegion(singlePixel(new Color(0.35f, 0.35f, 0.35f, 1f))));
    skin.add("accent", new TextureRegion(singlePixel(new Color(0.18f, 0.43f, 0.86f, 1f))));

    Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
    skin.add("default", labelStyle);

    TextButton.TextButtonStyle button = new TextButton.TextButtonStyle();
    button.font = font;
    button.up = skin.getDrawable("light");
    button.down = skin.getDrawable("gray");
    button.checked = skin.getDrawable("accent");
    button.over = skin.getDrawable("accent");
    skin.add("default", button);

    TextField.TextFieldStyle tf = new TextField.TextFieldStyle();
    tf.font = font;
    tf.fontColor = Color.WHITE;
    tf.background = skin.getDrawable("gray");
    tf.cursor = skin.getDrawable("white");
    tf.selection = skin.getDrawable("accent");
    skin.add("default", tf);

    List.ListStyle list = new List.ListStyle();
    list.font = font;
    list.fontColorSelected = Color.WHITE;
    list.fontColorUnselected = Color.LIGHT_GRAY;
    list.selection = skin.getDrawable("accent");
    skin.add("default", list);

    ScrollPane.ScrollPaneStyle sp = new ScrollPane.ScrollPaneStyle();
    sp.background = skin.getDrawable("gray");
    skin.add("default", sp);

    SelectBox.SelectBoxStyle sb = new SelectBox.SelectBoxStyle();
    sb.font = font;
    sb.fontColor = Color.WHITE;
    sb.background = skin.getDrawable("gray");
    sb.scrollStyle = sp;
    sb.listStyle = list;
    skin.add("default", sb);

    Window.WindowStyle ws = new Window.WindowStyle();
    ws.titleFont = font;
    ws.background = skin.getDrawable("gray");
    skin.add("default", ws);

    return skin;
  }

  private static Texture singlePixel(Color color) {
    Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    pix.setColor(color);
    pix.fill();
    Texture tex = new Texture(pix);
    pix.dispose();
    return tex;
  }
}
