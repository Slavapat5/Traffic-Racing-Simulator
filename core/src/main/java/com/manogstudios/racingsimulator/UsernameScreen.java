package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;

public class UsernameScreen implements Screen {

    private final Game game;
    private Stage stage;
    private Skin skin;

    private Texture bgTexture;
    private Image bgImage;

    public UsernameScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        // Require login
        if (!SupabaseAuth.isLoggedIn) {
            game.setScreen(new LoginScreen(game));
            return;
        }

        stage = new Stage(new ScreenViewport());
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        Gdx.input.setInputProcessor(stage);

        try {
            bgTexture = new Texture(Gdx.files.internal("login_bg.png"));
            bgImage = new Image(bgTexture);
            bgImage.setFillParent(true);
            stage.addActor(bgImage);
        } catch (Exception e) {
            bgTexture = null;
        }

        // --- Root container to center the card ---
        Table root = new Table();
        root.setFillParent(true);
        root.center();
        stage.addActor(root);

        // --- Card / panel ---
        Table card = new Table(skin);
        card.pad(30);
        card.defaults().pad(8).fillX();

        card.setBackground("default-round");
        card.setColor(0.08f, 0.08f, 0.08f, 0.92f);

        // --- Title + subtitle ---
        Label title = new Label("Choose Your Racer Name", skin);
        title.setAlignment(Align.center);
        title.setFontScale(1.3f);

        Label subtitle = new Label(
            "Pick a unique username.\n" +
                "This will appear on leaderboards and in online stats.",
            skin
        );
        subtitle.setAlignment(Align.center);
        subtitle.setColor(Color.LIGHT_GRAY);
        subtitle.setWrap(true);

        card.add(title).colspan(2).padBottom(4).row();
        card.add(subtitle).colspan(2).width(420).padBottom(16).row();

        // --- Username field ---
        Label usernameLabel = new Label("Username", skin);
        TextField usernameField = new TextField("", skin);
        usernameField.setMessageText("Racer123");

        card.add(usernameLabel).colspan(2).left();
        card.row();
        card.add(usernameField).colspan(2).width(360).row();

        // --- Status label ---
        Label statusLabel = new Label("", skin);
        statusLabel.setAlignment(Align.center);
        statusLabel.setWrap(true);
        statusLabel.setColor(Color.RED);

        card.add(statusLabel).colspan(2).width(380).padTop(8).row();

        // --- Confirm button ---
        TextButton confirmButton = new TextButton("Confirm", skin);
        confirmButton.getLabel().setAlignment(Align.center);
        card.add(confirmButton).colspan(2).width(360).height(45).padTop(10).row();

        //  Back to login/menu if user changes their mind
        TextButton backButton = new TextButton("Back to Menu", skin);
        card.add(backButton).colspan(2).width(200).height(40).padTop(8).row();

        // Add card to root
        root.add(card).width(480).pad(20);

        // --- Button logic ---
        confirmButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String entered = usernameField.getText().trim();

                // Basic validation
                if (entered.isEmpty()) {
                    statusLabel.setColor(Color.RED);
                    statusLabel.setText("Please enter a username.");
                    return;
                }
                if (entered.length() < 3 || entered.length() > 16) {
                    statusLabel.setColor(Color.RED);
                    statusLabel.setText("Username must be 3–16 characters long.");
                    return;
                }
                // Allow only letters, numbers, underscore for now
                if (!entered.matches("^[A-Za-z0-9_]+$")) {
                    statusLabel.setColor(Color.RED);
                    statusLabel.setText("Only letters, numbers, and _ are allowed.");
                    return;
                }

                statusLabel.setColor(Color.LIGHT_GRAY);
                statusLabel.setText("Saving username...");
                confirmButton.setDisabled(true);

                SupabaseGameData.updateUsername(
                    SupabaseAuth.userId,
                    SupabaseAuth.accessToken,
                    entered,
                    success -> {
                        Gdx.app.postRunnable(() -> {
                            if (success) {
                                statusLabel.setColor(Color.GREEN);
                                statusLabel.setText("Username set! Loading game...");
                                game.setScreen(new PlayScreen(game));
                            } else {
                                statusLabel.setColor(Color.RED);
                                statusLabel.setText("That username may be taken or invalid.\nTry another.");
                                confirmButton.setDisabled(false);
                            }
                        });
                    }
                );
            }
        });

        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new PlayScreen(game));
            }
        });
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override public void resize(int width, int height) { stage.getViewport().update(width, height, true); }
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
        if (bgTexture != null) bgTexture.dispose();
    }
}
