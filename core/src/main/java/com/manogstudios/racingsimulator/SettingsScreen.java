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
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

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
                showControlsPopup();
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

    private void showControlsPopup() {
        TextButton.TextButtonStyle popupButtonStyle = createModernPopupButtonStyle();

        Dialog controlsDialog = new Dialog("Controls / Help", createModernDialogStyle());
        controlsDialog.getTitleLabel().setAlignment(Align.center);
        controlsDialog.getTitleLabel().setFontScale(1.2f);
        controlsDialog.getContentTable().pad(28);

        Label subtitle = new Label(
            "Use these controls to navigate menus and drive during gameplay.",
            skin
        );
        subtitle.setWrap(true);
        subtitle.setAlignment(Align.center);
        subtitle.setColor(Color.LIGHT_GRAY);

        controlsDialog.getContentTable().add(subtitle).width(560).padBottom(20).row();

        Table controlsPanel = new Table();
        controlsPanel.setBackground(darkPanelDrawable());
        controlsPanel.pad(18);
        controlsPanel.defaults().padBottom(10).left();

        controlsPanel.add(createControlRow("W", "Accelerate")).width(520).row();
        controlsPanel.add(createControlRow("S", "Brake / Reverse")).width(520).row();
        controlsPanel.add(createControlRow("A", "Steer Left")).width(520).row();
        controlsPanel.add(createControlRow("D", "Steer Right")).width(520).row();
        controlsPanel.add(createControlRow("ESC", "Pause during gameplay / go back in menus")).width(520).row();
        controlsPanel.add(createControlRow("ENTER / SPACE", "Confirm on some menus")).width(520).row();
        controlsPanel.add(createControlRow("Mouse", "Click buttons and menu options")).width(520).row();

        controlsDialog.getContentTable().add(controlsPanel).width(580).padBottom(18).row();

        Table notesPanel = new Table();
        notesPanel.setBackground(darkPanelDrawable());
        notesPanel.pad(16);

        Label notesLabel = new Label(
            "Notes:\n" +
                "- In gameplay, ESC opens the pause menu.\n" +
                "- In menus, ESC usually returns to the previous screen.\n" +
                "- Drag Race uses W to accelerate and S to brake.",
            skin
        );

        notesLabel.setWrap(true);
        notesLabel.setAlignment(Align.left);
        notesLabel.setColor(Color.LIGHT_GRAY);

        notesPanel.add(notesLabel).width(520);

        controlsDialog.getContentTable().add(notesPanel).width(580).padBottom(20).row();

        TextButton closeButton = new TextButton("Close", popupButtonStyle);
        closeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                controlsDialog.hide();
            }
        });

        controlsDialog.getContentTable().add(closeButton).width(170).height(48).row();

        controlsDialog.show(stage);

        float dialogWidth = Math.min(680f, stage.getWidth() - 80f);
        float dialogHeight = Math.min(650f, stage.getHeight() - 80f);

        controlsDialog.setSize(dialogWidth, dialogHeight);
        controlsDialog.setPosition(
            (stage.getWidth() - dialogWidth) / 2f,
            (stage.getHeight() - dialogHeight) / 2f
        );
    }

    private Table createControlRow(String key, String action) {
        Table row = new Table();

        Label keyLabel = new Label(key, skin);
        keyLabel.setAlignment(Align.center);
        keyLabel.setColor(Color.WHITE);
        keyLabel.setFontScale(1.05f);

        Table keyBox = new Table();
        keyBox.setBackground(keyBoxDrawable());
        keyBox.pad(6);
        keyBox.add(keyLabel).center();

        Label actionLabel = new Label(action, skin);
        actionLabel.setColor(Color.LIGHT_GRAY);
        actionLabel.setWrap(true);

        row.add(keyBox).width(130).height(36).padRight(14);
        row.add(actionLabel).width(360).left();

        return row;
    }

    private Window.WindowStyle createModernDialogStyle() {
        Window.WindowStyle style = new Window.WindowStyle(skin.get(Window.WindowStyle.class));

        style.background = skin.newDrawable(
            "default-round",
            new Color(0.035f, 0.035f, 0.045f, 0.97f)
        );

        style.titleFont = skin.getFont("default-font");
        style.titleFontColor = Color.WHITE;

        return style;
    }

    private TextButton.TextButtonStyle createModernPopupButtonStyle() {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();

        style.up = skin.newDrawable(
            "default-round",
            new Color(0.16f, 0.16f, 0.20f, 1f)
        );

        style.down = skin.newDrawable(
            "default-round",
            new Color(0.08f, 0.08f, 0.11f, 1f)
        );

        style.over = skin.newDrawable(
            "default-round",
            new Color(0.24f, 0.24f, 0.30f, 1f)
        );

        style.font = skin.getFont("default-font");
        style.fontColor = Color.WHITE;
        style.overFontColor = Color.WHITE;
        style.downFontColor = Color.LIGHT_GRAY;

        return style;
    }

    private Drawable darkPanelDrawable() {
        return skin.newDrawable(
            "default-round",
            new Color(0.09f, 0.09f, 0.12f, 0.96f)
        );
    }

    private Drawable keyBoxDrawable() {
        return skin.newDrawable(
            "default-round",
            new Color(0.16f, 0.16f, 0.22f, 1f)
        );
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
