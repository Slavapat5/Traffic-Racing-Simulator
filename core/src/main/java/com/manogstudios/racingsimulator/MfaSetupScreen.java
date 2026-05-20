package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

public class MfaSetupScreen implements Screen {

    private final Game game;
    private Stage stage;
    private Skin skin;

    private Texture settingsButtonTexture;
    private Texture settingsButtonDownTexture;
    private Texture settingsButtonOverTexture;

    private Label statusLabel;
    private Label currentFactorLabel;
    private TextArea setupInfoArea;
    private TextField codeField;

    private String pendingFactorId;
    private String pendingChallengeId;
    private String verifiedFactorId;

    public MfaSetupScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        Gdx.input.setInputProcessor(stage);

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

        Label titleLabel = new Label("Two-Factor Authentication", skin);
        titleLabel.setFontScale(1.7f);
        titleLabel.setAlignment(Align.center);

        statusLabel = new Label("Checking MFA status...", skin);
        statusLabel.setWrap(true);
        statusLabel.setAlignment(Align.center);

        currentFactorLabel = new Label("No factor loaded", skin);
        currentFactorLabel.setWrap(true);

        setupInfoArea = new TextArea("", skin);
        setupInfoArea.setDisabled(true);
        setupInfoArea.setPrefRows(6);

        codeField = new TextField("", skin);
        codeField.setMessageText("Enter 6-digit authenticator code");

        TextButton refreshButton = new TextButton("Refresh Status", settingsButtonStyle);
        TextButton enableButton = new TextButton("Enable 2FA", settingsButtonStyle);
        TextButton verifyButton = new TextButton("Verify Code", settingsButtonStyle);
        TextButton disableButton = new TextButton("Disable 2FA", settingsButtonStyle);
        TextButton backButton = new TextButton("Back", settingsButtonStyle);

        refreshButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                loadFactorState();
            }
        });

        enableButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                beginTotpEnrollment();
            }
        });

        verifyButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                verifyEnrollmentCode();
            }
        });

        disableButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                disableCurrentFactor();
            }
        });

        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new SettingsScreen(game));
            }
        });

        Table table = new Table();
        table.setFillParent(true);
        table.pad(30);

        table.add(titleLabel).width(700).padBottom(20);
        table.row();

        table.add(statusLabel).width(700).padBottom(15);
        table.row();

        table.add(currentFactorLabel).width(700).padBottom(15).left();
        table.row();

        table.add(setupInfoArea).width(700).height(150).padBottom(15);
        table.row();

        table.add(codeField).width(420).height(50).padBottom(15);
        table.row();

        table.add(refreshButton).width(320).height(50).padBottom(10);
        table.row();

        table.add(enableButton).width(320).height(50).padBottom(10);
        table.row();

        table.add(verifyButton).width(320).height(50).padBottom(10);
        table.row();

        table.add(disableButton).width(320).height(50).padBottom(20);
        table.row();

        table.add(backButton).width(320).height(50);

        stage.addActor(table);

        loadFactorState();

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACKSPACE) {
                    game.setScreen(new SettingsScreen(game));
                    return true;
                }
                return false;
            }
        });
    }

    // Loads the current MFA status so the screen can show whether 2FA is enabled.
    private void loadFactorState() {
        statusLabel.setColor(Color.LIGHT_GRAY);
        statusLabel.setText("Checking MFA status...");

        // was the request succesful?
        SupabaseAuth.fetchMfaStatus(result -> {
            if (!result.success) {
                statusLabel.setColor(Color.RED);
                statusLabel.setText("Could not load MFA status: " + safe(result.error));
                currentFactorLabel.setText("No factor data available.");
                return;
            }

            // clear old data
            verifiedFactorId = null;
            pendingFactorId = null;
            pendingChallengeId = null;
            setupInfoArea.setText("");
            codeField.setText("");

            // if supabase says theyre is a verified factor, game stores factor id
            if (!result.verifiedFactorIds.isEmpty()) {
                verifiedFactorId = result.verifiedFactorIds.get(0);
            }

            if (result.hasVerifiedFactor) {
                statusLabel.setColor(Color.GREEN);
                statusLabel.setText("2FA is enabled.");
                currentFactorLabel.setText("A verified authenticator factor is enrolled.");
            } else if (result.factorCount > 0) {
                statusLabel.setColor(Color.ORANGE);
                statusLabel.setText("2FA setup exists but is not verified yet.");
                currentFactorLabel.setText("No verified factor is active yet.");
            } else {
                statusLabel.setColor(Color.YELLOW);
                statusLabel.setText("2FA is not enabled.");
                currentFactorLabel.setText("No MFA factors enrolled.");
            }
        });
    }

    // Starts TOTP enrollment and displays the secret/URI for the authenticator app
    private void beginTotpEnrollment() {
        statusLabel.setColor(Color.LIGHT_GRAY);
        statusLabel.setText("Starting TOTP enrollment...");

        SupabaseAuth.enrollTotpFactor("Traffic Racing Simulator", result -> {
            if (!result.success) {
                statusLabel.setColor(Color.RED);
                statusLabel.setText("Failed to start 2FA setup: " + safe(result.error));
                return;
            }

            // storing factor id
            pendingFactorId = result.factorId;
            pendingChallengeId = null;

            StringBuilder info = new StringBuilder();
            info.append("Add this factor to your authenticator app.\n\n");

            // displaying secret to user
            if (result.secret != null) {
                info.append("Secret:\n").append(result.secret).append("\n\n");
            }
            // displaying uri
            if (result.uri != null) {
                info.append("URI:\n").append(result.uri).append("\n\n");
            }

            info.append("Then enter the 6-digit code below and press Verify Code.");

            setupInfoArea.setText(info.toString());
            statusLabel.setColor(Color.YELLOW);
            statusLabel.setText("2FA factor created. Now verify it.");
        });
    }

    // Creates a challenge and verifies the users 6 digit authenticator code
    private void verifyEnrollmentCode() {
        String code = codeField.getText().trim();

        if (pendingFactorId == null || pendingFactorId.isEmpty()) {
            statusLabel.setColor(Color.RED);
            statusLabel.setText("No pending factor. Press Enable 2FA first.");
            return;
        }

        if (code.isEmpty()) {
            statusLabel.setColor(Color.RED);
            statusLabel.setText("Enter the 6-digit code from your authenticator app.");
            return;
        }

        statusLabel.setColor(Color.LIGHT_GRAY);
        statusLabel.setText("Creating challenge...");

        SupabaseAuth.createMfaChallenge(pendingFactorId, challengeResult -> {
            if (!challengeResult.success) {
                statusLabel.setColor(Color.RED);
                statusLabel.setText("Failed to create challenge: " + safe(challengeResult.error));
                return;
            }

            pendingChallengeId = challengeResult.challengeId;

            statusLabel.setText("Verifying code...");

            SupabaseAuth.verifyMfaChallenge(pendingFactorId, pendingChallengeId, code, verifyResult -> {
                if (!verifyResult.success) {
                    statusLabel.setColor(Color.RED);
                    statusLabel.setText("Verification failed: " + safe(verifyResult.error));
                    return;
                }

                statusLabel.setColor(Color.GREEN);
                statusLabel.setText("2FA enabled successfully.");
                loadFactorState();
            });
        });
    }

    // Removes the currently verified MFA factor from the account
    private void disableCurrentFactor() {
        if (verifiedFactorId == null || verifiedFactorId.isEmpty()) {
            statusLabel.setColor(Color.RED);
            statusLabel.setText("No verified factor to disable.");
            return;
        }

        statusLabel.setColor(Color.LIGHT_GRAY);
        statusLabel.setText("Disabling 2FA...");

        SupabaseAuth.unenrollMfaFactor(verifiedFactorId, result -> {
            if (!result.success) {
                statusLabel.setColor(Color.RED);
                statusLabel.setText("Failed to disable 2FA: " + safe(result.error));
                return;
            }

            statusLabel.setColor(Color.GREEN);
            statusLabel.setText("2FA disabled.");
            loadFactorState();
        });
    }

    private String safe(String s) {
        return (s == null || s.isEmpty()) ? "unknown_error" : s;
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
