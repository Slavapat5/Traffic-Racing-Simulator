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
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Image;

public class DragRaceScreen implements Screen {

    private final Game game;

    // Rendering
    private SpriteBatch batch;
    private OrthographicCamera camera;

    // World
    private Texture roadTexture;

    private Texture cashBgTexture;
    private Texture raceTitleTexture;
    private Texture youTitleTexture;
    private Texture opponentTitleTexture;

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

    private AchievementToastManager achievementToasts;
    private float achievementCheckTimer = 0f;
    private static final float ACHIEVEMENT_CHECK_INTERVAL = 0.15f;

    private float maxPlayerMph = 0f;
    private boolean dragScoreSubmitted = false;

    // UI
    private Stage uiStage;
    private Skin skin;

    private Label playerSpeedLabel;
    private Label playerClassLabel;
    private Label playerPiLabel;

    private Label aiSpeedLabel;
    private Label aiClassLabel;
    private Label aiPiLabel;

    private Label statusLabel;
    private Label cashLabel;

    private Table raceResultOverlay;

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

        achievementToasts = new AchievementToastManager(uiStage, skin);

        // Reuse road segment texture
        roadTexture = new Texture(Gdx.files.internal("Segment1.png"));

        cashBgTexture = new Texture(Gdx.files.internal("Cash_Label.png"));

        raceTitleTexture = new Texture(Gdx.files.internal("Game_Label_Race.png"));
        youTitleTexture = new Texture(Gdx.files.internal("Game_Label_You.png"));
        opponentTitleTexture = new Texture(Gdx.files.internal("Game_Label_Opponent.png"));

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
        cashTable.top().left().padTop(10).padLeft(20);

        cashLabel = new Label("$" + formatCash(CashManager.getCash()), skin);
        cashLabel.setFontScale(1.25f);
        cashLabel.setAlignment(Align.center);

        Table cashBox = new Table();
        cashBox.setBackground(new TextureRegionDrawable(new TextureRegion(cashBgTexture)));
        cashBox.add(cashLabel).center().padLeft(22f).padRight(22f).padTop(8f).padBottom(8f);

        cashTable.add(cashBox).width(180f).height(38f).left();
        uiStage.addActor(cashTable);


        Table statusTable = new Table();
        statusTable.setFillParent(true);
        statusTable.top().padTop(10);

        Image raceTitleImage = new Image(raceTitleTexture);

        statusLabel = new Label("Get ready...", skin);
        statusLabel.setFontScale(2.0f);
        statusLabel.setAlignment(Align.center);

        statusTable.add(raceTitleImage).width(220).height(60).center().padBottom(4).row();
        statusTable.add(statusLabel).center();

        uiStage.addActor(statusTable);


        Table aiTable = new Table();
        aiTable.setFillParent(true);
        aiTable.bottom().left().padLeft(0).padBottom(80);

        Image opponentTitleImage = new Image(opponentTitleTexture);

        aiSpeedLabel = new Label("0 mph", skin);
        aiSpeedLabel.setFontScale(2.2f);
        aiSpeedLabel.setAlignment(Align.left);
        aiSpeedLabel.setColor(Color.WHITE);

        aiClassLabel = new Label("", skin);
        aiClassLabel.setFontScale(1.45f);
        aiClassLabel.setAlignment(Align.left);

        aiPiLabel = new Label("", skin);
        aiPiLabel.setFontScale(1.15f);
        aiPiLabel.setAlignment(Align.left);
        aiPiLabel.setColor(Color.LIGHT_GRAY);

        Table aiInfoCard = new Table(skin);
        aiInfoCard.setBackground(skin.newDrawable("white", 0.04f, 0.04f, 0.04f, 0.75f));
        aiInfoCard.pad(12);
        aiInfoCard.defaults().left().padBottom(4);

        aiInfoCard.add(aiSpeedLabel).left().row();
        aiInfoCard.add(aiClassLabel).left().row();
        aiInfoCard.add(aiPiLabel).left().row();

        aiTable.add(opponentTitleImage).width(260).height(65).left().padBottom(4).row();
        aiTable.add(aiInfoCard).width(250).left();

        uiStage.addActor(aiTable);

        Table playerTable = new Table();
        playerTable.setFillParent(true);
        playerTable.bottom().right().padRight(0).padBottom(80);

        Image youTitleImage = new Image(youTitleTexture);

        playerSpeedLabel = new Label("0 mph", skin);
        playerSpeedLabel.setFontScale(2.4f);
        playerSpeedLabel.setAlignment(Align.right);
        playerSpeedLabel.setColor(Color.WHITE);

        playerClassLabel = new Label("", skin);
        playerClassLabel.setFontScale(1.55f);
        playerClassLabel.setAlignment(Align.right);

        playerPiLabel = new Label("", skin);
        playerPiLabel.setFontScale(1.2f);
        playerPiLabel.setAlignment(Align.right);
        playerPiLabel.setColor(Color.LIGHT_GRAY);

        Table playerInfoCard = new Table(skin);
        playerInfoCard.setBackground(skin.newDrawable("white", 0.04f, 0.04f, 0.04f, 0.75f));
        playerInfoCard.pad(12);
        playerInfoCard.defaults().right().padBottom(4);

        playerInfoCard.add(playerSpeedLabel).right().row();
        playerInfoCard.add(playerClassLabel).right().row();
        playerInfoCard.add(playerPiLabel).right().row();

        playerTable.add(youTitleImage).width(200).height(65).right().padBottom(4).row();
        playerTable.add(playerInfoCard).width(250).right();

        uiStage.addActor(playerTable);

        Table pauseTable = new Table();
        pauseTable.setFillParent(true);
        pauseTable.top().right().padTop(10).padRight(10);

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

        updateInfoLabels();

        maxPlayerMph = 0f;
        achievementCheckTimer = 0f;
        dragScoreSubmitted = false;
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

    private void checkLiveAchievements() {
        if (achievementToasts == null) return;

        Array<AchievementsManager.AchievementState> newlyUnlocked =
            AchievementsManager.onDragRaceLiveUpdate(maxPlayerMph);

        achievementToasts.queueAll(newlyUnlocked);
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

        float playerMph = playerCar.getSpeed() / 10f;
        if (playerMph > maxPlayerMph) {
            maxPlayerMph = playerMph;
        }

        achievementCheckTimer -= delta;
        if (achievementCheckTimer <= 0f && !raceFinished) {
            achievementCheckTimer = ACHIEVEMENT_CHECK_INTERVAL;
            checkLiveAchievements();
        }

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
                game.setScreen(new DragRaceScreen(game));
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

    private Table createResultStatBox(String titleText, String valueText, Color valueColor) {
        Table box = new Table(skin);
        box.setBackground(skin.newDrawable("white", 0.08f, 0.08f, 0.08f, 0.95f));
        box.pad(16);

        Label title = new Label(titleText, skin);
        title.setFontScale(1.05f);
        title.setColor(Color.LIGHT_GRAY);
        title.setAlignment(Align.center);

        Label value = new Label(valueText, skin);
        value.setFontScale(1.55f);
        value.setColor(valueColor);
        value.setAlignment(Align.center);

        box.add(title).center().row();
        box.add(value).center().padTop(6);

        return box;
    }

    private void finishRace() {
        raceFinished = true;

        boolean playerWon;
        if (playerFinishTime >= 0f && aiFinishTime >= 0f) {
            playerWon = playerFinishTime <= aiFinishTime;
        } else {
            playerWon = (playerFinishTime >= 0f);
        }

        String resultTitle = playerWon ? "YOU WIN" : "YOU LOSE";
        statusLabel.setText(resultTitle);

        int reward = 0;
        if (playerWon) {
            reward = MathUtils.clamp(100 + (playerStats.pi / 5), 100, 600);
        }

        int playerDistMeters = (int) Math.max(0, (playerCar.getY() - startY) / 10f);

        int dragScore = 0;
        if (playerFinishTime > 0f) {
            dragScore = (int) (1_000_000f / playerFinishTime);
        }

        // Local best time score
        int previousBest = HighScoreManager.getHighScore("drag_sprint");
        boolean newBestScore = dragScore > previousBest;

        if (dragScore > 0) {
            HighScoreManager.submitScore("drag_sprint", dragScore);
        }

        int bestScore = HighScoreManager.getHighScore("drag_sprint");

        // Server leaderboard submit.
        if (!dragScoreSubmitted && dragScore > 0 && SupabaseAuth.isLoggedIn) {
            dragScoreSubmitted = true;
            SupabaseGameData.submitScore("drag_sprint", dragScore);
        }

        Array<AchievementsManager.AchievementState> newlyUnlocked =
            AchievementsManager.onDragRaceFinished(
                playerWon,
                playerFinishTime,
                aiFinishTime,
                maxPlayerMph,
                playerStats.pi,
                opponentStats.pi
            );

        if (achievementToasts != null) {
            achievementToasts.queueAll(newlyUnlocked);
        }

        if (SupabaseAuth.isLoggedIn) {
            SupabaseGameData.updateDailyQuests(
                "drag_sprint",
                playerDistMeters,
                dragScore,
                elapsedRaceTime,
                0,
                0,
                maxPlayerMph,
                playerWon,
                result -> {
                    if (result.cash != null) {
                        cashLabel.setText("$" + formatCash(CashManager.getCash()));
                    }

                    if (result.rewardCash > 0) {
                        System.out.println("Daily quest reward earned: $" + result.rewardCash);
                    }
                },
                err -> System.out.println("Daily quest update failed: " + err)
            );
        }

        int finalReward = reward;
        final int finalDragScore = dragScore;
        final int finalBestScore = bestScore;
        final boolean finalNewBestScore = newBestScore;
        final boolean finalPlayerWon = playerWon;

        Runnable showSummary = () -> {
            cashLabel.setText("$" + formatCash(CashManager.getCash()));
            showRaceResultOverlay(
                finalPlayerWon,
                finalReward,
                finalDragScore,
                finalBestScore,
                finalNewBestScore
            );
        };

        if (playerWon && finalReward > 0) {
            adjustCashServer(finalReward, "DragRace win", () -> {
                Gdx.app.postRunnable(showSummary);
            });
        } else {
            showSummary.run();
        }
    }

    private void updateInfoLabels() {
        float pMph = playerCar.getSpeed() / 10f;
        float aMph = aiCar.getSpeed() / 10f;

        playerSpeedLabel.setText((int) pMph + " mph");
        playerClassLabel.setText("Class " + playerStats.carClass);
        playerClassLabel.setColor(getClassColor(playerStats.carClass));
        playerPiLabel.setText("PI " + playerStats.pi);

        aiSpeedLabel.setText((int) aMph + " mph");
        aiClassLabel.setText("Class " + opponentStats.carClass);
        aiClassLabel.setColor(getClassColor(opponentStats.carClass));
        aiPiLabel.setText("PI " + opponentStats.pi);
    }

    private Color getClassColor(CarClass carClass) {
        String c = String.valueOf(carClass);

        switch (c) {
            case "D":
                return Color.LIGHT_GRAY;
            case "C":
                return Color.GREEN;
            case "B":
                return Color.CYAN;
            case "A":
                return Color.BLUE;
            case "S1":
                return Color.PURPLE;
            case "S2":
                return Color.ORANGE;
            case "X":
                return Color.GOLD;
            default:
                return Color.WHITE;
        }
    }

    private void showRaceResultOverlay(boolean playerWon,
                                       int reward,
                                       int dragScore,
                                       int bestScore,
                                       boolean newBestScore) {
        if (raceResultOverlay != null) {
            raceResultOverlay.remove();
        }

        raceResultOverlay = new Table();
        raceResultOverlay.setFillParent(true);

        // Full-screen dim background.
        raceResultOverlay.setBackground(skin.newDrawable("white", 0f, 0f, 0f, 0.72f));

        Table panel = new Table(skin);
        panel.setBackground("default-round");
        panel.setColor(0.06f, 0.06f, 0.06f, 0.98f);
        panel.pad(35);
        panel.defaults().pad(8);

        Label titleLabel = new Label(playerWon ? "YOU WIN" : "YOU LOSE", skin);
        titleLabel.setFontScale(3.2f);
        titleLabel.setColor(playerWon ? Color.GREEN : Color.RED);
        titleLabel.setAlignment(Align.center);

        Label subtitleLabel = new Label("Drag Sprint Race Summary", skin);
        subtitleLabel.setFontScale(1.25f);
        subtitleLabel.setColor(Color.LIGHT_GRAY);
        subtitleLabel.setAlignment(Align.center);

        panel.add(titleLabel).center().padBottom(4).row();
        panel.add(subtitleLabel).center().padBottom(18).row();

        String playerTimeStr = (playerFinishTime >= 0f) ? String.format("%.2f s", playerFinishTime) : "DNF";
        String aiTimeStr = (aiFinishTime >= 0f) ? String.format("%.2f s", aiFinishTime) : "DNF";

        String gapText = "N/A";
        if (playerFinishTime >= 0f && aiFinishTime >= 0f) {
            float gap = Math.abs(playerFinishTime - aiFinishTime);
            gapText = String.format("%.2f s", gap);
        }

        // Time score section
        Table scoreBox = new Table(skin);
        scoreBox.setBackground(skin.newDrawable("white", 0.10f, 0.10f, 0.10f, 0.95f));
        scoreBox.pad(18);

        Label scoreTitle = new Label("Time Score", skin);
        scoreTitle.setFontScale(1.3f);
        scoreTitle.setColor(Color.LIGHT_GRAY);
        scoreTitle.setAlignment(Align.center);

        Label scoreValue = new Label(String.valueOf(dragScore), skin);
        scoreValue.setFontScale(3.0f);
        scoreValue.setColor(Color.WHITE);
        scoreValue.setAlignment(Align.center);

        Label bestLabel = new Label(
            newBestScore
                ? "NEW BEST TIME SCORE!"
                : "Best Time Score: " + bestScore,
            skin
        );
        bestLabel.setFontScale(1.3f);
        bestLabel.setColor(newBestScore ? Color.GOLD : Color.LIGHT_GRAY);
        bestLabel.setAlignment(Align.center);

        scoreBox.add(scoreTitle).center().row();
        scoreBox.add(scoreValue).center().padTop(5).row();
        scoreBox.add(bestLabel).center().padTop(8);

        panel.add(scoreBox).width(560).fillX().padBottom(15).row();

        // Race stats
        Table statsRow = new Table(skin);
        statsRow.defaults().pad(6);

        statsRow.add(createResultStatBox(
            "Your Time",
            playerTimeStr,
            playerWon ? Color.GREEN : Color.WHITE
        )).width(210).height(105);

        statsRow.add(createResultStatBox(
            "Opponent",
            aiTimeStr,
            playerWon ? Color.WHITE : Color.RED
        )).width(210).height(105);

        statsRow.add(createResultStatBox(
            "Gap",
            gapText,
            Color.CYAN
        )).width(210).height(105);

        statsRow.add(createResultStatBox(
            "Max Speed",
            String.format("%.0f mph", maxPlayerMph),
            Color.GOLD
        )).width(210).height(105);

        panel.add(statsRow).center().padBottom(12).row();

        // Car comparison
        Table carRow = new Table(skin);
        carRow.defaults().pad(6);

        carRow.add(createResultStatBox(
            "Your Car",
            playerStats.carClass + " PI " + playerStats.pi,
            Color.CYAN
        )).width(260).height(105);

        carRow.add(createResultStatBox(
            "Opponent",
            opponentStats.carClass + " PI " + opponentStats.pi,
            Color.CYAN
        )).width(260).height(105);

        panel.add(carRow).center().padBottom(15).row();

        // Cash section
        Table cashBox = new Table(skin);
        cashBox.setBackground(skin.newDrawable("white", 0.08f, 0.08f, 0.08f, 0.95f));
        cashBox.pad(16);
        cashBox.defaults().pad(4);

        Label cashEarnedLabel = new Label(
            playerWon
                ? "Cash Earned: $" + formatCash(reward)
                : "Cash Earned: $0",
            skin
        );
        cashEarnedLabel.setFontScale(1.45f);
        cashEarnedLabel.setColor(playerWon ? Color.GOLD : Color.LIGHT_GRAY);
        cashEarnedLabel.setAlignment(Align.center);

        Label totalCashLabel = new Label("Total Cash: $" + formatCash(CashManager.getCash()), skin);
        totalCashLabel.setFontScale(1.25f);
        totalCashLabel.setColor(Color.WHITE);
        totalCashLabel.setAlignment(Align.center);

        cashBox.add(cashEarnedLabel).center().row();
        cashBox.add(totalCashLabel).center().padTop(4);

        panel.add(cashBox).width(560).fillX().padBottom(22).row();

        // Buttons
        Table buttonRow = new Table();
        buttonRow.defaults().pad(8);

        TextButton retryButton = new TextButton("Retry", skin);
        TextButton modesButton = new TextButton("Back to Modes", skin);
        TextButton garageButton = new TextButton("Garage", skin);

        retryButton.getLabel().setFontScale(1.25f);
        modesButton.getLabel().setFontScale(1.25f);
        garageButton.getLabel().setFontScale(1.25f);

        retryButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new DragRaceScreen(game));
            }
        });

        modesButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new GameModeSelectorScreen(game));
            }
        });

        garageButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new GarageScreen(game));
            }
        });

        buttonRow.add(retryButton).width(210).height(65);
        buttonRow.add(modesButton).width(260).height(65);
        buttonRow.add(garageButton).width(210).height(65);

        panel.add(buttonRow).center().row();

        raceResultOverlay.add(panel).width(980).center();

        uiStage.addActor(raceResultOverlay);
        raceResultOverlay.toFront();
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
        if (cashBgTexture != null) cashBgTexture.dispose();
        if (raceTitleTexture != null) raceTitleTexture.dispose();
        if (youTitleTexture != null) youTitleTexture.dispose();
        if (opponentTitleTexture != null) opponentTitleTexture.dispose();
        uiStage.dispose();
        skin.dispose();
        if (playerCar != null) playerCar.dispose();
        if (aiCar != null) aiCar.dispose();
    }
}
