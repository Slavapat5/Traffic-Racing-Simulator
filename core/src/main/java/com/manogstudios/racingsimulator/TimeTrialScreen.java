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
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import java.util.ArrayList;
import java.util.List;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.graphics.Color;

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
    private static final int CRASH_PENALTY_POINTS = 800;

    // Road
    private Texture roadTexture;
    private Texture surroundingsTexture;
    private Texture scoreTitleTexture;
    private Texture distanceTitleTexture;
    private Texture speedTitleTexture;
    private Texture cashBgTexture;

    private Texture defaultButtonUpTexture;
    private Texture defaultButtonDownTexture;
    private Texture defaultButtonOverTexture;
    private TextButton.TextButtonStyle defaultButtonStyle;

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

    //  Time Trial config
    private final float timeLimitSeconds;
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

    // Telemetry (run session)
    private long runStartMillis = 0L;
    private int runCrashCount = 0;
    private int runNearMisses = 0;

    private float speedSumMphSeconds = 0f; // mph * seconds
    private float speedSampleSeconds = 0f;
    private float maxSpeedMph = 0f;

    private int penaltyPoints = 0;

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

    private Table gameOverOverlay;

    private float furthestPlayerY = 0f;
    private static final float MAX_BACKWARD_DISTANCE = 120f;

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
        defaultButtonStyle = createDefaultButtonStyle();

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

        // Traffic car types
        addTrafficType("2025 DMV 365e - Silver.png",600f, 600f, 5f);  // common sedan
        addTrafficType("2019 Nord Sesta SV - Light Red.png",600f, 600f, 4f);  // common
        addTrafficType("2014 Sazda FX5 Shiatto - Light Red.png",600f, 600f, 3f);  // a bit sporty
        addTrafficType("2015 Solaren 660z - Light Green.png",600f, 600f, 1.5f);// rarer sports car

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
        furthestPlayerY = playerCar.getY();

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

        float leftEdge = boundaryRoadCenterX - roadWidth / 2f;
        float rightEdge = boundaryRoadCenterX + roadWidth / 2f;

        borderRectangles.add(new Rectangle(leftEdge - 50f, -100000f, 50f, 200000f));
        borderRectangles.add(new Rectangle(rightEdge, -100000f, 50f, 200000f));

        // UI layout


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
        timeTable.top().right().padTop(55).padRight(190);

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

    private void checkLiveAchievements(int distMeters) {
        if (achievementToasts == null) return;

        Array<AchievementsManager.AchievementState> newlyUnlocked =
            AchievementsManager.onTimeTrialLiveUpdate(
                score,
                distMeters,
                runNearMisses,
                maxSpeedMph
            );

        achievementToasts.queueAll(newlyUnlocked);
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
        preventDrivingBackwards();

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

        if (mph > 0f) {
            speedSumMphSeconds += mph * delta;
            speedSampleSeconds += delta;

            if (mph > maxSpeedMph) {
                maxSpeedMph = mph;
            }
        }

        speedScoreAccumulator += mph * 0.35f * delta;

        // Score formula
        int distancePoints = (int) (distanceScore / 10f);
        int speedPoints = (int) speedScoreAccumulator;
        score = distancePoints + speedPoints + bonusPoints - penaltyPoints;
        if (score < 0) score = 0;

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
                        runNearMisses++;
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

    private void onCrash(TrafficCar hitCar) {
        if (invulnTimer > 0f || crashCooldown > 0f) return;

        crashCount++;
        runCrashCount++;

        // Penalty
        penaltyPoints += CRASH_PENALTY_POINTS;

        // Push the traffic car away to prevent immediate re-collision
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

    private int calculateCashReward() {
        int distMeters = (int) (distanceScore / 10f);

        int distanceCash = distMeters / 25;
        int scoreCash = score / 25;

        int carDistanceBonus = calculateSelectedCarDistanceBonus(distMeters);

        int total = distanceCash + scoreCash + carDistanceBonus;

        if (total < 0) total = 0;

        System.out.println("Base distance cash: $" + distanceCash);
        System.out.println("Score cash: $" + scoreCash);
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

    /**
     * Ends the run ONLY when the timer runs out.
     */
    private void endRun() {
        if (gameOver) return;
        gameOver = true;

        int cashEarned = calculateCashReward();
        int distMeters = (int) (distanceScore / 10f);

        Float avgSpeed = (speedSampleSeconds > 0f) ? (speedSumMphSeconds / speedSampleSeconds) : null;
        Float maxSpeed = (maxSpeedMph > 0f) ? maxSpeedMph : null;

        // Local best score
        int previousBest = HighScoreManager.getHighScore("time_trial");
        boolean newBestScore = score > previousBest;

        HighScoreManager.submitScore("time_trial", score);
        int bestScore = HighScoreManager.getHighScore("time_trial");

        // Server leaderboard
        if (SupabaseAuth.isLoggedIn) {
            SupabaseGameData.submitScore("time_trial", score);
        }

        // Daily quests
        if (SupabaseAuth.isLoggedIn) {
            SupabaseGameData.updateDailyQuests(
                "time_trial",
                distMeters,
                score,
                elapsedTime,
                runCrashCount,
                runNearMisses,
                maxSpeedMph,
                false,
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

        long endMillis = System.currentTimeMillis();

        if (SupabaseAuth.isLoggedIn) {
            SupabaseGameData.submitRunTelemetry(
                "time_trial",
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

        Array<AchievementsManager.AchievementState> newlyUnlocked =
            AchievementsManager.onTimeTrialFinished(
                score,
                distMeters,
                runNearMisses,
                crashCount,
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
            CashManager.addCashAndSync(cashEarned, "time_trial_reward");
            Gdx.app.postRunnable(showGameOverSummary);
        } else {
            CashManager.addCash(cashEarned);
            showGameOverSummary.run();
        }
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

        Label subtitleLabel = new Label("Time Trial is currently paused", skin);
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
                game.setScreen(new TimeTrialScreen(game, timeLimitSeconds));
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
        controlsPanel.add(createControlRow("S", "Brake")).width(500).row();
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

    private void showPauseMenu() {
        if (pauseOverlay != null) pauseOverlay.setVisible(true);
        if (pauseSettingsOverlay != null) pauseSettingsOverlay.setVisible(false);
    }

    private void showPauseSettings() {
        if (pauseOverlay != null) pauseOverlay.setVisible(false);
        if (pauseSettingsOverlay != null) pauseSettingsOverlay.setVisible(true);
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

        // Full-screen dim background
        gameOverOverlay.setBackground(skin.newDrawable("white", 0f, 0f, 0f, 0.72f));

        Table panel = new Table(skin);
        panel.setBackground("default-round");
        panel.setColor(0.06f, 0.06f, 0.06f, 0.98f);
        panel.pad(35);
        panel.defaults().pad(8);

        Label titleLabel = new Label("TIME'S UP", skin);
        titleLabel.setFontScale(3.2f);
        titleLabel.setColor(Color.GOLD);
        titleLabel.setAlignment(Align.center);

        Label subtitleLabel = new Label("Time Trial Run Summary", skin);
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
            "Time Limit",
            (int) timeLimitSeconds + " s",
            Color.CYAN
        )).width(210).height(105);

        statsRow.add(createResultStatBox(
            "Crashes",
            String.valueOf(crashCount),
            crashCount == 0 ? Color.GOLD : Color.CYAN
        )).width(210).height(105);

        statsRow.add(createResultStatBox(
            "Near Misses",
            String.valueOf(runNearMisses),
            Color.CYAN
        )).width(210).height(105);

        panel.add(statsRow).center().padBottom(12).row();

        Table statsRow2 = new Table(skin);
        statsRow2.defaults().pad(6);

        statsRow2.add(createResultStatBox(
            "Max Speed",
            String.format("%.0f mph", maxSpeedMph),
            Color.GOLD
        )).width(260).height(105);

        statsRow2.add(createResultStatBox(
            "Penalty",
            "-" + penaltyPoints,
            crashCount > 0 ? Color.RED : Color.LIGHT_GRAY
        )).width(260).height(105);

        panel.add(statsRow2).center().padBottom(15).row();

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
                game.setScreen(new TimeTrialScreen(game, timeLimitSeconds));
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
        if (defaultButtonUpTexture != null) defaultButtonUpTexture.dispose();
        if (defaultButtonDownTexture != null) defaultButtonDownTexture.dispose();
        if (defaultButtonOverTexture != null) defaultButtonOverTexture.dispose();

        uiStage.dispose();
        skin.dispose();
        playerCar.dispose();

        for (TrafficCarType type : trafficTypes) {
            type.texture.dispose();
        }
    }
}
