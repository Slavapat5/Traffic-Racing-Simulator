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
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;

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

        // title
        Label title = new Label("Account", skin);
        title.setFontScale(2f);
        title.setAlignment(Align.center);

        // username edit
        Label usernameLabel = new Label("Username", skin);
        usernameField = new TextField("", skin);
        usernameField.setMessageText("Enter username");

        // error label
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


                // saves the username to the private profile table, then also updates the public username used for leaderboards.
                SupabaseGameData.updateUsername(
                    SupabaseAuth.userId,
                    SupabaseAuth.accessToken,
                    entered,
                    success -> {
                        if (success) {
                            statusLabel.setText("Username updated.");
                            statusLabel.setColor(Color.GREEN);

                            SupabaseGameData.upsertPublicUsername(
                                SupabaseAuth.userId,
                                SupabaseAuth.accessToken,
                                entered,
                                publicSuccess -> {
                                    if (!publicSuccess) {
                                        System.out.println("Warning: public username did not update.");
                                    }
                                }
                            );
                        } else {
                            statusLabel.setText("Failed to update (maybe taken?). Try another.");
                            statusLabel.setColor(Color.RED);
                        }
                        saveUsernameButton.setDisabled(false);
                    }
                );
            }
        });

        // Basic Account Info
        Label userIdLabel = new Label("User ID: " + SupabaseAuth.userId, skin);
        userIdLabel.setWrap(false);
        userIdLabel.setAlignment(Align.left);

        // displays account progression currently loaded into the local manager classes
        cashLabel = new Label("Cash: $" + formatCash(CashManager.getCash()), skin);


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

        // buttons at the bottom
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

        TextButton logoutAllDevicesButton = new TextButton("Log out all devices", skin);

        TextButton exportDataButton = new TextButton("Export Data", skin);

        exportDataButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                exportAccountData();
            }
        });

        TextButton privacyNoticeButton = new TextButton("Privacy Notice", skin);

        privacyNoticeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Dialog privacyDialog = new Dialog("Privacy Notice", skin);

                Label privacyText = new Label(
                    "Traffic Racing Simulator is a project game.\n\n" +

                        "If you create an account, the game stores account and gameplay data using Supabase. " +
                        "This may include your email address for login, username, user ID, cash balance, owned cars, " +
                        "leaderboard scores, achievements, and two-factor authentication status.\n\n" +

                        "This data is used only to let you log in, save progress, display leaderboards, " +
                        "and secure your account.\n\n" +

                        "Passwords are handled by Supabase Auth and are not stored directly by the game.\n\n" +

                        "Gameplay data may be used for testing, debugging, and demonstrating the project.\n\n" +

                        "This project is not a commercial product.",
                    skin
                );

                privacyText.setWrap(true);
                privacyText.setAlignment(Align.topLeft);

                Table privacyContent = new Table();
                privacyContent.top().left();
                privacyContent.add(privacyText).width(620).left().top().pad(10);

                ScrollPane scrollPane = new ScrollPane(privacyContent, skin);
                scrollPane.setFadeScrollBars(false);
                scrollPane.setScrollingDisabled(true, false);

                privacyDialog.getContentTable().pad(15);
                privacyDialog.getContentTable().add(scrollPane).width(650).height(320).fill().expand();

                privacyDialog.button("OK");
                privacyDialog.show(stage);

                privacyDialog.setSize(720, 430);
                privacyDialog.setPosition(
                    (stage.getWidth() - privacyDialog.getWidth()) / 2f,
                    (stage.getHeight() - privacyDialog.getHeight()) / 2f
                );
            }
        });

        logoutAllDevicesButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Dialog confirmDialog = new Dialog("Confirm", skin) {
                    @Override
                    protected void result(Object object) {
                        if ((Boolean) object) {
                            SupabaseAuth.logoutAllDevices(success -> {
                                Dialog resultDialog = new Dialog("Signed out", skin);
                                if (success) {
                                    resultDialog.text("You have been signed out on all devices.");
                                } else {
                                    resultDialog.text("Local session cleared. Other devices may already be expired or could not be contacted.");
                                }
                                resultDialog.button("OK");
                                resultDialog.show(stage);

                                game.setScreen(new LoginScreen(game));
                            });
                        }
                    }
                };

                confirmDialog.text("Log out from all devices?");
                confirmDialog.button("Yes", true);
                confirmDialog.button("No", false);
                confirmDialog.show(stage);
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
        bottomButtons.defaults().padRight(10);

        bottomButtons.add(backButton).width(160);
        bottomButtons.add(logoutButton).width(160);
        bottomButtons.add(changePwButton).width(170);
        bottomButtons.add(logoutAllDevicesButton).width(190);
        bottomButtons.add(exportDataButton).width(160);
        bottomButtons.add(privacyNoticeButton).width(180);

        root.add(bottomButtons).colspan(2).right().padTop(20);

        // load existing username from Supabase
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

    // exports a local JSON copy of the players account data for transparency or testing
    private void exportAccountData() {
        try {
            JSONObject export = new JSONObject();

            export.put("exported_at", Instant.now().toString());
            export.put("game", "Traffic Racing Simulator");

            JSONObject account = new JSONObject();
            account.put("user_id", SupabaseAuth.userId);
            account.put("username", usernameField.getText().trim());
            account.put("cash", CashManager.getCash());

            export.put("account", account);

            JSONArray ownedCars = new JSONArray();
            for (String car : CarOwnershipManager.getOwnedCars()) {
                ownedCars.put(car);
            }
            export.put("owned_cars", ownedCars);

            JSONObject highScores = new JSONObject();
            highScores.put("free_ride", HighScoreManager.getHighScore("free_ride"));
            highScores.put("time_trial", HighScoreManager.getHighScore("time_trial"));
            highScores.put("endless_one_way", HighScoreManager.getHighScore("endless_one_way"));
            highScores.put("endless_two_way", HighScoreManager.getHighScore("endless_two_way"));
            highScores.put("drag_sprint", HighScoreManager.getHighScore("drag_sprint"));

            export.put("high_scores", highScores);

            String safeUserId = SupabaseAuth.userId == null
                ? "unknown_user"
                : SupabaseAuth.userId.replaceAll("[^a-zA-Z0-9_-]", "_");

            String fileName = "account_export_" + safeUserId + ".json";

            com.badlogic.gdx.files.FileHandle exportDir = Gdx.files.local("exports");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            com.badlogic.gdx.files.FileHandle file = Gdx.files.local("exports/" + fileName);
            file.writeString(export.toString(4), false, "UTF-8");

            statusLabel.setColor(Color.GREEN);
            statusLabel.setText("Data exported to: " + file.path());

            Dialog dialog = new Dialog("Export Complete", skin);
            dialog.text("Your account data was exported to:\n" + file.path());
            dialog.button("OK");
            dialog.show(stage);

            System.out.println("Account data exported to: " + file.file().getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();

            statusLabel.setColor(Color.RED);
            statusLabel.setText("Failed to export account data.");

            Dialog dialog = new Dialog("Export Failed", skin);
            dialog.text("Could not export account data.\nCheck the console for details.");
            dialog.button("OK");
            dialog.show(stage);
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // live updates if these change while the screen is open
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
