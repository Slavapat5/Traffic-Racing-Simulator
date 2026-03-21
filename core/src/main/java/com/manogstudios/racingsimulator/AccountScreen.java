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
import com.manogstudios.racingsimulator.CashManager;
import com.manogstudios.racingsimulator.CarOwnershipManager;
import com.manogstudios.racingsimulator.HighScoreManager;
import com.manogstudios.racingsimulator.MenuScreen;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;

public class AccountScreen implements Screen {

    private final Game game;
    private Stage stage;
    private Skin skin;

    private TextField usernameField;
    private Label statusLabel;
    private Label cashLabel;

    // Extra stats
    private Label ownedCarsLabel;
    private Label bestFreeRideLabel;
    private Label bestTimeTrialLabel;
    private Label bestEndlessOneWayLabel;
    private Label bestEndlessTwoWayLabel;
    private Label bestDragSprintLabel;

    public AccountScreen(Game game) {
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
        root.pad(30);
        stage.addActor(root);

        // --- TITLE ---
        Label title = new Label("Account", skin);
        title.setFontScale(2f);
        title.setAlignment(Align.center);

        // --- USERNAME EDIT ---
        Label usernameLabel = new Label("Username", skin);
        usernameField = new TextField("", skin);
        usernameField.setMessageText("Enter username");

        // status / error label
        statusLabel = new Label("", skin);
        statusLabel.setColor(Color.YELLOW);

        TextButton saveUsernameButton = new TextButton("Save Username", skin);

        saveUsernameButton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                String entered = usernameField.getText().trim();

                if (entered.isEmpty()) {
                    statusLabel.setText("Username cannot be empty.");
                    statusLabel.setColor(Color.RED);
                    return;
                }

                if (entered.length() < 3 || entered.length() > 20) {
                    statusLabel.setText("Username must be 3–20 characters.");
                    statusLabel.setColor(Color.RED);
                    return;
                }

                statusLabel.setText("Saving...");
                statusLabel.setColor(Color.YELLOW);
                saveUsernameButton.setDisabled(true);

                SupabaseGameData.updateUsername(
                    SupabaseAuth.userId,
                    SupabaseAuth.accessToken,
                    entered,
                    success -> {
                        if (success) {
                            statusLabel.setText("Username updated.");
                            statusLabel.setColor(Color.GREEN);
                        } else {
                            statusLabel.setText("Failed to update (maybe taken?). Try another.");
                            statusLabel.setColor(Color.RED);
                        }
                        saveUsernameButton.setDisabled(false);
                    }
                );
            }
        });

        // --- BASIC ACCOUNT INFO ---
        Label userIdLabel = new Label("User ID: " + SupabaseAuth.userId, skin);
        userIdLabel.setWrap(false);
        userIdLabel.setAlignment(Align.left);

        cashLabel = new Label("Cash: $" + formatCash(CashManager.getCash()), skin);

        // --- EXTRA STATS ---

        // Owned cars
        int ownedCars = CarOwnershipManager.getOwnedCars().size();
        ownedCarsLabel = new Label("Owned cars: " + ownedCars, skin);

        // Best scores per mode
        int bestFreeRide     = HighScoreManager.getHighScore("free_ride");
        int bestTimeTrial    = HighScoreManager.getHighScore("time_trial");
        int bestEndless1     = HighScoreManager.getHighScore("endless_one_way");
        int bestEndless2     = HighScoreManager.getHighScore("endless_two_way");
        int bestDragSprint   = HighScoreManager.getHighScore("drag_sprint");

        bestFreeRideLabel      = new Label("Free Ride: " + bestFreeRide, skin);
        bestTimeTrialLabel     = new Label("Time Trial: " + bestTimeTrial, skin);
        bestEndlessOneWayLabel = new Label("Endless One Way: " + bestEndless1, skin);
        bestEndlessTwoWayLabel = new Label("Endless Two Way: " + bestEndless2, skin);
        bestDragSprintLabel    = new Label("Drag Sprint: " + bestDragSprint, skin);

        // BUTTONS AT BOTTOM
        TextButton backButton = new TextButton("Back to Menu", skin);
        backButton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });

        TextButton logoutButton = new TextButton("Log out", skin);
        logoutButton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                Dialog confirm = new Dialog("Log out", skin) {
                    @Override
                    protected void result(Object obj) {
                        boolean confirmed = (Boolean) obj;
                        if (confirmed) {
                            SupabaseAuth.logout();
                            game.setScreen(new LoginScreen(game));
                        }
                    }
                };
                confirm.text("Are you sure you want to log out?");
                confirm.button("Yes", true);
                confirm.button("No", false);
                confirm.show(stage);
            }
        });

        TextButton changePwButton = new TextButton("Change Password", skin);
        changePwButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new ChangePasswordScreen(game));
            }
        });


        // Title
        root.add(title).colspan(2).padBottom(30).center().row();

        // Username row
        root.add(usernameLabel).left().padBottom(10);
        root.add(usernameField).width(300).padBottom(10).row();

        root.add(saveUsernameButton).left().padBottom(10);
        root.add(statusLabel).left().padBottom(10).row();

        // Spacer
        root.add().colspan(2).height(20).row();

        // Account info
        root.add(userIdLabel).left().colspan(2).padBottom(10).row();
        root.add(cashLabel).left().colspan(2).padBottom(10).row();

        // Owned cars
        root.add(ownedCarsLabel).left().colspan(2).padBottom(10).row();

        // Spacer
        root.add().colspan(2).height(20).row();

        // High score section title
        Label highsTitle = new Label("Best Scores (per mode)", skin);
        highsTitle.setFontScale(1.2f);
        root.add(highsTitle).left().colspan(2).padBottom(10).row();

        // Table of best scores
        Table highsTable = new Table(skin);
        highsTable.left();
        highsTable.defaults().pad(3).left();

        highsTable.add(bestFreeRideLabel).left().row();
        highsTable.add(bestTimeTrialLabel).left().row();
        highsTable.add(bestEndlessOneWayLabel).left().row();
        highsTable.add(bestEndlessTwoWayLabel).left().row();
        highsTable.add(bestDragSprintLabel).left().row();

        root.add(highsTable).left().colspan(2).padBottom(10).row();

        // Spacer
        root.add().colspan(2).expandY().row();

        // Bottom buttons
        Table bottomButtons = new Table();
        bottomButtons.add(backButton).width(160).padRight(20);
        bottomButtons.add(logoutButton).width(160);
        bottomButtons.add(changePwButton);

        root.add(bottomButtons).colspan(2).right().padTop(20);

        // Load existing username from Supabase
        SupabaseGameData.fetchUsername(
            SupabaseAuth.userId,
            SupabaseAuth.accessToken,
            username -> {
                if (username != null && !username.trim().isEmpty()) {
                    usernameField.setText(username);
                }
            }
        );
    }

    private String formatCash(int cash) {
        return String.format("%,d", cash);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Live updates if these change while the screen is open
        cashLabel.setText("Cash: $" + formatCash(CashManager.getCash()));

        int ownedCars = CarOwnershipManager.getOwnedCars().size();
        ownedCarsLabel.setText("Owned cars: " + ownedCars);

        bestFreeRideLabel.setText("Free Ride: " + HighScoreManager.getHighScore("free_ride"));
        bestTimeTrialLabel.setText("Time Trial: " + HighScoreManager.getHighScore("time_trial"));
        bestEndlessOneWayLabel.setText("Endless One Way: " + HighScoreManager.getHighScore("endless_one_way"));
        bestEndlessTwoWayLabel.setText("Endless Two Way: " + HighScoreManager.getHighScore("endless_two_way"));
        bestDragSprintLabel.setText("Drag Sprint: " + HighScoreManager.getHighScore("drag_sprint"));

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
        dispose();
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();
    }
}
