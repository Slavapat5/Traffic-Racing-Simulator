package com.manogstudios.racingsimulator;


import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;


public class RegisterScreen implements Screen {
    private final Game game;
    private Stage stage;
    private Skin skin;

    private Texture bgTexture;
    private Image bgImage;

    public RegisterScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = new Skin(Gdx.files.internal("uiskin.json"));

        //  background image
        try {
            bgTexture = new Texture(Gdx.files.internal("login_bg.png"));
            bgImage = new Image(bgTexture);
            bgImage.setFillParent(true);
            stage.addActor(bgImage);
        } catch (Exception e) {
            bgTexture = null; // fall back to plain background
        }

        // Root container to center the card
        Table root = new Table();
        root.setFillParent(true);
        root.center();
        stage.addActor(root);

        // --- Card / panel ---
        Table card = new Table(skin);
        card.pad(30);
        card.defaults().pad(8).fillX();

        card.setBackground("default-round");
        card.setColor(0.08f, 0.08f, 0.08f, 0.90f);

        //  Title + subtitle
        Label title = new Label("Traffic Racing Simulator", skin);
        title.setAlignment(Align.center);
        title.setFontScale(1.3f);

        Label subtitle = new Label("Create your account", skin);
        subtitle.setAlignment(Align.center);
        subtitle.setColor(Color.LIGHT_GRAY);

        card.add(title).colspan(2).padBottom(4).row();
        card.add(subtitle).colspan(2).padBottom(18).row();

        //  Email
        Label emailLabel = new Label("Email", skin);
        TextField emailField = new TextField("", skin);
        emailField.setMessageText("you@example.com");

        card.add(emailLabel).colspan(2).left().padTop(6);
        card.row();
        card.add(emailField).colspan(2).width(380).row();

        //  Password + Show toggle
        Label passwordLabel = new Label("Password", skin);
        TextField passwordField = new TextField("", skin);
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('*');
        passwordField.setMessageText("••••••••");

        TextButton showButton = new TextButton("Show", skin);
        Table passwordRow = new Table();
        passwordRow.add(passwordField).width(300).padRight(6);
        passwordRow.add(showButton).width(70);

        card.add(passwordLabel).colspan(2).left().padTop(6);
        card.row();
        card.add(passwordRow).colspan(2).row();

        showButton.addListener(new ClickListener() {
            private boolean showing = false;

            @Override
            public void clicked(InputEvent event, float x, float y) {
                showing = !showing;
                passwordField.setPasswordMode(!showing);
                showButton.setText(showing ? "Hide" : "Show");
            }
        });

        //  Status  feedback label
        Label statusLabel = new Label("", skin);
        statusLabel.setAlignment(Align.center);
        statusLabel.setColor(Color.RED);
        statusLabel.setWrap(true);

        card.add(statusLabel).colspan(2).width(380).padTop(8).row();

        // Sign Up button
        TextButton registerButton = new TextButton("Sign Up", skin);
        registerButton.getLabel().setAlignment(Align.center);
        card.add(registerButton).colspan(2).width(380).height(45).padTop(10).row();

        // "Already have an account?" + Log in
        Label haveAccountLabel = new Label("Already have an account?", skin);
        haveAccountLabel.setColor(Color.LIGHT_GRAY);

        TextButton loginButton = new TextButton("Log in", skin);
        loginButton.getLabel().setColor(Color.SKY);

        Table bottomRow = new Table();
        bottomRow.add(haveAccountLabel).padRight(6);
        bottomRow.add(loginButton);

        card.add(bottomRow).colspan(2).padTop(10).row();

        // Back button
        TextButton backButton = new TextButton("Back to Menu", skin);
        card.add(backButton).colspan(2).width(200).height(40).padTop(10).row();

        // Add card into root
        root.add(card).width(480).pad(20);

        //  Button logic
        registerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String email = emailField.getText().trim();
                String password = passwordField.getText();

                if (email.isEmpty() || password.isEmpty()) {
                    statusLabel.setColor(Color.RED);
                    statusLabel.setText("Email and password are required.");
                    return;
                }

                // Password strength validation
                if (!isStrongPassword(password)) {
                    statusLabel.setColor(Color.RED);
                    statusLabel.setText(
                        "Password must be at least 8 characters\n" +
                            "and include upper, lower, and a number."
                    );
                    return;
                }

                statusLabel.setColor(Color.LIGHT_GRAY);
                statusLabel.setText("Creating account...");

                new Thread(() -> {
                    boolean success = SupabaseAuth.registerUser(email, password);
                    Gdx.app.postRunnable(() -> {
                        if (success) {
                            statusLabel.setColor(Color.GREEN);
                            statusLabel.setText("Registration successful! You can now log in.");
                        } else {
                            statusLabel.setColor(Color.RED);
                            statusLabel.setText("Failed to register. Try again.");
                        }
                    });
                }).start();
            }
        });


        loginButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new LoginScreen(game));
            }
        });

        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
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

    @Override public void resize(int width, int height) {
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
        if (bgTexture != null) bgTexture.dispose();
    }

    /** Simple password strength check:
     *  - at least 8 characters
     *  - at least one upper, one lower, one digit
     */
    private boolean isStrongPassword(String password) {
        if (password == null || password.length() < 8) return false;

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;

            if (hasUpper && hasLower && hasDigit) {
                return true;
            }
        }
        return false;
    }

}
