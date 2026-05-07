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
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.ui.Window;

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

    private Texture defaultButtonUpTexture;
    private Texture defaultButtonDownTexture;
    private Texture defaultButtonOverTexture;
    private TextButton.TextButtonStyle defaultButtonStyle;

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
        defaultButtonStyle = createDefaultButtonStyle();

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

        TextButton pauseButton = createMenuButton("PAUSE");
        pauseButton.getLabel().setFontScale(0.9f);

        pauseTable.add(pauseButton).width(150f).height(42f);

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
        pauseOverlay.setBackground(skin.newDrawable("white", 0f, 0f, 0f, 0.65f));

        pauseCard = new Table();
        pauseCard.setBackground(createDarkPanelDrawable());
        pauseCard.pad(32);
        pauseCard.defaults().padBottom(12).width(300).height(48);

        Label titleLabel = new Label("Paused", skin);
        titleLabel.setFontScale(1.7f);
        titleLabel.setColor(Color.WHITE);
        titleLabel.setAlignment(Align.center);

        Label subtitleLabel = new Label("Free Ride is currently paused", skin);
        subtitleLabel.setFontScale(0.9f);
        subtitleLabel.setColor(Color.LIGHT_GRAY);
        subtitleLabel.setAlignment(Align.center);

        TextButton continueButton = createMenuButton("Continue");
        TextButton settingsButton = createMenuButton("Settings");
        TextButton restartButton = createMenuButton("Restart");
        TextButton quitButton = createMenuButton("Quit Run");

        pauseCard.add(titleLabel).width(300).center().padBottom(4).row();
        pauseCard.add(subtitleLabel).width(300).center().padBottom(18).row();

        pauseCard.add(continueButton).row();
        pauseCard.add(settingsButton).row();
        pauseCard.add(restartButton).row();
        pauseCard.add(quitButton).padBottom(0).row();

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
                showModernConfirmDialog(
                    "Quit Run",
                    "Quit this run and return to game modes?",
                    "Yes",
                    "No",
                    () -> game.setScreen(new GameModeSelectorScreen(game))
                );
            }
        });
    }

    private void createPauseSettingsOverlay() {
        pauseSettingsOverlay = new Table();
        pauseSettingsOverlay.setFillParent(true);
        pauseSettingsOverlay.setVisible(false);
        pauseSettingsOverlay.setBackground(skin.newDrawable("white", 0f, 0f, 0f, 0.65f));

        pauseSettingsCard = new Table();
        pauseSettingsCard.setBackground(createDarkPanelDrawable());
        pauseSettingsCard.pad(32);
        pauseSettingsCard.defaults().padBottom(12).width(330).height(48);

        Label titleLabel = new Label("Pause Settings", skin);
        titleLabel.setFontScale(1.55f);
        titleLabel.setColor(Color.WHITE);
        titleLabel.setAlignment(Align.center);

        Label subtitleLabel = new Label("Adjust options during this run", skin);
        subtitleLabel.setFontScale(0.9f);
        subtitleLabel.setColor(Color.LIGHT_GRAY);
        subtitleLabel.setAlignment(Align.center);

        TextButton fullscreenButton = createMenuButton(getFullscreenText());
        TextButton controlsButton = createMenuButton("Controls / Help");
        TextButton backButton = createMenuButton("Back");

        pauseSettingsCard.add(titleLabel).width(330).center().padBottom(4).row();
        pauseSettingsCard.add(subtitleLabel).width(330).center().padBottom(18).row();

        pauseSettingsCard.add(fullscreenButton).row();
        pauseSettingsCard.add(controlsButton).row();
        pauseSettingsCard.add(backButton).padBottom(0).row();

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

                GameSettings.setFullscreenEnabled(enableFullscreen);
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
        Dialog controlsDialog = new Dialog("Controls / Help", createModernDialogStyle());
        controlsDialog.getTitleLabel().setAlignment(Align.center);
        controlsDialog.getTitleLabel().setFontScale(1.15f);
        controlsDialog.getContentTable().pad(26);

        Table controlsPanel = new Table();
        controlsPanel.setBackground(createDarkInnerPanelDrawable());
        controlsPanel.pad(18);
        controlsPanel.defaults().padBottom(8).left();

        controlsPanel.add(createControlRow("W", "Accelerate")).width(500).row();
        controlsPanel.add(createControlRow("S", "Brake / Reverse")).width(500).row();
        controlsPanel.add(createControlRow("A", "Steer Left")).width(500).row();
        controlsPanel.add(createControlRow("D", "Steer Right")).width(500).row();
        controlsPanel.add(createControlRow("ESC", "Pause / Back")).width(500).row();
        controlsPanel.add(createControlRow("Mouse", "Click buttons and menus")).width(500).row();

        Label notesLabel = new Label(
            "Notes:\n" +
                "- In driving modes, ESC opens the pause menu.\n" +
                "- In pause settings, use Back to return to the pause menu.\n" +
                "- Drag Race uses W to accelerate and S to brake.",
            skin
        );
        notesLabel.setWrap(true);
        notesLabel.setAlignment(Align.left);
        notesLabel.setColor(Color.LIGHT_GRAY);

        Table notesPanel = new Table();
        notesPanel.setBackground(createDarkInnerPanelDrawable());
        notesPanel.pad(16);
        notesPanel.add(notesLabel).width(500);

        TextButton closeButton = createMenuButton("OK");
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                controlsDialog.hide();
            }
        });

        controlsDialog.getContentTable().add(controlsPanel).width(550).padBottom(16).row();
        controlsDialog.getContentTable().add(notesPanel).width(550).padBottom(18).row();
        controlsDialog.getContentTable().add(closeButton).width(150).height(40).row();

        controlsDialog.show(uiStage);

        float dialogWidth = Math.min(650f, uiStage.getWidth() - 80f);
        float dialogHeight = Math.min(620f, uiStage.getHeight() - 80f);

        controlsDialog.setSize(dialogWidth, dialogHeight);
        controlsDialog.setPosition(
            (uiStage.getWidth() - dialogWidth) / 2f,
            (uiStage.getHeight() - dialogHeight) / 2f
        );
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

        TextButton retryButton = createMenuButton("Retry");
        TextButton modesButton = createMenuButton("Back to Modes");
        TextButton garageButton = createMenuButton("Garage");

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

    private TextButton.TextButtonStyle createDefaultButtonStyle() {
        defaultButtonUpTexture = new Texture(Gdx.files.internal("Default_Button.png"));
        defaultButtonDownTexture = new Texture(Gdx.files.internal("Default_Button_Down.png"));
        defaultButtonOverTexture = new Texture(Gdx.files.internal("Default_Button_Over.png"));

        TextureRegionDrawable up = new TextureRegionDrawable(new TextureRegion(defaultButtonUpTexture));
        TextureRegionDrawable down = new TextureRegionDrawable(new TextureRegion(defaultButtonDownTexture));
        TextureRegionDrawable over = new TextureRegionDrawable(new TextureRegion(defaultButtonOverTexture));

        up.setMinWidth(0);
        up.setMinHeight(0);
        down.setMinWidth(0);
        down.setMinHeight(0);
        over.setMinWidth(0);
        over.setMinHeight(0);

        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.up = up;
        style.down = down;
        style.over = over;

        style.font = skin.getFont("default-font");
        style.fontColor = Color.WHITE;
        style.overFontColor = Color.WHITE;
        style.downFontColor = Color.LIGHT_GRAY;

        return style;
    }

    private TextButton createMenuButton(String text) {
        TextButton button = new TextButton(text, defaultButtonStyle);
        button.getLabel().setAlignment(Align.center);
        button.getLabel().setFontScale(1.0f);
        return button;
    }

    private Table createControlRow(String key, String action) {
        Table row = new Table();

        Label keyLabel = new Label(key, skin);
        keyLabel.setAlignment(Align.center);
        keyLabel.setColor(Color.WHITE);
        keyLabel.setFontScale(0.95f);

        Table keyBox = new Table();
        keyBox.setBackground(skin.newDrawable(
            "default-round",
            new Color(0.16f, 0.16f, 0.22f, 1f)
        ));
        keyBox.pad(6);
        keyBox.add(keyLabel).center();

        Label actionLabel = new Label(action, skin);
        actionLabel.setColor(Color.LIGHT_GRAY);
        actionLabel.setWrap(true);

        row.add(keyBox).width(120).height(34).padRight(14);
        row.add(actionLabel).width(340).left();

        return row;
    }

    private Window.WindowStyle createModernDialogStyle() {
        Window.WindowStyle style = new Window.WindowStyle(skin.get(Window.WindowStyle.class));

        style.background = skin.newDrawable(
            "default-round",
            new Color(0.035f, 0.035f, 0.045f, 0.98f)
        );

        style.titleFont = skin.getFont("default-font");
        style.titleFontColor = Color.WHITE;

        return style;
    }

    private Drawable createDarkPanelDrawable() {
        return skin.newDrawable(
            "default-round",
            new Color(0.035f, 0.035f, 0.045f, 0.97f)
        );
    }

    private Drawable createDarkInnerPanelDrawable() {
        return skin.newDrawable(
            "default-round",
            new Color(0.09f, 0.09f, 0.12f, 0.96f)
        );
    }

    private void showModernConfirmDialog(
        String title,
        String message,
        String confirmText,
        String cancelText,
        Runnable onConfirm
    ) {
        Dialog dialog = new Dialog(title, createModernDialogStyle());
        dialog.getTitleLabel().setAlignment(Align.center);
        dialog.getTitleLabel().setFontScale(1.15f);
        dialog.getContentTable().pad(26);

        Table messagePanel = new Table();
        messagePanel.setBackground(createDarkInnerPanelDrawable());
        messagePanel.pad(18);

        Label messageLabel = new Label(message, skin);
        messageLabel.setWrap(true);
        messageLabel.setAlignment(Align.center);
        messageLabel.setColor(Color.LIGHT_GRAY);

        messagePanel.add(messageLabel).width(430).center();

        TextButton confirmButton = createMenuButton(confirmText);
        TextButton cancelButton = createMenuButton(cancelText);

        confirmButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dialog.hide();
                if (onConfirm != null) {
                    onConfirm.run();
                }
            }
        });

        cancelButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dialog.hide();
            }
        });

        Table buttonRow = new Table();
        buttonRow.add(confirmButton).width(150).height(40).padRight(12);
        buttonRow.add(cancelButton).width(140).height(40);

        dialog.getContentTable().add(messagePanel).width(470).padBottom(20).row();
        dialog.getContentTable().add(buttonRow).row();

        dialog.show(uiStage);

        float dialogWidth = Math.min(560f, uiStage.getWidth() - 80f);
        float dialogHeight = Math.min(350f, uiStage.getHeight() - 80f);

        dialog.setSize(dialogWidth, dialogHeight);
        dialog.setPosition(
            (uiStage.getWidth() - dialogWidth) / 2f,
            (uiStage.getHeight() - dialogHeight) / 2f
        );
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

        if (defaultButtonUpTexture != null) defaultButtonUpTexture.dispose();
        if (defaultButtonDownTexture != null) defaultButtonDownTexture.dispose();
        if (defaultButtonOverTexture != null) defaultButtonOverTexture.dispose();

        uiStage.dispose();
        skin.dispose();
        if (playerCar != null) playerCar.dispose();
        if (aiCar != null) aiCar.dispose();
    }
}
