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
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import java.util.ArrayList;
import java.util.List;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

public class TimeTrialScreen implements Screen {

    private final Game game;

    // Rendering
    private SpriteBatch batch;
    private OrthographicCamera camera;

    // Player
    private Car playerCar;
    private List<Rectangle> borderRectangles;

    // Crash handling (time trial should NOT end on crash)
    private int crashCount = 0;
    private float crashCooldown = 0f;                 // prevents spam-crashes
    private float invulnTimer = 0f;                   // temporary invulnerability after crash
    private static final float CRASH_COOLDOWN_SECONDS = 0.8f;
    private static final float INVULN_SECONDS = 1.2f;
    private static final int CRASH_PENALTY_POINTS = 800;  // tweak

    // Road
    private Texture roadTexture;
    private Texture surroundingsTexture;
    private Texture scoreTitleTexture;
    private Texture distanceTitleTexture;
    private Texture speedTitleTexture;
    private Texture cashBgTexture;
    private static final float VIEW_WIDTH = 1920f;
    private static final float VIEW_HEIGHT = 1080f;
    private static final float SEGMENT_WIDTH = 1920f;
    private static final float SEGMENT_HEIGHT = 1080f;
    private float roadCenterX = VIEW_WIDTH / 2f;
    private float roadWidth = 800f;

    // --- Time Trial config ---
    private final float timeLimitSeconds;   // e.g. 60, 90, 120
    private float timeRemaining;            // counts down

    private boolean paused = false;
    private Table pauseOverlay;
    private Table pauseCard;

    private Table pauseSettingsOverlay;
    private Table pauseSettingsCard;

    // Traffic
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
        float weight;
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
    private int bonusPoints = 0;
    private float speedScoreAccumulator = 0f; // adds score over time based on speed
    private boolean gameOver = false;

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
    private float laneWidth;
    private float[] laneX;

    public TimeTrialScreen(Game game) {
        this(game, 60f); // default 60 seconds
    }

    public TimeTrialScreen(Game game, float timeLimitSeconds) {
        this.game = game;
        this.timeLimitSeconds = Math.max(5f, timeLimitSeconds);
        this.timeRemaining = this.timeLimitSeconds;
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

        // Road texture
        EnvironmentTheme theme = EnvironmentThemeManager.getCurrentTheme();

        roadTexture = new Texture(Gdx.files.internal(theme.roadTexturePath));
        surroundingsTexture = new Texture(Gdx.files.internal(theme.surroundingsTexturePath));
        roadWidth = theme.roadWidth;

        scoreTitleTexture = new Texture(Gdx.files.internal("Game_Label_Score.png"));
        distanceTitleTexture = new Texture(Gdx.files.internal("Game_Label_Distance.png"));
        speedTitleTexture = new Texture(Gdx.files.internal("Game_Label_Speed.png"));

        cashBgTexture = new Texture(Gdx.files.internal("Cash_Label.png"));

        System.out.println("Loaded theme:");
        System.out.println("Location = " + EnvironmentSelectionData.getSelectedLocation());
        System.out.println("Season = " + EnvironmentSelectionData.getSelectedSeason());
        System.out.println("Road texture = " + theme.roadTexturePath);
        System.out.println("Surroundings texture = " + theme.surroundingsTexturePath);

        // Traffic car types
        addTrafficType("BMW 330i - 2025.png", 600f, 600f, 5f);
        addTrafficType("Ford Fiesta ST - 2019.png", 600f, 600f, 4f);
        addTrafficType("Mazda MX-5 Miata - 2014.png", 600f, 600f, 3f);
        addTrafficType("Mclaren 650s - 2015.png", 600f, 600f, 1.5f);

        // Player car
        String selectedCarTexture = CarSelectionData.getSelectedCarTexture();
        CarStats stats = CarRegistry.getStats(selectedCarTexture);
        float startX = roadCenterX;
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

        this.startY = startYWorld;

        // Lanes (4 lanes)
        laneWidth = roadWidth / 4f;
        laneX = new float[]{
            roadCenterX - laneWidth * 1.5f,
            roadCenterX - laneWidth * 0.5f,
            roadCenterX + laneWidth * 0.5f,
            roadCenterX + laneWidth * 1.5f
        };

        // Borders
        borderRectangles = new ArrayList<>();
        float leftEdge = roadCenterX - roadWidth / 2f;
        float rightEdge = roadCenterX + roadWidth / 2f;
        borderRectangles.add(new Rectangle(leftEdge - 50f, -100000f, 50f, 200000f));
        borderRectangles.add(new Rectangle(rightEdge, -100000f, 50f, 200000f));

// --- UI layout ---


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
        cashTable.top().left().padTop(18).padLeft(230);

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

        timeLabel = new Label("Time Left: " + (int) timeRemaining + "s", skin);
        timeLabel.setFontScale(1.3f);
        timeLabel.setAlignment(Align.right);

        timeTable.add(timeLabel).right();
        uiStage.addActor(timeTable);


        Table bottomRightInfo = new Table();
        bottomRightInfo.setFillParent(true);
        bottomRightInfo.bottom().right().padRight(0).padBottom(100);

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

        // invulnerability blink
        boolean blink = invulnTimer > 0f && ((int) (invulnTimer * 10f) % 2 == 0);
        if (!blink) {
            playerCar.render(batch);
        }

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

    private void updateLogic(float delta) {
        // Decrement crash timers
        if (crashCooldown > 0f) crashCooldown -= delta;
        if (invulnTimer > 0f) invulnTimer -= delta;

        // Countdown
        timeRemaining -= delta;
        if (timeRemaining <= 0f) {
            timeRemaining = 0f;
            endRun(); // only end condition
            return;
        }

        // Player input
        boolean moveForward = Gdx.input.isKeyPressed(Input.Keys.W);
        boolean brake = Gdx.input.isKeyPressed(Input.Keys.S);
        boolean turnLeft = Gdx.input.isKeyPressed(Input.Keys.A);
        boolean turnRight = Gdx.input.isKeyPressed(Input.Keys.D);

        playerCar.update(delta, moveForward, brake, turnLeft, turnRight, borderRectangles);

        // Camera follow
        float camY = playerCar.getY() + 300f;
        camera.position.set(roadCenterX, camY, 0);

        // Time alive
        elapsedTime += delta;

        // Distance
        float dy = playerCar.getY() - startY;
        if (dy < 0) dy = 0;
        distanceScore = dy;

        int displayDistance = (int) (distanceScore / 10f);

        // Speed display + speed-based scoring
        float rawSpeed = playerCar.getSpeed();
        float mph = rawSpeed / 10f;
        int speedDisplay = (int) mph;
        speedLabel.setText(speedDisplay + " mph");

        speedScoreAccumulator += mph * 0.35f * delta;

        // Score formula
        int distancePoints = (int) (distanceScore / 10f);
        int speedPoints = (int) speedScoreAccumulator;
        score = distancePoints + speedPoints + bonusPoints;

        // UI update
        scoreLabel.setText(String.valueOf(score));
        distanceLabel.setText(displayDistance + " m");
        timeLabel.setText(String.format("Time Left: %.1fs", timeRemaining));
        cashLabel.setText("$" + formatCash(CashManager.getCash()));

        // Traffic spawn/update
        spawnTimer -= delta;
        if (spawnTimer <= 0f) {
            spawnTrafficCar();
            spawnTimer = spawnInterval;
        }

        Rectangle playerRect = playerCar.getBoundingRectangle();

        for (int i = trafficCars.size() - 1; i >= 0; i--) {
            TrafficCar t = trafficCars.get(i);
            t.update(delta);

            // Despawn behind
            if (t.y < playerCar.getY() - 2000f) {
                trafficCars.remove(i);
                continue;
            }

            // Crash = penalty, NOT game over
            if (playerRect.overlaps(t.bounds)) {
                onCrash(t);
            }

            // Near miss bonus
            if (!t.nearMissAwarded) {
                if (!playerRect.overlaps(t.bounds)) {
                    float playerCenterX = playerRect.x + playerRect.width / 2f;
                    float playerCenterY = playerRect.y + playerRect.height / 2f;
                    float carCenterX = t.bounds.x + t.bounds.width / 2f;
                    float carCenterY = t.bounds.y + t.bounds.height / 2f;

                    float dx = Math.abs(playerCenterX - carCenterX);
                    float dyCenter = Math.abs(playerCenterY - carCenterY);

                    boolean closeHorizontally = dx < playerRect.width * 1f;
                    boolean closeVertically = dyCenter < playerRect.height;

                    if (closeHorizontally && closeVertically) {
                        bonusPoints += 200;
                        t.nearMissAwarded = true;
                    }
                }
            }
        }
    }

    private void onCrash(TrafficCar hitCar) {
        runCrashCount++;

        if (invulnTimer > 0f || crashCooldown > 0f) return;

        crashCount++;

        runCrashCount++;

        // Penalty
        score -= CRASH_PENALTY_POINTS;
        if (score < 0) score = 0;

        // Push the traffic car away to prevent immediate re-collision spam
        if (hitCar != null) {
            hitCar.y = playerCar.getY() + 2500f;
            hitCar.bounds.setPosition(hitCar.x, hitCar.y);
        }

        // Reposition
        float resetX = roadCenterX;
        float resetY = Math.max(startY, playerCar.getY() - 200f);
        playerCar.setPosition(resetX, resetY);

        //  apply a reduced speed
        playerCar.setSpeed(playerCar.getSpeed() * 0.55f);

        crashCooldown = CRASH_COOLDOWN_SECONDS;
        invulnTimer = INVULN_SECONDS;
    }


    private boolean isLaneClearForSpawn(float laneXPos, float spawnY) {
        final float MIN_GAP_Y = 400f;
        for (TrafficCar t : trafficCars) {
            float dx = Math.abs(t.x - laneXPos);
            if (dx < laneWidth * 1f) {
                float dy = Math.abs(t.y - spawnY);
                if (dy < MIN_GAP_Y) return false;
            }
        }
        return true;
    }

    private void spawnTrafficCar() {
        float baseSpawnY = playerCar.getY() + 2000f;

        TrafficCarType type = pickRandomTrafficType();
        if (type == null) return;

        final int MAX_ATTEMPTS = 5;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int laneIdx = MathUtils.random(0, laneX.length - 1);
            float x = laneX[laneIdx];
            float spawnY = baseSpawnY;

            if (isLaneClearForSpawn(x, spawnY)) {
                float speed = MathUtils.random(type.minSpeed, type.maxSpeed);
                trafficCars.add(new TrafficCar(type.texture, x, spawnY, speed));
                return;
            }
        }
    }

    private TrafficCarType pickRandomTrafficType() {
        if (trafficTypes.isEmpty()) return null;

        float r = MathUtils.random() * totalTrafficWeight;
        float cumulative = 0f;

        for (TrafficCarType type : trafficTypes) {
            cumulative += type.weight;
            if (r <= cumulative) return type;
        }
        return trafficTypes.get(trafficTypes.size() - 1);
    }

    private int calculateCashReward() {
        int distMeters = (int) (distanceScore / 10f);

        int distanceCash = distMeters / 25;
        int scoreCash = score / 25;

        int total = distanceCash + scoreCash;
        if (total < 0) total = 0;
        return total;
    }

    /**
     * Ends the run ONLY when the timer runs out.
     */
    private void endRun() {
        if (gameOver) return;
        gameOver = true;

        HighScoreManager.submitScore("time_trial", score);
        int bestScore = HighScoreManager.getHighScore("time_trial");

        // Local best
        // Online leaderboard submit (if logged in)
        if (SupabaseAuth.isLoggedIn) {
            SupabaseGameData.submitScore("time_trial", score);
        }

        // Cash reward (server sync if logged in)
        int cashEarned = calculateCashReward();
        if (SupabaseAuth.isLoggedIn) {
            CashManager.addCashAndSync(cashEarned, "time_trial_reward");
        } else {
            // if i want to block offline rewards completely, remove this
            CashManager.addCash(cashEarned);
        }

        int distMeters = (int) (distanceScore / 10f);

        StringBuilder sb = new StringBuilder();
        sb.append("Time trial finished!\n")
            .append("Time Limit: ").append((int) timeLimitSeconds).append(" s\n")
            .append("Score: ").append(score).append("\n")
            .append("Distance: ").append(distMeters).append(" m\n")
            .append("Crashes: ").append(crashCount).append("\n")
            .append("Best Score: ").append(bestScore).append("\n\n")
            .append("Cash earned: $").append(cashEarned).append("\n")
            .append("Total cash: $").append(formatCash(CashManager.getCash()));


        Dialog dialog = new Dialog("Time's up!", skin) {
            @Override
            protected void result(Object obj) {
                String choice = (String) obj;
                if ("retry".equals(choice)) {
                    game.setScreen(new TimeTrialScreen(game, timeLimitSeconds));
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

    private String formatCash(int cash) {
        return String.format("%,d", cash);
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

    private void showPauseMenu() {
        if (pauseOverlay != null) pauseOverlay.setVisible(true);
        if (pauseSettingsOverlay != null) pauseSettingsOverlay.setVisible(false);
    }

    private void showPauseSettings() {
        if (pauseOverlay != null) pauseOverlay.setVisible(false);
        if (pauseSettingsOverlay != null) pauseSettingsOverlay.setVisible(true);
    }



    private String getFullscreenText() {
        return "Fullscreen: " + (Gdx.graphics.isFullscreen() ? "ON" : "OFF");
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
