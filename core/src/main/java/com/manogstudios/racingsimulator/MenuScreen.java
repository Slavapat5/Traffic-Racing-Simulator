package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputListener;

public class MenuScreen implements Screen {
    private final Game game;
    private Stage stage;
    private Skin skin;
    private Label cashLabel;
    private Label usernameLabel;
    private Texture backgroundTexture;
    private SpriteBatch batch;


    // settings button textures
    private Texture settingsUpTex, settingsDownTex, settingsOverTex;

    public MenuScreen(Game game) {
        this.game = game;
    }

    public Label getCashLabel() {
        return cashLabel;
    }

    @Override
    public void show() {



        if (!SupabaseAuth.isLoggedIn) {
            game.setScreen(new LoginScreen(game));
            return;
        }

        backgroundTexture = new Texture(Gdx.files.internal("Menu_Background.png"));
        batch = new SpriteBatch();

        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        skin = new Skin(Gdx.files.internal("uiskin.json"));

        // ===== MAIN CENTER CARD WITH BUTTONS =====
        Table centerRoot = new Table();
        centerRoot.setFillParent(true);
        centerRoot.center();
        stage.addActor(centerRoot);

        // Dark card behind the big menu buttons
        Table card = new Table(skin);
        card.pad(30);
        card.defaults().pad(10);


        ImageTextButton.ImageTextButtonStyle dealershipStyle = new ImageTextButton.ImageTextButtonStyle();
        dealershipStyle.up   = new TextureRegionDrawable(new TextureRegion(new Texture("MenuButton4.png")));
        dealershipStyle.down = new TextureRegionDrawable(new TextureRegion(new Texture("MenuButton5.png")));
        dealershipStyle.over = new TextureRegionDrawable(new TextureRegion(new Texture("MenuButton6.png")));
        dealershipStyle.font = skin.getFont("default-font");

        ImageTextButton.ImageTextButtonStyle driveStyle = new ImageTextButton.ImageTextButtonStyle();
        driveStyle.up   = new TextureRegionDrawable(new TextureRegion(new Texture("MenuButton1.png")));
        driveStyle.down = new TextureRegionDrawable(new TextureRegion(new Texture("MenuButton2.png")));
        driveStyle.over = new TextureRegionDrawable(new TextureRegion(new Texture("MenuButton3.png")));
        driveStyle.font = skin.getFont("default-font");

        ImageTextButton.ImageTextButtonStyle garageStyle = new ImageTextButton.ImageTextButtonStyle();
        garageStyle.up   = new TextureRegionDrawable(new TextureRegion(new Texture("MenuButton7.png")));
        garageStyle.down = new TextureRegionDrawable(new TextureRegion(new Texture("MenuButton8.png")));
        garageStyle.over = new TextureRegionDrawable(new TextureRegion(new Texture("MenuButton9.png")));
        garageStyle.font = skin.getFont("default-font");

        TextButton button1 = new TextButton("DEALERSHIP", dealershipStyle);
        TextButton button2 = new TextButton("DRIVE",      driveStyle);
        TextButton button3 = new TextButton("GARAGE",     garageStyle);

        button1.getLabel().setFontScale(2);
        button2.getLabel().setFontScale(2);
        button3.getLabel().setFontScale(2);

        float buttonWidth  = 420;
        float buttonHeight = 240;

        card.add(button1).width(buttonWidth).height(buttonHeight).padRight(20);
        card.add(button2).width(buttonWidth).height(buttonHeight).padRight(20);
        card.add(button3).width(buttonWidth).height(buttonHeight);

        centerRoot.add(card);

        //  Button listeners
        button3.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dispose();
                game.setScreen(new GarageScreen(game));
            }
        });

        button2.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dispose();
                game.setScreen(new GameModeSelectorScreen(game));

                if (game instanceof Main) {
                    String selectedCar = ((Main) game).selectedCarName;
                    System.out.println("Driving with: " + selectedCar);
                }
            }
        });

        button1.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dispose();
                game.setScreen(new DealershipScreen(game));
            }
        });

        // ===== TOP BAR: CASH + USERNAME  =====
        cashLabel = new Label("$" + formatCash(CashManager.getCash()), skin);
        cashLabel.setFontScale(1.2f);
        cashLabel.setAlignment(Align.center);

        Container<Label> cashContainer = new Container<>(cashLabel);
        cashContainer.setBackground(createCashLabelBackground());
        cashContainer.setColor(Color.BLACK);

        usernameLabel = new Label("Loading...", skin);
        usernameLabel.setFontScale(1.1f);
        usernameLabel.setAlignment(Align.center);

        Container<Label> usernameContainer = new Container<>(usernameLabel);
        usernameContainer.setBackground(createCashLabelBackground());
        usernameContainer.setColor(Color.BLACK);
        usernameContainer.padLeft(10).padRight(10);

        Table topBar = new Table();
        topBar.top().left().pad(10);
        topBar.setFillParent(true);

        topBar.add(cashContainer).left().expandX();
        topBar.add(usernameContainer).right();

        stage.addActor(topBar);

        // Username dropdown
        final Table userMenu = new Table(skin);
        userMenu.setVisible(false);
        userMenu.defaults().pad(5).fillX();
        userMenu.setBackground("default-round");
        userMenu.setColor(0.08f, 0.08f, 0.08f, 0.95f);

        TextButton viewAccountButton = new TextButton("View account", skin);
        userMenu.add(viewAccountButton).row();

        Table userMenuContainer = new Table();
        userMenuContainer.setFillParent(true);
        userMenuContainer.top().right().padTop(60).padRight(10);
        userMenuContainer.add(userMenu).width(180);
        stage.addActor(userMenuContainer);

        usernameContainer.addListener(new ClickListener() {
            private boolean visible = false;

            @Override
            public void clicked(InputEvent event, float x, float y) {
                visible = !visible;
                userMenu.setVisible(visible);
            }
        });

        viewAccountButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                userMenu.setVisible(false);
                game.setScreen(new AccountScreen(game));
            }
        });

        // Fetch username ONCE here
        SupabaseGameData.fetchUsername(
            SupabaseAuth.userId,
            SupabaseAuth.accessToken,
            username -> Gdx.app.postRunnable(() -> {
                if (username == null || username.trim().isEmpty()) {
                    usernameLabel.setText("Unknown");
                } else {
                    usernameLabel.setText(username);
                }
            })
        );


        // Load textures for custom button
        settingsUpTex   = new Texture(Gdx.files.internal("settings_button_up.png"));
        settingsDownTex = new Texture(Gdx.files.internal("settings_button_down.png"));
        settingsOverTex = new Texture(Gdx.files.internal("settings_button_over.png"));

        ImageTextButton.ImageTextButtonStyle settingsStyle = new ImageTextButton.ImageTextButtonStyle();
        settingsStyle.up   = new TextureRegionDrawable(new TextureRegion(settingsUpTex));
        settingsStyle.down = new TextureRegionDrawable(new TextureRegion(settingsDownTex));
        settingsStyle.over = new TextureRegionDrawable(new TextureRegion(settingsOverTex));
        settingsStyle.font = skin.getFont("default-font");

        // Anchor table at bottom-right
        Table settingsAnchor = new Table();
        settingsAnchor.setFillParent(true);
        settingsAnchor.bottom().right().pad(20);
        stage.addActor(settingsAnchor);

        ImageTextButton settingsButton = new ImageTextButton("SETTINGS", settingsStyle);
        //settingsButton.getLabel().setFontScale(0.9f);
        settingsAnchor.add(settingsButton).size(180, 70);

        // Popup card
        Table settingsPopup = new Table(skin);
        settingsPopup.setVisible(false);
        settingsPopup.defaults().pad(4).fillX();
        settingsPopup.setBackground("default-round");
        settingsPopup.setColor(0.08f, 0.08f, 0.08f, 0.95f);
        settingsPopup.pad(10);

        Label settingsTitle = new Label("Settings", skin);
        settingsTitle.setColor(Color.LIGHT_GRAY);
        settingsTitle.setAlignment(Align.center);
        settingsPopup.add(settingsTitle).expandX().left().padBottom(6).row();

        Label divider = new Label("-----------------------------------------", skin);
        divider.setColor(Color.DARK_GRAY);
        settingsPopup.add(divider).expandX().left().padBottom(4).row();

        TextButton logoutButton = new TextButton("Log out", skin);
        TextButton toMainMenu   = new TextButton("Back to Title Screen", skin);
        TextButton quitButton   = new TextButton("Quit Game", skin);
        TextButton cashButton   = new TextButton("Add Cash (debug)", skin);
        TextButton fullscreenButton = new TextButton(getFullscreenText(), skin);

        settingsPopup.add(fullscreenButton).row();
        settingsPopup.add(toMainMenu).row();
        settingsPopup.add(cashButton).row();
        settingsPopup.add(logoutButton).row();
        settingsPopup.add(quitButton).row();


        // Attach popup above the settings button
        Table popupContainer = new Table();
        popupContainer.setFillParent(true);
        popupContainer.bottom().right().padBottom(70).padRight(20);
        popupContainer.add(settingsPopup).width(220);
        stage.addActor(popupContainer);

        fullscreenButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                boolean enableFullscreen = !Gdx.graphics.isFullscreen();

                if (enableFullscreen) {
                    Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
                } else {
                    Gdx.graphics.setWindowedMode(1600, 900);
                }

                GameSettings.setFullscreenEnabled(enableFullscreen);
                fullscreenButton.setText(getFullscreenText());
            }
        });

        // Toggle popup
        settingsButton.addListener(new ClickListener() {
            private boolean visible = false;

            @Override
            public void clicked(InputEvent event, float x, float y) {
                visible = !visible;
                settingsPopup.setVisible(visible);
            }
        });

        // --- Settings popup actions ---

        quitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.exit();
            }
        });

        toMainMenu.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new PlayScreen(game));
            }
        });

        logoutButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
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


        cashButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Dialog confirmDialog = new Dialog("Confirm Cash?", skin) {
                    @Override
                    protected void result(Object object) {
                        if ((Boolean) object) {
                            // Server-side cash grant for testing
                            if (!com.manogstudios.racingsimulator.network.SupabaseAuth.isLoggedIn) {
                                Dialog d = new Dialog("Not logged in", skin);
                                d.text("Login first so cash can be saved to the cloud.");
                                d.button("OK");
                                d.show(stage);
                                return;
                            }

                            com.manogstudios.racingsimulator.network.SupabaseGameData.adjustCash(
                                10000,
                                newCash -> {
                                    // update local cache + UI
                                    CashManager.setCash(newCash);
                                    CashManager.saveCash();
                                    // cashLabel.setText("$" + formatCash(CashManager.getCash()));
                                },
                                err -> {
                                    Dialog d = new Dialog("Cash update failed", skin);
                                    d.text("Error: " + err);
                                    d.button("OK");
                                    d.show(stage);
                                }
                            );
                        }
                    }
                };

                confirmDialog.text("Add $10,000 server-side?");
                confirmDialog.button("Yes", true);
                confirmDialog.button("No", false);
                confirmDialog.show(stage);
            }
        });

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



    private String formatCash(int cash) {
        return String.format("%,d", cash);
    }

    private String getFullscreenText() {
        return "Fullscreen: " + (Gdx.graphics.isFullscreen() ? "ON" : "OFF");
    }

    private Drawable createCashLabelBackground() {
        int width = 200;
        int height = 50;

        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.LIGHT_GRAY);
        pixmap.fill();

        pixmap.setColor(Color.BLACK);
        pixmap.drawRectangle(0, 0, width, height);

        Texture texture = new Texture(pixmap);
        pixmap.dispose(); // Free memory

        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.begin();
        batch.draw(backgroundTexture, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.end();

        stage.act(delta);
        stage.draw();

        cashLabel.setText("$" + formatCash(CashManager.getCash()));
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        backgroundTexture.dispose();
        batch.dispose();


        if (settingsUpTex != null)   settingsUpTex.dispose();
        if (settingsDownTex != null) settingsDownTex.dispose();
        if (settingsOverTex != null) settingsOverTex.dispose();
    }
}
