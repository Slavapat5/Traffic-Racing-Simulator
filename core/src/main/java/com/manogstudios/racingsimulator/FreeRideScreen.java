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
import java.util.ArrayList;
import java.util.List;
import com.badlogic.gdx.utils.Array;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.graphics.Color;


public class FreeRideScreen implements Screen {

    private final Game game;

    // Rendering
    private SpriteBatch batch;
    private OrthographicCamera camera;

    // Player
    private Car playerCar;
    private List<Rectangle> borderRectangles;

    // Road
    private Texture roadTexture;
    private Texture surroundingsTexture;
    private Texture scoreTitleTexture;
    private Texture distanceTitleTexture;
    private Texture speedTitleTexture;
    private Texture cashBgTexture;

    private AchievementToastManager achievementToasts;
    private float achievementCheckTimer = 0f;
    private static final float ACHIEVEMENT_CHECK_INTERVAL = 0.25f;

    private static final float VIEW_WIDTH = 1920f;
    private static final float VIEW_HEIGHT = 1080f;
    private static final float SEGMENT_WIDTH = 1920f;
    private static final float SEGMENT_HEIGHT = 1080f;
    private float roadCenterX = VIEW_WIDTH / 2f;
    private float roadWidth = 800f;
    private static final float ROAD_BOUNDARY_OFFSET_X = 40f;

    private boolean paused = false;
    private Table pauseOverlay;
    private Table pauseCard;

    private Table pauseSettingsOverlay;
    private Table pauseSettingsCard;

    private float furthestPlayerY = 0f;
    private static final float MAX_BACKWARD_DISTANCE = 120f;


    private static class TrafficCar {
        Texture texture;
        float x, y;
        float speed;
        Rectangle bounds;
        boolean nearMissAwarded = false;

        TrafficCar(Texture texture, float x, float y, float speed) {
            this.texture = texture;
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.bounds = new Rectangle(x, y, texture.getWidth(), texture.getHeight());
        }

        void update(float delta) {
            y += speed * delta;
            bounds.setPosition(x, y);
        }

        void render(SpriteBatch batch) {
            batch.draw(texture, x, y);
        }
    }

        private static class TrafficCarType {
            Texture texture;
            float minSpeed;
            float maxSpeed;
            float weight;   // spawn chance weight
        }

        private final List<TrafficCar> trafficCars = new ArrayList<>();
        private final List<TrafficCarType> trafficTypes = new ArrayList<>();
        private float totalTrafficWeight = 0f;
        private float spawnTimer = 0f;
        private float spawnInterval = 1.5f;



    // Scoring
    private float startY;
    private float distanceScore = 0f;
    private float elapsedTime = 0f;
    private int score = 0;
    private int bonusPoints = 0;   // near misses, etc.
    private boolean gameOver = false;

    // --- Anti-AFK ---
    private static final float AFK_GRACE_SECONDS = 8f;              // gives player time at start
    private static final float AFK_LOW_SPEED_LIMIT_MPH = 35f;       // below this counts as slow rolling
    private static final float AFK_MAX_LOW_ACTIVITY_SECONDS = 20f;  // allowed idle time

    private float lowActivityTimer = 0f;
    private boolean endedByAfk = false;

    // --- Telemetry (run session) ---
    private long runStartMillis = 0L;
    private int runCrashCount = 0;
    private int runNearMisses = 0;

    private float speedSumMphSeconds = 0f; // mph * seconds
    private float speedSampleSeconds = 0f;
    private float maxSpeedMph = 0f;


    // UI
    private Stage uiStage;
    private Skin skin;
    private Label cashLabel;
    private Label scoreLabel;
    private Label speedLabel;
    private Label distanceLabel;
    private Label timeLabel;

    private Table gameOverOverlay;

    private float laneWidth;
    private float[] laneX;


    public FreeRideScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, VIEW_WIDTH, VIEW_HEIGHT);

        // UI
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        uiStage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(uiStage);

        achievementToasts = new AchievementToastManager(uiStage, skin);

        // Road texture
        EnvironmentTheme theme = EnvironmentThemeManager.getCurrentTheme();

        roadTexture = new Texture(Gdx.files.internal(theme.roadTexturePath));
        surroundingsTexture = new Texture(Gdx.files.internal(theme.surroundingsTexturePath));
        roadWidth = theme.roadWidth;
        float boundaryRoadCenterX = roadCenterX + ROAD_BOUNDARY_OFFSET_X;

        scoreTitleTexture = new Texture(Gdx.files.internal("Game_Label_Score.png"));
        distanceTitleTexture = new Texture(Gdx.files.internal("Game_Label_Distance.png"));
        speedTitleTexture = new Texture(Gdx.files.internal("Game_Label_Speed.png"));

        cashBgTexture = new Texture(Gdx.files.internal("Cash_Label.png"));

        System.out.println("Loaded theme:");
        System.out.println("Location = " + EnvironmentSelectionData.getSelectedLocation());
        System.out.println("Season = " + EnvironmentSelectionData.getSelectedSeason());
        System.out.println("Road texture = " + theme.roadTexturePath);
        System.out.println("Surroundings texture = " + theme.surroundingsTexturePath);



        // Traffic car types (variety + spawn chances)
        addTrafficType("BMW 330i - 2025.png",600f, 600f, 5f);  // common sedan
        addTrafficType("Ford Fiesta ST - 2019.png",600f, 600f, 4f);  // common
        addTrafficType("Mazda MX-5 Miata - 2014.png",600f, 600f, 3f);  // a bit sporty
        addTrafficType("Mclaren 650s - 2015.png",600f, 600f, 1.5f);// rarer sports car


        // Player car
        String selectedCarTexture = CarSelectionData.getSelectedCarTexture();
        CarStats stats = CarRegistry.getStats(selectedCarTexture);
        float startX = roadCenterX;       // roughly center lane
        float startYWorld = 0f;

        float MIN_SPEED = 150f;
        playerCar = new Car(
            selectedCarTexture,
            startX,
            startYWorld,
            stats.acceleration,
            stats.speed,
            stats.handling,
            MIN_SPEED
        );

        // measures distance travelled
        this.startY = startYWorld;

        furthestPlayerY = playerCar.getY();

        // Simple lane setup: 4 lanes across the road
        laneWidth = roadWidth / 4f;

        // positions at: center - 1.5w, center - 0.5w, center + 0.5w, center + 1.5w
        laneX = new float[] {
            roadCenterX - laneWidth * 1.5f,
            roadCenterX - laneWidth * 0.5f,
            roadCenterX + laneWidth * 0.5f,
            roadCenterX + laneWidth * 1.5f
        };
        // Borders (so car can't leave road)
        borderRectangles = new ArrayList<>();
        float leftEdge = boundaryRoadCenterX - roadWidth / 2f;
        float rightEdge = boundaryRoadCenterX + roadWidth / 2f;

        // Tall vertical rectangles as invisible walls
        borderRectangles.add(new Rectangle(leftEdge - 50f, -100000f, 50f, 200000f));
        borderRectangles.add(new Rectangle(rightEdge, -100000f, 50f, 200000f));

        // --- UI ---


        Table scoreTable = new Table();
        scoreTable.setFillParent(true);
        scoreTable.top().left().padTop(10).padLeft(0);

        Image scoreTitleImage = new Image(scoreTitleTexture);

        scoreLabel = new Label("0", skin);
        scoreLabel.setFontScale(2.0f);
        scoreLabel.setAlignment(Align.left);

        scoreTable.add(scoreTitleImage).width(160).height(55).left().padBottom(4).row();
        scoreTable.add(scoreLabel).left().padLeft(18);

        uiStage.addActor(scoreTable);


        Table cashTable = new Table();
        cashTable.setFillParent(true);
        cashTable.top().left().padTop(18).padLeft(170);

        cashLabel = new Label("$" + formatCash(CashManager.getCash()), skin);
        cashLabel.setFontScale(1.25f);
        cashLabel.setAlignment(Align.center);

        Table cashBox = new Table();
        cashBox.setBackground(new TextureRegionDrawable(new TextureRegion(cashBgTexture)));
        cashBox.add(cashLabel).center().padLeft(22f).padRight(22f).padTop(8f).padBottom(8f);

        cashTable.add(cashBox).width(180f).height(38f).left();
        uiStage.addActor(cashTable);


        Table timeTable = new Table();
        timeTable.setFillParent(true);
        timeTable.top().right().padTop(18).padRight(130);

        timeLabel = new Label("Time: 0.0s", skin);
        timeLabel.setFontScale(1.2f);
        timeLabel.setAlignment(Align.right);

        timeTable.add(timeLabel).right();
        uiStage.addActor(timeTable);


        Table bottomRightInfo = new Table();
        bottomRightInfo.setFillParent(true);
        bottomRightInfo.bottom().right().padRight(0).padBottom(90);

        Image speedTitleImage = new Image(speedTitleTexture);
        speedLabel = new Label("0 mph", skin);
        speedLabel.setFontScale(2.0f);
        speedLabel.setAlignment(Align.right);

        Image distanceTitleImage = new Image(distanceTitleTexture);
        distanceLabel = new Label("0 m", skin);
        distanceLabel.setFontScale(1.7f);
        distanceLabel.setAlignment(Align.right);

        bottomRightInfo.add(speedTitleImage).width(160).height(55).right().padBottom(2).row();
        bottomRightInfo.add(speedLabel).right().padRight(22).padBottom(12).row();
        bottomRightInfo.add(distanceTitleImage).width(160).height(55).right().padBottom(2).row();
        bottomRightInfo.add(distanceLabel).right().padRight(22);

        uiStage.addActor(bottomRightInfo);


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

        runStartMillis = System.currentTimeMillis();
        runCrashCount = 0;
        runNearMisses = 0;
        speedSumMphSeconds = 0f;
        speedSampleSeconds = 0f;
        maxSpeedMph = 0f;


    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (!gameOver && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (paused && pauseSettingsOverlay != null && pauseSettingsOverlay.isVisible()) {
                showPauseMenu();
            } else {
                setPaused(!paused);
            }
        }

        if (!gameOver && !paused) {
            updateLogic(delta);
        }

        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        float baseY = (float) Math.floor(camera.position.y / SEGMENT_HEIGHT) * SEGMENT_HEIGHT;
        float roadX = roadCenterX - SEGMENT_WIDTH / 2f;

        for (int i = -1; i <= 1; i++) {
            float y = baseY + i * SEGMENT_HEIGHT;
            batch.draw(surroundingsTexture, 0, y, VIEW_WIDTH, SEGMENT_HEIGHT);
        }

        for (int i = -1; i <= 1; i++) {
            float y = baseY + i * SEGMENT_HEIGHT;
            batch.draw(roadTexture, roadX, y, SEGMENT_WIDTH, SEGMENT_HEIGHT);
        }

        for (TrafficCar t : trafficCars) {
            t.render(batch);
        }

        playerCar.render(batch);
        batch.end();

        uiStage.act(delta);
        uiStage.draw();
    }

    private void addTrafficType(String texturePath, float minSpeed, float maxSpeed, float weight) {
        Texture tex = new Texture(Gdx.files.internal(texturePath));
        TrafficCarType type = new TrafficCarType();
        type.texture = tex;
        type.minSpeed = minSpeed;
        type.maxSpeed = maxSpeed;
        type.weight = weight;
        trafficTypes.add(type);
        totalTrafficWeight += weight;
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
                game.setScreen(new FreeRideScreen(game));
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

    private void preventDrivingBackwards() {
        float currentY = playerCar.getY();

        if (currentY > furthestPlayerY) {
            furthestPlayerY = currentY;
        }

        float minimumAllowedY = furthestPlayerY - MAX_BACKWARD_DISTANCE;

        if (currentY < minimumAllowedY) {
            playerCar.setPosition(playerCar.getX(), minimumAllowedY);

            if (playerCar.getSpeed() < 0f) {
                playerCar.setSpeed(0f);
            }
        }
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

    private void checkLiveAchievements(int distMeters) {
        if (achievementToasts == null) return;

        Array<AchievementsManager.AchievementState> newlyUnlocked =
            AchievementsManager.onFreeRideLiveUpdate(
                score,
                distMeters,
                elapsedTime,
                runNearMisses,
                maxSpeedMph
            );

        achievementToasts.queueAll(newlyUnlocked);
    }

    private void showGameOverOverlay(int cashEarned,
                                     int distMeters,
                                     int bestScore,
                                     boolean newBestScore) {
        if (gameOverOverlay != null) {
            gameOverOverlay.remove();
        }

        gameOverOverlay = new Table();
        gameOverOverlay.setFillParent(true);

        // Full-screen dim background. No PNG needed.
        gameOverOverlay.setBackground(skin.newDrawable("white", 0f, 0f, 0f, 0.72f));

        Table panel = new Table(skin);
        panel.setBackground("default-round");
        panel.setColor(0.06f, 0.06f, 0.06f, 0.98f);
        panel.pad(35);
        panel.defaults().pad(8);

        Label titleLabel = new Label(
            endedByAfk ? "RUN ENDED" : "YOU CRASHED",
            skin
        );

        titleLabel.setFontScale(3.2f);
        titleLabel.setColor(endedByAfk ? Color.GOLD : Color.RED);
        titleLabel.setAlignment(Align.center);

        Label subtitleLabel = new Label(
            endedByAfk
                ? "Free Ride ended because no active driving was detected."
                : "Free Ride Run Summary",
            skin
        );
        subtitleLabel.setFontScale(1.25f);
        subtitleLabel.setColor(Color.LIGHT_GRAY);
        subtitleLabel.setAlignment(Align.center);

        panel.add(titleLabel).center().padBottom(4).row();
        panel.add(subtitleLabel).center().padBottom(18).row();

        // Score section
        Table scoreBox = new Table(skin);
        scoreBox.setBackground(skin.newDrawable("white", 0.10f, 0.10f, 0.10f, 0.95f));
        scoreBox.pad(18);

        Label scoreTitle = new Label("Score", skin);
        scoreTitle.setFontScale(1.3f);
        scoreTitle.setColor(Color.LIGHT_GRAY);
        scoreTitle.setAlignment(Align.center);

        Label scoreValue = new Label(String.valueOf(score), skin);
        scoreValue.setFontScale(3.0f);
        scoreValue.setColor(Color.WHITE);
        scoreValue.setAlignment(Align.center);

        Label bestLabel = new Label(
            newBestScore
                ? "NEW BEST SCORE!"
                : "Best Score: " + bestScore,
            skin
        );
        bestLabel.setFontScale(1.3f);
        bestLabel.setColor(newBestScore ? Color.GOLD : Color.LIGHT_GRAY);
        bestLabel.setAlignment(Align.center);

        scoreBox.add(scoreTitle).center().row();
        scoreBox.add(scoreValue).center().padTop(5).row();
        scoreBox.add(bestLabel).center().padTop(8);

        panel.add(scoreBox).width(560).fillX().padBottom(15).row();

        // Stats section
        Table statsRow = new Table(skin);
        statsRow.defaults().pad(6);

        statsRow.add(createResultStatBox(
            "Distance",
            distMeters + " m",
            Color.CYAN
        )).width(210).height(105);

        statsRow.add(createResultStatBox(
            "Time",
            String.format("%.1f s", elapsedTime),
            Color.CYAN
        )).width(210).height(105);

        statsRow.add(createResultStatBox(
            "Near Misses",
            String.valueOf(runNearMisses),
            Color.CYAN
        )).width(210).height(105);

        statsRow.add(createResultStatBox(
            "Max Speed",
            String.format("%.0f mph", maxSpeedMph),
            Color.GOLD
        )).width(210).height(105);

        panel.add(statsRow).center().padBottom(15).row();

        // Cash section
        Table cashBox = new Table(skin);
        cashBox.setBackground(skin.newDrawable("white", 0.08f, 0.08f, 0.08f, 0.95f));
        cashBox.pad(16);
        cashBox.defaults().pad(4);

        Label cashEarnedLabel = new Label("Cash Earned: $" + formatCash(cashEarned), skin);
        cashEarnedLabel.setFontScale(1.45f);
        cashEarnedLabel.setColor(Color.GOLD);
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
                game.setScreen(new FreeRideScreen(game));
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

        gameOverOverlay.add(panel).width(980).center();

        uiStage.addActor(gameOverOverlay);
        gameOverOverlay.toFront();
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

    private void updateLogic(float delta) {
        // Player input
        boolean moveForward = Gdx.input.isKeyPressed(Input.Keys.W);
        boolean brake = Gdx.input.isKeyPressed(Input.Keys.S);
        boolean turnLeft = Gdx.input.isKeyPressed(Input.Keys.A);
        boolean turnRight = Gdx.input.isKeyPressed(Input.Keys.D);

        playerCar.update(delta, moveForward, brake, turnLeft, turnRight, borderRectangles);

        preventDrivingBackwards();

        // follow the car in X and Y
        float camY = playerCar.getY() + 300f;   // keeps the car a bit lower on the screen
        camera.position.set(roadCenterX, camY, 0);


        // Time survived
        elapsedTime += delta;

        // Distance-based metric
        float dy = playerCar.getY() - startY;
        if (dy < 0) dy = 0;
        distanceScore = dy;

        // Convert raw distance to something like meters for display
        int displayDistance = (int) (distanceScore / 10f);

        // Simple scoring formula
        int distancePoints = (int) (distanceScore / 10f);   // 1 point per 10 units
        int timePoints = (int) (elapsedTime * 2f);          // 2 points per second
        score = distancePoints + timePoints + bonusPoints;

        // Update labels
        scoreLabel.setText(String.valueOf(score));
        distanceLabel.setText(displayDistance + " m");
        timeLabel.setText(String.format("Time: %.1fs", elapsedTime));

        float rawSpeed = playerCar.getSpeed();
        float mph = rawSpeed / 10f;

        if (mph > 0f) {
            speedSumMphSeconds += mph * delta;
            speedSampleSeconds += delta;
            if (mph > maxSpeedMph) maxSpeedMph = mph;
        }

        int speedDisplay = (int) mph;
        speedLabel.setText(speedDisplay + " mph");

        updateAntiAfk(delta, mph, moveForward, brake, turnLeft, turnRight);

        if (gameOver) {
            return;
        }


        // Update cash UI (in case they earn money from something later)
        cashLabel.setText("$" + formatCash(CashManager.getCash()));


        // Traffic spawning + update
        spawnTimer -= delta;
        if (spawnTimer <= 0f) {
            spawnTrafficCar();
            spawnTimer = spawnInterval;
        }

        Rectangle playerRect = playerCar.getBoundingRectangle();

        for (int i = trafficCars.size() - 1; i >= 0; i--) {
            TrafficCar t = trafficCars.get(i);
            t.update(delta);

            // Despawn cars far behind
            if (t.y < playerCar.getY() - 2000f) {
                trafficCars.remove(i);
                continue;
            }

            // --- COLLISION CHECK ---
            if (playerRect.overlaps(t.bounds)) {
                onCrash();
                break;
            }

            // --- NEAR MISS DETECTION ---
            if (!t.nearMissAwarded) {
                if (!playerRect.overlaps(t.bounds)) {

                    // Centers of player + traffic car
                    float playerCenterX = playerRect.x + playerRect.width / 2f;
                    float playerCenterY = playerRect.y + playerRect.height / 2f;
                    float carCenterX = t.bounds.x + t.bounds.width / 2f;
                    float carCenterY = t.bounds.y + t.bounds.height / 2f;

                    float dx = Math.abs(playerCenterX - carCenterX);
                    float dyCenter = Math.abs(playerCenterY - carCenterY);


                    boolean closeHorizontally = dx < playerRect.width * 1f;   // 1 car width apart
                    boolean closeVertically = dyCenter < playerRect.height;   // passed very close

                    if (closeHorizontally && closeVertically) {
                        bonusPoints += 200;          // award near-miss bonus
                        t.nearMissAwarded = true;   // don't award twice for this car
                        runNearMisses++;
                        System.out.println("Near miss! +200 points");
                    }
                }
            }
        }

        achievementCheckTimer -= delta;

        if (achievementCheckTimer <= 0f && !gameOver) {
            achievementCheckTimer = ACHIEVEMENT_CHECK_INTERVAL;
            checkLiveAchievements(displayDistance);
        }
    }

    private void updateAntiAfk(float delta,
                               float mph,
                               boolean moveForward,
                               boolean brake,
                               boolean turnLeft,
                               boolean turnRight) {

        if (gameOver) return;

        boolean pressingControls = moveForward || brake || turnLeft || turnRight;

        boolean pastGracePeriod = elapsedTime >= AFK_GRACE_SECONDS;
        boolean movingTooSlow = mph < AFK_LOW_SPEED_LIMIT_MPH;

        if (pastGracePeriod && movingTooSlow && !pressingControls) {
            lowActivityTimer += delta;
        } else {
            lowActivityTimer = 0f;
        }

        if (lowActivityTimer >= AFK_MAX_LOW_ACTIVITY_SECONDS) {
            onAfkTimeout();
        }
    }

    private boolean isLaneClearForSpawn(float laneXPos, float spawnY) {
        // Minimum vertical gap between cars in the same lane
        final float MIN_GAP_Y = 400f;  // higher = more distance

        for (TrafficCar t : trafficCars) {
            // Same lane? (roughly)
            float dx = Math.abs(t.x - laneXPos);
            if (dx < laneWidth * 1f) { // within a lane width = same lane
                float dy = Math.abs(t.y - spawnY);
                if (dy < MIN_GAP_Y) {
                    // Too close to an existing car in this lane
                    return false;
                }
            }
        }
        return true;
    }


    private void spawnTrafficCar() {
        // Spawn ahead of player
        float baseSpawnY = playerCar.getY() + 2000f;

        TrafficCarType type = pickRandomTrafficType();
        if (type == null) return;

        // Try a few times to find a free lane position
        final int MAX_ATTEMPTS = 5;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {

            int laneIdx = MathUtils.random(0, laneX.length - 1);
            float x = laneX[laneIdx];
            float spawnY = baseSpawnY;

            if (isLaneClearForSpawn(x, spawnY)) {
                // Random speed within that type's range
                float speed = MathUtils.random(type.minSpeed, type.maxSpeed);

                TrafficCar car = new TrafficCar(type.texture, x, spawnY, speed);
                trafficCars.add(car);
                return; // done
            }
            // else: try another lane/attempt
        }
        // If no suitable lane found after a few tries, just skip this spawn.
    }

    private int getSelectedCarBonusPerMile() {
        String selectedCarTexture = CarSelectionData.getSelectedCarTexture();

        if (selectedCarTexture == null || selectedCarTexture.isEmpty()) {
            return 100;
        }

        CarDataBase.load();

        CarData car = CarDataBase.getCarByImage(selectedCarTexture);

        if (car == null) {
            return 100;
        }

        // $20,000 car = around $100 per mile
        // $200,000 car = around $500 per mile
        float basePrice = 20000f;
        float targetPrice = 200000f;

        float progress = (car.price - basePrice) / (targetPrice - basePrice);

        int bonusPerMile = Math.round(100 + (progress * 400f));

        return MathUtils.clamp(bonusPerMile, 100, 800);
    }

    private int calculateSelectedCarDistanceBonus(int distMeters) {
        float miles = distMeters / 1609.34f;
        int bonusPerMile = getSelectedCarBonusPerMile();

        return Math.round(miles * bonusPerMile);
    }

    /** How much cash to award for this Free Ride run */
    private int calculateCashReward() {
        int distMeters = (int) (distanceScore / 10f);

        int distanceCash = distMeters / 20;
        int scoreCash = score / 15;
        int timeCash = (int) (elapsedTime / 5f);

        int carDistanceBonus = calculateSelectedCarDistanceBonus(distMeters);

        int total = distanceCash + scoreCash + timeCash + carDistanceBonus;

        if (total < 0) total = 0;

        System.out.println("Base distance cash: $" + distanceCash);
        System.out.println("Score cash: $" + scoreCash);
        System.out.println("Time cash: $" + timeCash);
        System.out.println("Selected car bonus: $" + carDistanceBonus);
        System.out.println("Total cash earned: $" + total);

        return total;
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

    private void finishRun(boolean countedAsCrash) {
        gameOver = true;

        if (countedAsCrash) {
            runCrashCount++;
        }

        Float avgSpeed = (speedSampleSeconds > 0f) ? (speedSumMphSeconds / speedSampleSeconds) : null;
        Float maxSpeed = (maxSpeedMph > 0f) ? maxSpeedMph : null;

        long endMillis = System.currentTimeMillis();

        int cashEarned = calculateCashReward();
        int distMeters = (int) (distanceScore / 10f);

        if (SupabaseAuth.isLoggedIn) {
            SupabaseGameData.submitRunTelemetry(
                "free_ride",
                runStartMillis,
                endMillis,
                elapsedTime,
                distMeters,
                score,
                runCrashCount,
                runNearMisses,
                avgSpeed,
                maxSpeed,
                CarSelectionData.getSelectedCarTexture(),
                "0.1.0",
                () -> System.out.println("Telemetry saved"),
                (err) -> System.out.println("Telemetry failed: " + err)
            );
        }

        // Local best score
        int previousBest = HighScoreManager.getHighScore("free_ride");
        boolean newBestScore = score > previousBest;

        HighScoreManager.submitScore("free_ride", score);
        int bestScore = HighScoreManager.getHighScore("free_ride");

        // Server leaderboard
        if (SupabaseAuth.isLoggedIn) {
            SupabaseGameData.submitScore("free_ride", score);
        }

        // Daily quests
        if (SupabaseAuth.isLoggedIn) {
            SupabaseGameData.updateDailyQuests(
                "free_ride",
                distMeters,
                score,
                elapsedTime,
                runCrashCount,
                runNearMisses,
                maxSpeedMph,
                false,
                result -> {
                    if (result.rewardCash > 0) {
                        System.out.println("Daily quest reward earned: $" + result.rewardCash);
                    }
                },
                err -> System.out.println("Daily quest update failed: " + err)
            );
        }

        // Achievements
        Array<AchievementsManager.AchievementState> newlyUnlocked =
            AchievementsManager.onFreeRideFinished(
                score,
                distMeters,
                elapsedTime,
                runNearMisses,
                maxSpeedMph
            );

        if (achievementToasts != null) {
            achievementToasts.queueAll(newlyUnlocked);
        }

        Runnable showGameOverSummary = () -> {
            cashLabel.setText("$" + formatCash(CashManager.getCash()));
            showGameOverOverlay(cashEarned, distMeters, bestScore, newBestScore);
        };

        if (SupabaseAuth.isLoggedIn) {
            CashManager.addCashAndSync(cashEarned, "free_ride_reward");
            Gdx.app.postRunnable(showGameOverSummary);
        } else {
            CashManager.addCash(cashEarned);
            showGameOverSummary.run();
        }
    }

    private void onCrash() {
        finishRun(true);
    }

    private void onAfkTimeout() {
        if (gameOver) return;

        endedByAfk = true;
        finishRun(false);
    }

    private TrafficCarType pickRandomTrafficType() {
        if (trafficTypes.isEmpty()) return null;

        float r = MathUtils.random() * totalTrafficWeight;
        float cumulative = 0f;

        for (TrafficCarType type : trafficTypes) {
            cumulative += type.weight;
            if (r <= cumulative) {
                return type;
            }
        }

        // Fallback
        return trafficTypes.get(trafficTypes.size() - 1);
    }



    private String formatCash(int cash) {
        return String.format("%,d", cash);
    }

    @Override
    public void resize(int width, int height) {
        uiStage.getViewport().update(width, height, true);
    }

    @Override public void pause() { }
    @Override public void resume() { }
    @Override public void hide() { }

    @Override
    public void dispose() {
        batch.dispose();
        if (roadTexture != null) roadTexture.dispose();
        if (surroundingsTexture != null) surroundingsTexture.dispose();
        if (scoreTitleTexture != null) scoreTitleTexture.dispose();
        if (distanceTitleTexture != null) distanceTitleTexture.dispose();
        if (speedTitleTexture != null) speedTitleTexture.dispose();
        if (cashBgTexture != null) cashBgTexture.dispose();
        uiStage.dispose();
        skin.dispose();
        playerCar.dispose();

        for (TrafficCarType type : trafficTypes) {
            type.texture.dispose();
        }

    }

}
