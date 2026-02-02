package com.droiddungeon.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import java.util.EnumMap;
import java.util.Map;

/** Central place to configure key bindings. Supports multiple keys per action. */
public final class InputBindings {
  private final Map<InputAction, int[]> keys = new EnumMap<>(InputAction.class);

  private InputBindings() {}

  public static InputBindings defaults() {
    InputBindings bindings = new InputBindings();
    bindings.map(InputAction.TOGGLE_INVENTORY, Input.Keys.E);
    bindings.map(InputAction.DROP_ITEM, Input.Keys.Q);
    bindings.map(InputAction.PICK_UP_ITEM, Input.Keys.F);
    bindings.map(InputAction.TOGGLE_MAP, Input.Keys.M);
    bindings.map(InputAction.CLOSE_MAP, Input.Keys.ESCAPE);
    bindings.map(InputAction.RESTART_RUN, Input.Keys.R);
    // Toggle debug overlay with F3
    bindings.map(InputAction.TOGGLE_DEBUG, Input.Keys.F3);
    return bindings;
  }

  public void map(InputAction action, int... keyCodes) {
    if (action == null || keyCodes == null || keyCodes.length == 0) {
      return;
    }
    keys.put(action, keyCodes);
  }

  public boolean isJustPressed(InputAction action) {
    int[] mapped = keys.get(action);
    if (mapped == null) {
      return false;
    }
    for (int key : mapped) {
      if (Gdx.input.isKeyJustPressed(key)) {
        return true;
      }
    }
    return false;
  }

  public boolean isPressed(InputAction action) {
    int[] mapped = keys.get(action);
    if (mapped == null) {
      return false;
    }
    for (int key : mapped) {
      if (Gdx.input.isKeyPressed(key)) {
        return true;
      }
    }
    return false;
  }
}
