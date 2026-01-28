package com.droiddungeon;

import com.badlogic.gdx.ApplicationAdapter;
import com.droiddungeon.config.GameConfig;
import com.droiddungeon.runtime.GameRuntime;

public class DroidDungeonGame extends ApplicationAdapter {
    private GameRuntime runtime;

    @Override
    public void create() {
        runtime = new GameRuntime(GameConfig.defaults());
        runtime.create();
    }

    @Override
    public void resize(int width, int height) {
        if (runtime != null) {
            runtime.resize(width, height);
        }
    }

    @Override
    public void render() {
        if (runtime != null) {
            runtime.render();
        }
    }

    @Override
    public void dispose() {
        if (runtime != null) {
            runtime.dispose();
        }
    }
}
