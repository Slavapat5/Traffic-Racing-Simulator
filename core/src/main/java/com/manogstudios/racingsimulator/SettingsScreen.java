package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class SettingsScreen implements Screen {

    private final Game game;
    private Stage stage;
    private Skin skin;

    public SettingsScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        skin = new Skin(Gdx.files.internal("uiskin.json"));

        Label titleLabel = new Label("Settings", skin);
        titleLabel.setFontScale(2.0f);

        final TextButton fullscreenButton = new TextButton(getFullscreenText(), skin);
        TextButton backButton = new TextButton("Back", skin);

        final TextButton mfaButton = new TextButton("Two-Factor Authentication", skin);

        fullscreenButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                boolean enableFullscreen = !Gdx.graphics.isFullscreen();

                if (enableFullscreen) {
                    Graphics.DisplayMode displayMode = Gdx.graphics.getDisplayMode();
                    Gdx.graphics.setFullscreenMode(displayMode);
                } else {
                    Gdx.graphics.setWindowedMode(1600, 900);
                }

                GameSettings.setFullscreenEnabled(enableFullscreen);
                fullscreenButton.setText(getFullscreenText());
            }
        });

        mfaButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new MfaSetupScreen(game));
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new PlayScreen(game));
            }
        });

        Table table = new Table();
        table.setFillParent(true);
        table.center();

        table.add(titleLabel).padBottom(40);
        table.row();

        table.add(fullscreenButton).width(320).height(55).padBottom(25);
        table.row();

        table.add(mfaButton).width(320).height(55).padBottom(20);
        table.row();

        table.add(backButton).width(320).height(55);

        stage.addActor(table);
        Gdx.input.setInputProcessor(stage);
    }

    private String getFullscreenText() {
        return "Fullscreen: " + (Gdx.graphics.isFullscreen() ? "ON" : "OFF");
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(Math.min(delta, 1 / 30f));
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();
    }
}
