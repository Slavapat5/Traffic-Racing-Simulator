package com.manogstudios.racingsimulator;


import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;



import java.util.ArrayList;
import java.util.List;

public class DragRaceScreen implements Screen {

    private final Game game;

    // Rendering
    private SpriteBatch batch;
    private OrthographicCamera camera;

    // World
    private Texture roadTexture;

    private static final float VIEW_WIDTH = 1920f;
    private static final float VIEW_HEIGHT = 1080f;

    // Drag strip settings
    private static final float STRIP_CENTER_X = VIEW_WIDTH / 2f;
    private static final float STRIP_WIDTH = 900f;

    // Two lanes (player right lane, opponent left lane)
    private float laneWidth;
    private float leftLaneX;
    private float rightLaneX;

    // Finish line distance (world units, same scale as Y movement)
    private static final float FINISH_DISTANCE = 25000f; // tweak

    // Cars
    private Car playerCar;
    private Car aiCar;
    private List<Rectangle> borderRectangles;

    // State
    private float startY;
    private boolean raceStarted = false;
    private boolean raceFinished = false;

    // Countdown
    private float countdown = 3.0f; // 3..2..1..GO
    private String countdownText = "3";

    // Timing
    private float playerFinishTime = -1f;
    private float aiFinishTime = -1f;
    private float elapsedRaceTime = 0f;

    // UI
    private Stage uiStage;
    private Skin skin;
    private Label playerInfoLabel;
    private Label aiInfoLabel;
    private Label statusLabel;
    private Label cashLabel;

    // Opponent selection results
    private String opponentTexture;
    private CarStats playerStats;
    private CarStats opponentStats;

    private boolean paused = false;
    private Table pauseOverlay;
    private Table pauseCard;

    private Table pauseSettingsOverlay;
    private Table pauseSettingsCard;

    public DragRaceScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, VIEW_WIDTH, VIEW_HEIGHT);

        skin = new Skin(Gdx.files.internal("uiskin.json"));
        uiStage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(uiStage);

        // Reuse road segment texture
        roadTexture = new Texture(Gdx.files.internal("Segment1.png"));

        // Lane layout
        laneWidth = STRIP_WIDTH / 2f;
        leftLaneX = STRIP_CENTER_X - laneWidth / 2f;  // opponent
        rightLaneX = STRIP_CENTER_X + laneWidth / 2f; // player

        // Borders (keep both cars on strip)
        borderRectangles = new ArrayList<>();
        float leftEdge = STRIP_CENTER_X - STRIP_WIDTH / 2f;
        float rightEdge = STRIP_CENTER_X + STRIP_WIDTH / 2f;
        borderRectangles.add(new Rectangle(leftEdge - 50f, -100000f, 50f, 200000f));
        borderRectangles.add(new Rectangle(rightEdge, -100000f, 50f, 200000f));

        //  Pick player + opponent
        String selectedCarTexture = CarSelectionData.getSelectedCarTexture();
        playerStats = CarRegistry.getStats(selectedCarTexture);

        opponentTexture = pickOpponentSameClassClosestPI(selectedCarTexture, playerStats);
        opponentStats = CarRegistry.getStats(opponentTexture);

        // Spawn cars
        float startYWorld = 0f;
        this.startY = startYWorld;

        float MIN_SPEED = 0f; // drag race: allow standstill
        playerCar = new Car(
            selectedCarTexture,
            rightLaneX,
            startYWorld,
            playerStats.acceleration,
            playerStats.speed,
            playerStats.handling,
            MIN_SPEED
        );

        aiCar = new Car(
            opponentTexture,
            leftLaneX,
            startYWorld,
            opponentStats.acceleration,
            opponentStats.speed,
            opponentStats.handling,
            MIN_SPEED
        );

        // --- UI ---


        Table cashTable = new Table();
        cashTable.setFillParent(true);
        cashTable.top().left().padTop(18).padLeft(20);

        cashLabel = new Label("$" + formatCash(CashManager.getCash()), skin);
        cashLabel.setFontScale(1.35f);
        cashLabel.setAlignment(Align.left);

        cashTable.add(cashLabel).left();
        uiStage.addActor(cashTable);


        Table statusTable = new Table();
        statusTable.setFillParent(true);
        statusTable.top().padTop(18);

        statusLabel = new Label("Get ready...", skin);
        statusLabel.setFontScale(2.2f);
        statusLabel.setAlignment(Align.center);

        statusTable.add(statusLabel).center();
        uiStage.addActor(statusTable);

        Table aiTable = new Table();
        aiTable.setFillParent(true);
        aiTable.bottom().left().padLeft(30).padBottom(120);

        aiInfoLabel = new Label("", skin);
        aiInfoLabel.setFontScale(1.5f);
        aiInfoLabel.setAlignment(Align.left);

        aiTable.add(aiInfoLabel).left();
        uiStage.addActor(aiTable);


        Table playerTable = new Table();
        playerTable.setFillParent(true);
        playerTable.bottom().right().padRight(30).padBottom(120);

        playerInfoLabel = new Label("", skin);
        playerInfoLabel.setFontScale(1.7f);
        playerInfoLabel.setAlignment(Align.right);

        playerTable.add(playerInfoLabel).right();
        uiStage.addActor(playerTable);


        Table pauseTable = new Table();
        pauseTable.setFillParent(true);
        pauseTable.top().right().padTop(18).padRight(20);

        TextButton pauseButton = new TextButton("Pause", skin);
        pauseTable.add(pauseButton).width(100f).height(42f);

        uiStage.addActor(pauseTable);


        createPauseOverlay();
        createPauseSettingsOverlay();

        pauseButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                setPaused(true);
            }
        });

// initial UI text
        updateInfoLabels();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (!raceFinished && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (paused && pauseSettingsOverlay != null && pauseSettingsOverlay.isVisible()) {
                showPauseMenu();
            } else {
                setPaused(!paused);
            }
        }

        if (!raceFinished && !paused) {
            updateLogic(delta);
        }

        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        //  infinite segment draw
        float segH = 1080f;
        float segW = 1920f;
        float baseY = (float) Math.floor(camera.position.y / segH) * segH;
        float roadX = STRIP_CENTER_X - segW / 2f;

        for (int i = -1; i <= 1; i++) {
            float y = baseY + i * segH;
            batch.draw(roadTexture, roadX, y, segW, segH);
        }

        // render cars
        aiCar.render(batch);
        playerCar.render(batch);

        batch.end();

        uiStage.act(delta);
        uiStage.draw();
    }

    private void adjustCashServer(int delta, String reasonLabel, Runnable after) {
        if (!SupabaseAuth.isLoggedIn) {
            System.out.println("adjustCash blocked (not logged in): " + reasonLabel);
            if (after != null) after.run();
            return;
        }

        SupabaseGameData.adjustCash(
            delta,
            newCash -> {
                if (newCash >= 0) {
                    CashManager.setCash(newCash);
                    CashManager.saveCash();
                    cashLabel.setText("$" + formatCash(CashManager.getCash()));
                } else {
                    // If adjustCash sometimes returns -1, can reload profile instead
                    System.out.println("adjustCash returned -1 (" + reasonLabel + ")");
                }
                if (after != null) after.run();
            },
            err -> {
                System.out.println("adjustCash failed (" + reasonLabel + "): " + err);
                if (after != null) after.run();
            }
        );
    }


    private void updateLogic(float delta) {
        // follow midpoint of both cars (keeps them on screen)
        float camY = Math.max(playerCar.getY(), aiCar.getY()) + 350f;
        camera.position.set(STRIP_CENTER_X, camY, 0);

        // countdown until start
        if (!raceStarted) {
            countdown -= delta;

            if (countdown > 2f) countdownText = "3";
            else if (countdown > 1f) countdownText = "2";
            else if (countdown > 0f) countdownText = "1";
            else countdownText = "GO!";

            statusLabel.setText(countdownText);

            if (countdown <= 0f) {
                raceStarted = true;
                statusLabel.setText("RACE!");
            }

            // while waiting, keep cars still
            updateInfoLabels();
            return;
        }

        elapsedRaceTime += delta;

        // Player controls: W accelerate, S brake, no steering in drag race
        boolean moveForward = Gdx.input.isKeyPressed(Input.Keys.W);
        boolean brake = Gdx.input.isKeyPressed(Input.Keys.S);

        // lock steering off (A/D false)
        playerCar.update(delta, moveForward, brake, false, false, borderRectangles);

        // AI behaviour: always full throttle (no steering)
        aiCar.update(delta, true, false, false, false, borderRectangles);

        updateInfoLabels();

        // finish checks
        float playerDist = playerCar.getY() - startY;
        float aiDist = aiCar.getY() - startY;

        if (playerFinishTime < 0f && playerDist >= FINISH_DISTANCE) {
            playerFinishTime = elapsedRaceTime;
        }
        if (aiFinishTime < 0f && aiDist >= FINISH_DISTANCE) {
            aiFinishTime = elapsedRaceTime;
        }

        if (playerFinishTime >= 0f || aiFinishTime >= 0f) {
            // when both have a time, decide winner
            if (playerFinishTime >= 0f && aiFinishTime >= 0f) {
                finishRace();
            } else {
                // if one finished, allow a short grace so the other can finish
                //  here it ends as soon as someone finishes + 0.6s
                if (elapsedRaceTime - Math.min(
                    (playerFinishTime >= 0f ? playerFinishTime : elapsedRaceTime),
                    (aiFinishTime >= 0f ? aiFinishTime : elapsedRaceTime)
                ) > 0.6f) {
                    finishRace();
                }
            }

            //Leaderboard submit (drag_sprint)

             // Faster time => higher score.
            if (playerFinishTime > 0f) {
                int timeScore = (int)(1_000_000f / playerFinishTime);

                // Local best
                // Server-only leaderboard submit
                if (SupabaseAuth.isLoggedIn) {
                    SupabaseGameData.submitScore("drag_sprint", timeScore);
                }

            }

        }
    }

    private void createPauseOverlay() {
        pauseOverlay = new Table();
        pauseOverlay.setFillParent(true);
        pauseOverlay.setVisible(false);
        pauseOverlay.setBackground(skin.newDrawable("white", 0f, 0f, 0f, 0.55f));

        pauseCard = new Table(skin);
        pauseCard.setBackground("default-round");
        pauseCard.pad(25);
        pauseCard.defaults().pad(10).width(240).height(50);

        Label titleLabel = new Label("Paused", skin);
        titleLabel.setFontScale(1.4f);
        titleLabel.setAlignment(Align.center);

        TextButton continueButton = new TextButton("Continue", skin);
        TextButton settingsButton = new TextButton("Settings", skin);
        TextButton restartButton = new TextButton("Restart", skin);
        TextButton quitButton = new TextButton("Quit", skin);

        pauseCard.add(titleLabel).padBottom(15).row();
        pauseCard.add(continueButton).row();
        pauseCard.add(settingsButton).row();
        pauseCard.add(restartButton).row();
        pauseCard.add(quitButton).row();

        pauseOverlay.add(pauseCard).center();
        uiStage.addActor(pauseOverlay);

        continueButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                setPaused(false);
            }
        });

        settingsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showPauseSettings();
            }
        });

        restartButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new FreeRideScreen(game)); // replace per screen
            }
        });

        quitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Dialog confirmDialog = new Dialog("Quit Run", skin) {
                    @Override
                    protected void result(Object object) {
                        if ((Boolean) object) {
                            game.setScreen(new GameModeSelectorScreen(game));
                        }
                    }
                };

                confirmDialog.text("Quit this run and return to game modes?");
                confirmDialog.button("Yes", true);
                confirmDialog.button("No", false);
                confirmDialog.show(uiStage);
            }
        });
    }

    private void createPauseSettingsOverlay() {
        pauseSettingsOverlay = new Table();
        pauseSettingsOverlay.setFillParent(true);
        pauseSettingsOverlay.setVisible(false);
        pauseSettingsOverlay.setBackground(skin.newDrawable("white", 0f, 0f, 0f, 0.55f));

        pauseSettingsCard = new Table(skin);
        pauseSettingsCard.setBackground("default-round");
        pauseSettingsCard.pad(25);
        pauseSettingsCard.defaults().pad(10).width(260).height(50);

        Label titleLabel = new Label("Pause Settings", skin);
        titleLabel.setFontScale(1.3f);
        titleLabel.setAlignment(Align.center);

        TextButton fullscreenButton = new TextButton(getFullscreenText(), skin);
        TextButton controlsButton = new TextButton("Controls / Help", skin);
        TextButton backButton = new TextButton("Back", skin);

        pauseSettingsCard.add(titleLabel).padBottom(15).row();
        pauseSettingsCard.add(fullscreenButton).row();
        pauseSettingsCard.add(controlsButton).row();
        pauseSettingsCard.add(backButton).row();

        pauseSettingsOverlay.add(pauseSettingsCard).center();
        uiStage.addActor(pauseSettingsOverlay);

        fullscreenButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                boolean enableFullscreen = !Gdx.graphics.isFullscreen();

                if (enableFullscreen) {
                    Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
                } else {
                    Gdx.graphics.setWindowedMode(1600, 900);
                }

                fullscreenButton.setText(getFullscreenText());
            }
        });

        controlsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showControlsDialog();
            }
        });

        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showPauseMenu();
            }
        });
    }

    private void showPauseMenu() {
        if (pauseOverlay != null) pauseOverlay.setVisible(true);
        if (pauseSettingsOverlay != null) pauseSettingsOverlay.setVisible(false);
    }

    private void showPauseSettings() {
        if (pauseOverlay != null) pauseOverlay.setVisible(false);
        if (pauseSettingsOverlay != null) pauseSettingsOverlay.setVisible(true);
    }

    private void setPaused(boolean value) {
        paused = value;

        if (!value) {
            if (pauseOverlay != null) pauseOverlay.setVisible(false);
            if (pauseSettingsOverlay != null) pauseSettingsOverlay.setVisible(false);
        } else {
            showPauseMenu();
        }
    }

    private String getFullscreenText() {
        return "Fullscreen: " + (Gdx.graphics.isFullscreen() ? "ON" : "OFF");
    }

    private void showControlsDialog() {
        Dialog controlsDialog = new Dialog("Controls", skin);

        Label controlsLabel = new Label(
            "W = Accelerate\n" +
                "S = Brake / Reverse\n" +
                "A = Steer Left\n" +
                "D = Steer Right\n" +
                "ESC = Pause / Back\n" +
                "Mouse = Click buttons and menus\n\n" +
                "Notes:\n" +
                "- In driving modes, ESC opens the pause menu.\n" +
                "- In pause settings, use Back to return to the pause menu.\n" +
                "- Drag Race uses W to accelerate and S to brake.",
            skin
        );

        controlsLabel.setWrap(true);
        controlsLabel.setAlignment(Align.left);

        controlsDialog.getContentTable().add(controlsLabel).width(420).pad(20);
        controlsDialog.button("OK");
        controlsDialog.show(uiStage);
    }



    private void finishRace() {
        raceFinished = true;

        boolean playerWon;
        if (playerFinishTime >= 0f && aiFinishTime >= 0f) {
            playerWon = playerFinishTime <= aiFinishTime;
        } else {
            // only one finished within grace
            playerWon = (playerFinishTime >= 0f);
        }

        String resultTitle = playerWon ? "You Win!" : "You Lose!";
        statusLabel.setText(resultTitle);

        int reward = 0;
        if (playerWon) {
            reward = MathUtils.clamp(100 + (playerStats.pi / 5), 100, 600);

            int finalReward = reward;
            adjustCashServer(finalReward, "DragRace win", () -> {
                // cashLabel is updated inside adjustCashServer
            });
        }

        cashLabel.setText("$" + formatCash(CashManager.getCash()));

        String playerTimeStr = (playerFinishTime >= 0f) ? String.format("%.2fs", playerFinishTime) : "DNF";
        String aiTimeStr = (aiFinishTime >= 0f) ? String.format("%.2fs", aiFinishTime) : "DNF";

        StringBuilder sb = new StringBuilder();
        sb.append("Player: ").append(playerTimeStr).append("\n");
        sb.append("Opponent: ").append(aiTimeStr).append("\n\n");
        sb.append("Your car: ").append(playerStats.carClass).append(" (PI ").append(playerStats.pi).append(")\n");
        sb.append("Opponent: ").append(opponentStats.carClass).append(" (PI ").append(opponentStats.pi).append(")\n");

        if (playerWon) {
            sb.append("\nCash earned: $").append(reward);
        }

        Dialog dialog = new Dialog(resultTitle, skin) {
            @Override
            protected void result(Object obj) {
                String choice = (String) obj;
                if ("retry".equals(choice)) {
                    game.setScreen(new DragRaceScreen(game));
                } else if ("modes".equals(choice)) {
                    game.setScreen(new GameModeSelectorScreen(game));
                }
            }
        };

        dialog.text(sb.toString());
        dialog.button("Retry", "retry");
        dialog.button("Back to Modes", "modes");
        dialog.show(uiStage);
    }

    private void updateInfoLabels() {
        float pMph = playerCar.getSpeed() / 10f;
        float aMph = aiCar.getSpeed() / 10f;

        playerInfoLabel.setText("You: " + playerStats.carClass + " " + playerStats.pi + "  |  " + (int)pMph + " mph");
        aiInfoLabel.setText("AI: " + opponentStats.carClass + " " + opponentStats.pi + "  |  " + (int)aMph + " mph");
    }

    /**
     * Picks an opponent car from CarDataBase / CarRegistry that:
     * - is not the player's exact car
     * - has same CarClass
     * - closest PI to player (fair fight)
     *
     * Falls back to any other car if something is missing.
     */
    private String pickOpponentSameClassClosestPI(String playerTexture, CarStats playerStats) {
        CarDataBase.load();

        CarClass targetClass = (playerStats != null) ? playerStats.carClass : CarClass.D;
        int targetPI = (playerStats != null) ? playerStats.pi : 0;

        String bestTexture = null;
        int bestDiff = Integer.MAX_VALUE;

        for (CarData car : CarDataBase.getAllCars()) {
            if (car == null || car.image == null) continue;
            if (car.image.equals(playerTexture)) continue;

            CarStats s = CarRegistry.getStats(car.image);
            if (s == null || s.carClass == null) continue;

            if (s.carClass != targetClass) continue;

            int diff = Math.abs(s.pi - targetPI);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestTexture = car.image;
            }
        }

        // Fallback: if no same-class found, pick any other
        if (bestTexture == null) {
            for (CarData car : CarDataBase.getAllCars()) {
                if (car != null && car.image != null && !car.image.equals(playerTexture)) {
                    bestTexture = car.image;
                    break;
                }
            }
        }

        // Final fallback
        if (bestTexture == null) bestTexture = playerTexture;

        return bestTexture;
    }

    private String formatCash(int cash) {
        return String.format("%,d", cash);
    }

    @Override
    public void resize(int width, int height) {
        uiStage.getViewport().update(width, height, true);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        batch.dispose();
        roadTexture.dispose();
        uiStage.dispose();
        skin.dispose();
        if (playerCar != null) playerCar.dispose();
        if (aiCar != null) aiCar.dispose();
    }
}
