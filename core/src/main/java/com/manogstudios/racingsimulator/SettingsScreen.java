package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.graphics.Color;

public class SettingsScreen implements Screen {

    private final Game game;
    private Stage stage;
    private Skin skin;
    private Texture settingsButtonTexture;
    private Texture settingsButtonDownTexture;
    private Texture settingsButtonOverTexture;

    public SettingsScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        skin = new Skin(Gdx.files.internal("uiskin.json"));

        settingsButtonTexture = new Texture(Gdx.files.internal("Settings_Button.png"));
        settingsButtonDownTexture = new Texture(Gdx.files.internal("Settings_Button_Down.png"));
        settingsButtonOverTexture = new Texture(Gdx.files.internal("Settings_Button_Over.png"));

        TextButton.TextButtonStyle settingsButtonStyle = new TextButton.TextButtonStyle();
        settingsButtonStyle.font = skin.getFont("default-font");

        settingsButtonStyle.up = new TextureRegionDrawable(new TextureRegion(settingsButtonTexture));
        settingsButtonStyle.down = new TextureRegionDrawable(new TextureRegion(settingsButtonDownTexture));
        settingsButtonStyle.over = new TextureRegionDrawable(new TextureRegion(settingsButtonOverTexture));

        settingsButtonStyle.fontColor = Color.WHITE;
        settingsButtonStyle.downFontColor = Color.LIGHT_GRAY;
        settingsButtonStyle.overFontColor = Color.GOLD;

        Label titleLabel = new Label("Settings", skin);
        titleLabel.setFontScale(2.0f);

        final TextButton fullscreenButton = new TextButton(getFullscreenText(), settingsButtonStyle);
        final TextButton mfaButton = new TextButton("Two-Factor Authentication", settingsButtonStyle);
        TextButton controlsButton = new TextButton("Controls / Help", settingsButtonStyle);
        TextButton logoutButton = new TextButton("Log out", settingsButtonStyle);
        TextButton backButton = new TextButton("Back", settingsButtonStyle);


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

        controlsButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Dialog controlsDialog = new Dialog("Controls", skin);

                Label controlsLabel = new Label(
                    "W = Accelerate\n" +
                        "S = Brake / Reverse\n" +
                        "A = Steer Left\n" +
                        "D = Steer Right\n" +
                        "ESC = Pause or go back\n" +
                        "ENTER / SPACE = Confirm on some menus\n" +
                        "Mouse = Click buttons and menus\n\n" +
                        "Notes:\n" +
                        "- In gameplay, ESC opens the pause menu.\n" +
                        "- In menus, ESC usually goes back to the previous screen.\n" +
                        "- Drag Race uses W to accelerate and S to brake.",
                    skin
                );

                controlsLabel.setWrap(true);
                controlsLabel.setAlignment(Align.left);

                controlsDialog.getContentTable().add(controlsLabel).width(420).pad(20);
                controlsDialog.button("OK");
                controlsDialog.show(stage);
            }
        });

        logoutButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Dialog confirmDialog = new Dialog("Confirm Logout", skin) {
                    @Override
                    protected void result(Object object) {
                        if ((Boolean) object) {
                            game.setScreen(new LoginScreen(game));
                        }
                    }
                };

                confirmDialog.text("Are you sure you want to log out?");
                confirmDialog.button("Yes", true);
                confirmDialog.button("No", false);
                confirmDialog.show(stage);
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

        table.add(controlsButton).width(320).height(55).padBottom(20);
        table.row();

        table.add(logoutButton).width(320).height(55).padBottom(20);
        table.row();

        table.add(backButton).width(320).height(55);

        stage.addActor(table);
        Gdx.input.setInputProcessor(stage);

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACKSPACE) {
                    game.setScreen(new PlayScreen(game));
                    return true;
                }
                return false;
            }
        });
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

        if (settingsButtonTexture != null) settingsButtonTexture.dispose();
        if (settingsButtonDownTexture != null) settingsButtonDownTexture.dispose();
        if (settingsButtonOverTexture != null) settingsButtonOverTexture.dispose();
    }
}
