package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.manogstudios.racingsimulator.network.SupabaseAuth;

public class ChangePasswordScreen implements Screen {

    private final Game game;
    private Stage stage;
    private Skin skin;

    public ChangePasswordScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        if (!SupabaseAuth.isLoggedIn) {
            game.setScreen(new LoginScreen(game));
            return;
        }

        stage = new Stage(new ScreenViewport());
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        Gdx.input.setInputProcessor(stage);

        Table root = new Table();
        root.setFillParent(true);
        root.center().pad(30);
        stage.addActor(root);

        Table card = new Table(skin);
        card.pad(25);
        card.defaults().pad(8).fillX();
        card.setBackground("default-round");
        card.setColor(0.08f, 0.08f, 0.08f, 0.9f);

        Label title = new Label("Change Password", skin);
        title.setAlignment(Align.center);
        title.setFontScale(1.3f);
        card.add(title).colspan(2).padBottom(16).row();

        // new password
        Label newPwLabel = new Label("New Password", skin);
        TextField newPwField = new TextField("", skin);
        newPwField.setPasswordMode(true);
        newPwField.setPasswordCharacter('*');

        card.add(newPwLabel).left().colspan(2);
        card.row();
        card.add(newPwField).width(360).colspan(2).row();

        // confirm password
        Label confirmLabel = new Label("Confirm Password", skin);
        TextField confirmField = new TextField("", skin);
        confirmField.setPasswordMode(true);
        confirmField.setPasswordCharacter('*');

        card.add(confirmLabel).left().colspan(2).padTop(6);
        card.row();
        card.add(confirmField).width(360).colspan(2).row();

        // Status
        Label statusLabel = new Label("", skin);
        statusLabel.setAlignment(Align.center);
        statusLabel.setColor(Color.RED);
        statusLabel.setWrap(true);
        card.add(statusLabel).colspan(2).width(360).padTop(8).row();

        // buttons
        TextButton saveButton = new TextButton("Save Password", skin);
        TextButton backButton = new TextButton("Back", skin);

        Table buttonRow = new Table();
        buttonRow.add(backButton).width(140).padRight(10);
        buttonRow.add(saveButton).width(180);

        card.add(buttonRow).colspan(2).padTop(12).row();

        root.add(card).width(480);

        // Listeners
        saveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String pw1 = newPwField.getText();
                String pw2 = confirmField.getText();

                if (pw1.isEmpty() || pw2.isEmpty()) {
                    statusLabel.setColor(Color.RED);
                    statusLabel.setText("Please fill both password fields.");
                    return;
                }

                if (!pw1.equals(pw2)) {
                    statusLabel.setColor(Color.RED);
                    statusLabel.setText("Passwords do not match.");
                    return;
                }

                //  Strength validation
                if (!isStrongPassword(pw1)) {
                    statusLabel.setColor(Color.RED);
                    statusLabel.setText(
                        "Password must be at least 8 characters\n" +
                            "and include upper, lower, and a number."
                    );
                    return;
                }

                statusLabel.setColor(Color.LIGHT_GRAY);
                statusLabel.setText("Updating password...");

                SupabaseAuth.updatePassword(SupabaseAuth.accessToken, pw1, success -> {
                    if (success) {
                        statusLabel.setColor(Color.GREEN);
                        statusLabel.setText("Password updated successfully.");
                    } else {
                        statusLabel.setColor(Color.RED);
                        statusLabel.setText("Failed to update password. Please try again.");
                    }
                });
            }
        });


        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new AccountScreen(game));
            }
        });
    }

    @Override public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override public void resize(int width, int height) { stage.getViewport().update(width, height, true); }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() { dispose(); }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();
    }

    // Same password strength rule as registration
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
