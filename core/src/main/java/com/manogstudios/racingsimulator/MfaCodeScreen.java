package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;

public class MfaCodeScreen implements Screen {

    private final Game game;
    private Stage stage;
    private Skin skin;

    private Label statusLabel;
    private TextField codeField;

    private String verifiedFactorId;
    private boolean mfaCompleted = false;

    public MfaCodeScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        Gdx.input.setInputProcessor(stage);

        Label titleLabel = new Label("Two-Factor Authentication", skin);
        titleLabel.setFontScale(1.5f);
        titleLabel.setAlignment(Align.center);

        Label subtitleLabel = new Label("Enter the 6-digit code from your authenticator app", skin);
        subtitleLabel.setAlignment(Align.center);
        subtitleLabel.setWrap(true);

        statusLabel = new Label("Loading 2FA factor...", skin);
        statusLabel.setAlignment(Align.center);
        statusLabel.setWrap(true);
        statusLabel.setColor(Color.LIGHT_GRAY);

        codeField = new TextField("", skin);
        codeField.setMessageText("123456");

        TextButton verifyButton = new TextButton("Verify", skin);
        TextButton backButton = new TextButton("Back to Login", skin);

        verifyButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                verifyCode();
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                SupabaseAuth.logout();
                game.setScreen(new LoginScreen(game));
            }
        });

        Table table = new Table();
        table.setFillParent(true);
        table.center();
        table.pad(30);

        table.add(titleLabel).width(600).padBottom(15);
        table.row();

        table.add(subtitleLabel).width(600).padBottom(15);
        table.row();

        table.add(statusLabel).width(600).padBottom(20);
        table.row();

        table.add(codeField).width(320).height(50).padBottom(15);
        table.row();

        table.add(verifyButton).width(260).height(50).padBottom(10);
        table.row();

        table.add(backButton).width(260).height(50);

        stage.addActor(table);

        loadVerifiedFactor();
    }

    // Loads the verified MFA factor that will be challenged during login
    private void loadVerifiedFactor() {
        statusLabel.setColor(Color.LIGHT_GRAY);
        statusLabel.setText("Checking enrolled 2FA factor...");

        SupabaseAuth.fetchMfaStatus(result -> {
            if (!result.success) {
                statusLabel.setColor(Color.RED);
                statusLabel.setText("Could not load MFA factor: " + safe(result.error));
                return;
            }

            verifiedFactorId = null;

            if (!result.verifiedFactorIds.isEmpty()) {
                verifiedFactorId = result.verifiedFactorIds.get(0);
            }

            if (verifiedFactorId == null || verifiedFactorId.isEmpty()) {
                statusLabel.setColor(Color.RED);
                statusLabel.setText("No verified MFA factor was found for this account.");
            } else {
                statusLabel.setColor(Color.GREEN);
                statusLabel.setText("Enter your authenticator code to finish signing in.");
            }
        });
    }

    // If the user leaves before MFA is completed, clear the partial login session
    private void verifyCode() {
        String code = codeField.getText().trim();

        if (verifiedFactorId == null || verifiedFactorId.isEmpty()) {
            statusLabel.setColor(Color.RED);
            statusLabel.setText("No verified factor loaded.");
            return;
        }

        if (code.isEmpty()) {
            statusLabel.setColor(Color.RED);
            statusLabel.setText("Please enter the 6-digit code.");
            return;
        }

        // MfaCodeScreen
        if (!code.matches("\\d{6}")) {
            statusLabel.setColor(Color.RED);
            statusLabel.setText("Please enter a valid 6-digit code.");
            return;
        }

        statusLabel.setColor(Color.LIGHT_GRAY);
        statusLabel.setText("Creating challenge...");

        SupabaseAuth.createMfaChallenge(verifiedFactorId, challengeResult -> {
            Gdx.app.postRunnable(() -> {
                if (!challengeResult.success) {
                    statusLabel.setColor(Color.RED);
                    statusLabel.setText("Could not create MFA challenge: " + safe(challengeResult.error));
                    return;
                }

                statusLabel.setText("Verifying code...");

                SupabaseAuth.verifyMfaChallenge(
                    verifiedFactorId,
                    challengeResult.challengeId,
                    code,
                    verifyResult -> Gdx.app.postRunnable(() -> {
                        if (!verifyResult.success) {
                            statusLabel.setColor(Color.RED);
                            statusLabel.setText("Invalid code or verification failed: " + safe(verifyResult.error));
                            return;
                        }

                        statusLabel.setColor(Color.LIGHT_GRAY);
                        statusLabel.setText("2FA verified. Checking session...");

                        String currentAal = SupabaseAuth.getCurrentAal();

                        System.out.println("Post-MFA current AAL = " + currentAal);

                        if ("aal2".equalsIgnoreCase(currentAal)) {
                            mfaCompleted = true;

                            statusLabel.setColor(Color.GREEN);
                            statusLabel.setText("2FA successful. Loading profile...");
                            continueAfterSuccessfulAuth();
                        } else {
                            statusLabel.setColor(Color.RED);
                            statusLabel.setText("2FA verification did not upgrade the session.");
                        }
                    })
                );
            });
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

    private String safe(String s) {
        return (s == null || s.isEmpty()) ? "unknown_error" : s;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
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
        if (!mfaCompleted) {
            SupabaseAuth.logout();
        }

        dispose();
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();
    }
}
