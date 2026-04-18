package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;

public class LoginScreen implements Screen {

    private final Game game;
    private Stage stage;
    private Skin skin;

    private Texture bgTexture;   //  background
    private Image bgImage;
    private int failedAttempts = 0;
    private long lockoutEndTimeMs = 0L;
    private static final int MAX_LOGIN_ATTEMPTS = 5;         // max attempts before lock
    private static final long LOCKOUT_DURATION_MS = 30_000L; // 30 seconds

    public LoginScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        Gdx.input.setInputProcessor(stage);

        // Background image
        try {
            bgTexture = new Texture(Gdx.files.internal("login_bg.png"));
            bgImage = new Image(bgTexture);
            bgImage.setFillParent(true);
            stage.addActor(bgImage);
        } catch (Exception e) {
            // If the texture is missing, just skip the background
            bgTexture = null;
        }

        // Root table
        Table root = new Table();
        root.setFillParent(true);
        root.center();
        stage.addActor(root);

        // Card / panel
        Table card = new Table(skin);
        card.pad(30);
        card.defaults().pad(8).fillX();

        // Using the default rounded background from uiskin, and darken it a bit
        card.setBackground("default-round");
        card.setColor(0.08f, 0.08f, 0.08f, 0.90f);

        // Title + subtitle
        Label title = new Label("Traffic Racing Simulator", skin);
        title.setAlignment(Align.center);
        title.setFontScale(1.3f);

        Label subtitle = new Label("Sign in to your account", skin);
        subtitle.setAlignment(Align.center);
        subtitle.setColor(Color.LIGHT_GRAY);

        card.add(title).colspan(2).padBottom(4).row();
        card.add(subtitle).colspan(2).padBottom(18).row();

        // Email field
        Label emailLabel = new Label("Email", skin);
        TextField emailField = new TextField("", skin);
        emailField.setMessageText("you@example.com");

        card.add(emailLabel).colspan(2).left();
        card.row();
        card.add(emailField).colspan(2).width(380).row();

        // Password + Show toggle
        Label passwordLabel = new Label("Password", skin);
        TextField passwordField = new TextField("", skin);
        passwordField.setPasswordMode(true);
        passwordField.setPasswordCharacter('*');
        passwordField.setMessageText("••••••••");

        // little "Show" button
        TextButton showButton = new TextButton("Show", skin);

        // password field + show button
        Table passwordRow = new Table();
        passwordRow.add(passwordField).width(300).padRight(6);
        passwordRow.add(showButton).width(70);

        card.add(passwordLabel).colspan(2).left().padTop(10);
        card.row();
        card.add(passwordRow).colspan(2).row();

        // Show/hide
        showButton.addListener(new ClickListener() {
            private boolean showing = false;

            @Override
            public void clicked(InputEvent event, float x, float y) {
                showing = !showing;
                passwordField.setPasswordMode(!showing);
                showButton.setText(showing ? "Hide" : "Show");
            }
        });

        // Feedback label
        Label feedbackLabel = new Label("", skin);
        feedbackLabel.setAlignment(Align.center);
        feedbackLabel.setColor(Color.RED);
        feedbackLabel.setWrap(true);

        card.add(feedbackLabel).colspan(2).width(380).padTop(8).row();

        //  Forgot password link
        TextButton forgotPasswordButton = new TextButton("Forgot password?", skin);
        forgotPasswordButton.getLabel().setColor(Color.SKY);
        forgotPasswordButton.getLabel().setAlignment(Align.center);
        forgotPasswordButton.getLabel().setFontScale(0.9f);

        card.add(forgotPasswordButton).colspan(2).width(200).height(30).padTop(4).row();

        // Login button
        TextButton loginButton = new TextButton("Login", skin);
        loginButton.getLabel().setAlignment(Align.center);
        card.add(loginButton).colspan(2).width(380).height(45).padTop(10).row();

        //  Divider text
        Label orLabel = new Label("Don't have an account?", skin);
        orLabel.setAlignment(Align.center);
        orLabel.setColor(Color.LIGHT_GRAY);
        card.add(orLabel).colspan(2).padTop(10).row();

        // Register button
        TextButton toRegisterButton = new TextButton("Create account", skin);
        toRegisterButton.getLabel().setColor(Color.SKY);
        card.add(toRegisterButton).colspan(2).width(200).height(40).padTop(4).row();

        // Add the card to the root
        root.add(card).width(480).pad(20);



        loginButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                long now = System.currentTimeMillis();

                // 1) Check if its currently locked out
                if (now < lockoutEndTimeMs) {
                    long remainingMs = lockoutEndTimeMs - now;
                    long remainingSec = (remainingMs + 999) / 1000; // round up to seconds

                    feedbackLabel.setColor(Color.RED);
                    feedbackLabel.setText(
                        "Too many login attempts.\n" +
                            "Please wait " + remainingSec + "s before trying again."
                    );
                    return;
                }

                // 2) Normal login flow
                final String email = emailField.getText().trim();
                final String password = passwordField.getText();

                if (email.isEmpty() || password.isEmpty()) {
                    feedbackLabel.setColor(Color.RED);
                    feedbackLabel.setText("Please enter both email and password.");
                    return;
                }

                feedbackLabel.setColor(Color.LIGHT_GRAY);
                feedbackLabel.setText("Logging in...");

                SupabaseAuth.login(email, password, success -> {
                    Gdx.app.postRunnable(() -> {
                        if (success) {
                            failedAttempts = 0;
                            lockoutEndTimeMs = 0L;

                            feedbackLabel.setColor(Color.LIGHT_GRAY);
                            feedbackLabel.setText("Password accepted. Checking 2FA...");

                            SupabaseAuth.fetchMfaStatus(aalResult -> {
                                System.out.println("=== MFA STATUS DEBUG ===");
                                System.out.println("success = " + aalResult.success);
                                System.out.println("error = " + aalResult.error);
                                System.out.println("currentLevel = " + aalResult.currentLevel);
                                System.out.println("nextLevel = " + aalResult.nextLevel);
                                System.out.println("hasVerifiedFactor = " + aalResult.hasVerifiedFactor);
                                System.out.println("factorCount = " + aalResult.factorCount);
                                System.out.println("verifiedFactorIds = " + aalResult.verifiedFactorIds);
                                System.out.println("========================");

                                if (!aalResult.success) {
                                    feedbackLabel.setColor(Color.RED);
                                    feedbackLabel.setText(
                                        "Login worked, but 2FA status could not be checked.\n" +
                                            "Error: " + (aalResult.error == null ? "unknown" : aalResult.error)
                                    );
                                    return;
                                }

                                boolean needsMfa =
                                    "aal1".equalsIgnoreCase(aalResult.currentLevel) &&
                                        "aal2".equalsIgnoreCase(aalResult.nextLevel);

                                if (needsMfa) {
                                    game.setScreen(new MfaCodeScreen(game));
                                } else {
                                    feedbackLabel.setColor(Color.GREEN);
                                    feedbackLabel.setText("Login successful!");
                                    continueAfterSuccessfulAuth();
                                }
                            });

                        } else {
                            // Failure: increment attempts and maybe lock
                            failedAttempts++;

                            String code = SupabaseAuth.lastErrorCode;
                            String msg  = SupabaseAuth.lastErrorMessage != null
                                ? SupabaseAuth.lastErrorMessage.toLowerCase()
                                : "";

                            // If too many failures, start lockout
                            if (failedAttempts >= MAX_LOGIN_ATTEMPTS) {
                                lockoutEndTimeMs = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
                                failedAttempts = 0; // reset counter for the next window

                                feedbackLabel.setColor(Color.RED);
                                feedbackLabel.setText(
                                    "Too many login attempts.\n" +
                                        "Please wait 30 seconds before trying again."
                                );
                                return;
                            }

                            feedbackLabel.setColor(Color.RED);

                            if ("invalid_grant".equals(code)
                                && msg.contains("email")
                                && msg.contains("confirm")) {

                                feedbackLabel.setText(
                                    "Your email is not confirmed yet.\n" +
                                        "Please click the verification link in your inbox, then try again."
                                );
                            } else {
                                feedbackLabel.setText(
                                    "Login failed. Check your email & password.\n" +
                                        "Attempts left: " + (MAX_LOGIN_ATTEMPTS - failedAttempts)
                                );
                            }
                        }
                    });
                });
            }
        });



        forgotPasswordButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String email = emailField.getText().trim();

                if (email.isEmpty()) {
                    feedbackLabel.setColor(Color.RED);
                    feedbackLabel.setText("Enter your email first, then tap 'Forgot password?'.");
                    return;
                }

                feedbackLabel.setColor(Color.LIGHT_GRAY);
                feedbackLabel.setText("Sending reset email...");

                SupabaseAuth.resetPassword(email, success -> {
                    Gdx.app.postRunnable(() -> {
                        if (success) {
                            feedbackLabel.setColor(Color.GREEN);
                            feedbackLabel.setText(
                                "If an account exists for that email,\n" +
                                    "a reset link has been sent."
                            );
                        } else {
                            feedbackLabel.setColor(Color.RED);
                            feedbackLabel.setText(
                                "Could not send reset email.\n" +
                                    "Try again in a moment."
                            );
                        }
                    });
                });
            }
        });


        toRegisterButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new RegisterScreen(game));
            }
        });
    }

    private void continueAfterSuccessfulAuth() {
        String userId = SupabaseAuth.userId;
        String token = SupabaseAuth.accessToken;

        AchievementsManager.setCurrentUser(userId);
        AchievementsManager.syncFromCloud(null);

        SupabaseGameData.loadProfile(userId, token, () -> {
            SupabaseGameData.fetchUsername(userId, token, username -> {
                if (username == null || username.trim().isEmpty()) {
                    game.setScreen(new UsernameScreen(game));
                } else {
                    game.setScreen(new PlayScreen(game));
                }
            });
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
