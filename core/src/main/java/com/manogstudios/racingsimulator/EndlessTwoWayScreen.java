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

public class EndlessTwoWayScreen implements Screen {

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
    private static final float VIEW_WIDTH = 1920f;
    private static final float VIEW_HEIGHT = 1080f;
    private static final float SEGMENT_WIDTH = 1920f;
    private static final float SEGMENT_HEIGHT = 1080f;
    private float roadCenterX = VIEW_WIDTH / 2f;
    private float roadWidth = 800f;

    // --- Multiplier (Endless) ---
    private static final float SPEED_THRESHOLD_MPH = 90f;

    private float multiplierMeter = 0f; // 0..1
    private float scoreMultiplier = 1.0f;

    private static final float METER_GAIN_PER_SEC = 0.18f;
    private static final float METER_DRAIN_PER_SEC = 0.28f;
    private static final float MULTIPLIER_MAX = 3.0f;
    private static final float MULTIPLIER_STEP = 0.2f;

    private Label multLabel;

    // --- Two-way config ---
    // Lanes 0 and 1 are "opposing traffic" (coming toward the player).
    private static final int[] OPPOSING_LANES = {0, 1};

    // Bonus for driving in opposing lanes (risk reward)
    private static final float OPPOSING_MIN_MPH = 70f;            // must be at least this fast to earn bonus
    private static final float OPPOSING_POINTS_PER_SEC = 75f;     // tweak
    private float opposingScoreAccumulator = 0f;
    private Label opposingLabel;

    // Opposing lanes: tighter gaps and faster cars
    private static final float MIN_GAP_Y_NORMAL = 400f;
    private static final float MIN_GAP_Y_OPPOSING_START = 320f;
    private static final float MIN_GAP_Y_OPPOSING_MIN = 230f;
    private float opposingLaneGapCurrent = MIN_GAP_Y_OPPOSING_START;

    private static final float OPPOSING_SPEED_BOOST_MIN = 10f;
    private static final float OPPOSING_SPEED_BOOST_MAX = 50f;
    private float opposingBoostExtra = 0f;

    //  bias spawns toward opposing lanes as heat increases
    private float opposingTrafficBias = 0.20f;             // 20% initial bias
    private static final float OPPOSING_BIAS_MAX = 0.55f;

    // --- Difficulty Ramp (Heat) ---
    private static final float HEAT_STEP_SECONDS = 20f;
    private float heatTimer = 0f;

    private static final float SPAWN_INTERVAL_START = 1.5f;
    private static final float SPAWN_INTERVAL_MIN = 0.85f;
    private static final float SPAWN_INTERVAL_MULT = 0.95f;

    private static final float SPEED_BOOST_MIN_PER_STEP = 10f;
    private static final float SPEED_BOOST_MAX_PER_STEP = 18f;

    private float clusterChance = 0.06f;
    private static final float CLUSTER_CHANCE_MAX = 0.40f;
    private static final float CLUSTER_CHANCE_ADD = 0.04f;

    private static final float OPPOSING_GAP_REDUCE_PER_STEP = 10f;
    private static final float OPPOSING_BOOST_GROWTH_PER_STEP = 12f;

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

    // Traffic
    private static class TrafficCar {
        Texture texture;
        float x, y;
        float speed;           // world units per second (can be + or -)
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
            // If speed is negative, the car is moving toward the player.
            boolean flipY = speed < 0f;

            batch.draw(
                texture,
                x, y,
                texture.getWidth() / 2f, texture.getHeight() / 2f,
                texture.getWidth(), texture.getHeight(),
                1f, 1f,
                0f,
                0, 0,
                texture.getWidth(), texture.getHeight(),
                false, flipY
            );
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
    private float spawnInterval = SPAWN_INTERVAL_START;

    // Scoring
    private float startY;
    private float distanceScore = 0f;
    private float elapsedTime = 0f;
    private int score = 0;
    private int bonusPoints = 0;
    private boolean gameOver = false;

    // --- Telemetry (run session) ---
    private long runStartMillis = 0L;
    private int runCrashCount = 0;
    private int runNearMisses = 0;

    private float speedSumMphSeconds = 0f; // mph * seconds
    private float speedSampleSeconds = 0f;
    private float maxSpeedMph = 0f;


    public EndlessTwoWayScreen(Game game) {
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

        EnvironmentTheme theme = EnvironmentThemeManager.getCurrentTheme();

        roadTexture = new Texture(Gdx.files.internal(theme.roadTexturePath));
        surroundingsTexture = new Texture(Gdx.files.internal(theme.surroundingsTexturePath));
        roadWidth = theme.roadWidth;

        System.out.println("Loaded theme:");
        System.out.println("Location = " + EnvironmentSelectionData.getSelectedLocation());
        System.out.println("Season = " + EnvironmentSelectionData.getSelectedSeason());
        System.out.println("Road texture = " + theme.roadTexturePath);
        System.out.println("Surroundings texture = " + theme.surroundingsTexturePath);

        // Traffic types
        addTrafficType("BMW 330i - 2025.png", 600f, 600f, 5f);
        addTrafficType("Ford Fiesta ST - 2019.png", 600f, 600f, 4f);
        addTrafficType("Mazda MX-5 Miata - 2014.png", 600f, 600f, 3f);
        addTrafficType("Mclaren 650s - 2015.png", 600f, 600f, 1.5f);

        // Player
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

        runStartMillis = System.currentTimeMillis();
        runCrashCount = 0;
        runNearMisses = 0;

        // Lanes
        laneWidth = roadWidth / 4f;
        laneX = new float[]{
            roadCenterX - laneWidth * 1.5f,  // lane 0 (opposing)
            roadCenterX - laneWidth * 0.5f,  // lane 1 (opposing)
            roadCenterX + laneWidth * 0.5f,  // lane 2 (normal)
            roadCenterX + laneWidth * 1.5f   // lane 3 (normal)
        };

        opposingLaneGapCurrent = MIN_GAP_Y_OPPOSING_START;

        // Borders
        borderRectangles = new ArrayList<>();
        float leftEdge = roadCenterX - roadWidth / 2f;
        float rightEdge = roadCenterX + roadWidth / 2f;
        borderRectangles.add(new Rectangle(leftEdge - 50f, -100000f, 50f, 200000f));
        borderRectangles.add(new Rectangle(rightEdge, -100000f, 50f, 200000f));

        // --- UI ---
        Table root = new Table();
        root.setFillParent(true);
        uiStage.addActor(root);

        Table topBar = new Table();
        topBar.top().left().pad(10);
        topBar.setFillParent(true);

        multLabel = new Label("x1.0", skin);
        multLabel.setFontScale(1.2f);
        multLabel.setAlignment(Align.left);
        topBar.add(multLabel).left().padRight(14f);

        opposingLabel = new Label("Opposing: +0", skin);
        opposingLabel.setFontScale(1.0f);
        opposingLabel.setAlignment(Align.left);
        topBar.add(opposingLabel).left().padRight(20f);

        cashLabel = new Label("$" + formatCash(CashManager.getCash()), skin);
        cashLabel.setFontScale(1.2f);
        cashLabel.setAlignment(Align.left);
        topBar.add(cashLabel).left().padRight(20f);

        scoreLabel = new Label("Score: 0", skin);
        scoreLabel.setFontScale(1.2f);
        scoreLabel.setAlignment(Align.left);
        topBar.add(scoreLabel).left().expandX();

        distanceLabel = new Label("Dist: 0 m", skin);
        distanceLabel.setFontScale(1.2f);
        distanceLabel.setAlignment(Align.left);
        topBar.add(distanceLabel).left().padRight(20f);

        timeLabel = new Label("Time: 0.0s", skin);
        timeLabel.setFontScale(1.2f);
        timeLabel.setAlignment(Align.left);
        topBar.add(timeLabel).left().padRight(20f);

        speedLabel = new Label("Speed: 0 mph", skin);
        speedLabel.setFontScale(1.2f);
        speedLabel.setAlignment(Align.left);
        topBar.add(speedLabel).left().expandX();

        TextButton menuButton = new TextButton("Menu", skin);
        topBar.add(menuButton).right().width(80f);

        uiStage.addActor(topBar);

        final Table menuTable = new Table(skin);
        menuTable.setVisible(false);
        menuTable.defaults().pad(5).fillX().uniformX();
        menuTable.background("default-round");

        TextButton switchCarBtn = new TextButton("Switch Car", skin);
        TextButton homeBtn = new TextButton("Home", skin);
        TextButton quitBtn = new TextButton("Quit", skin);

        menuTable.add(switchCarBtn).row();
        menuTable.add(homeBtn).row();
        menuTable.add(quitBtn).row();

        Table menuContainer = new Table();
        menuContainer.setFillParent(true);
        menuContainer.top().right().pad(10, 10, 0, 10);
        menuContainer.add(menuTable);
        uiStage.addActor(menuContainer);

        menuButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                menuTable.setVisible(!menuTable.isVisible());
            }
        });

        switchCarBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new GarageScreen(game));
            }
        });

        homeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });

        quitBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new PlayScreen(game));
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

        if (!gameOver) {
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

    private void updateMultiplier(float delta, float mph) {
        boolean above = mph >= SPEED_THRESHOLD_MPH;

        if (above) multiplierMeter += METER_GAIN_PER_SEC * delta;
        else multiplierMeter -= METER_DRAIN_PER_SEC * delta;

        multiplierMeter = MathUtils.clamp(multiplierMeter, 0f, 1f);

        float raw = 1.0f + (MULTIPLIER_MAX - 1.0f) * multiplierMeter;
        float stepped = (float) (Math.floor(raw / MULTIPLIER_STEP) * MULTIPLIER_STEP);
        scoreMultiplier = MathUtils.clamp(stepped, 1.0f, MULTIPLIER_MAX);
    }

    private void updateHeat(float delta) {
        heatTimer += delta;

        if (heatTimer < HEAT_STEP_SECONDS) return;
        heatTimer -= HEAT_STEP_SECONDS;

        spawnInterval *= SPAWN_INTERVAL_MULT;
        if (spawnInterval < SPAWN_INTERVAL_MIN) spawnInterval = SPAWN_INTERVAL_MIN;

        for (TrafficCarType type : trafficTypes) {
            type.minSpeed += SPEED_BOOST_MIN_PER_STEP;
            type.maxSpeed += SPEED_BOOST_MAX_PER_STEP;
            if (type.maxSpeed < type.minSpeed) type.maxSpeed = type.minSpeed;
        }

        clusterChance = Math.min(CLUSTER_CHANCE_MAX, clusterChance + CLUSTER_CHANCE_ADD);

        opposingTrafficBias = Math.min(OPPOSING_BIAS_MAX, opposingTrafficBias + 0.03f);
        opposingLaneGapCurrent = Math.max(MIN_GAP_Y_OPPOSING_MIN, opposingLaneGapCurrent - OPPOSING_GAP_REDUCE_PER_STEP);
        opposingBoostExtra += OPPOSING_BOOST_GROWTH_PER_STEP;
    }

    private boolean isOpposingLane(int laneIdx) {
        return (laneIdx == 0 || laneIdx == 1);
    }

    private int getNearestLaneIndex(float xPos) {
        int best = 0;
        float bestDist = Math.abs(xPos - laneX[0]);

        for (int i = 1; i < laneX.length; i++) {
            float d = Math.abs(xPos - laneX[i]);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private int pickSpawnLaneIndex() {
        // Bias toward opposing lanes as heat increases (and as clusters get more common)
        float bias = Math.min(OPPOSING_BIAS_MAX, opposingTrafficBias + clusterChance * 0.6f);

        if (MathUtils.random() < bias) {
            return MathUtils.randomBoolean(0.55f) ? 0 : 1;
        }

        return MathUtils.random(0, laneX.length - 1);
    }

    private boolean isLaneClearForSpawn(int laneIdx, float laneXPos, float spawnY) {
        float minGap = isOpposingLane(laneIdx) ? opposingLaneGapCurrent : MIN_GAP_Y_NORMAL;

        for (TrafficCar t : trafficCars) {
            float dx = Math.abs(t.x - laneXPos);
            if (dx < laneWidth * 1f) {
                float dy = Math.abs(t.y - spawnY);
                if (dy < minGap) return false;
            }
        }
        return true;
    }

    private void spawnTrafficBurst() {
        int count = 1;

        if (MathUtils.random() < clusterChance) {
            count = (MathUtils.randomBoolean(0.7f)) ? 2 : 3;
        }

        for (int i = 0; i < count; i++) {
            float extraOffset = i * MathUtils.random(120f, 260f);
            spawnTrafficCar(extraOffset);
        }
    }

    private void spawnTrafficCar(float extraY) {
        // Normal lanes spawn ahead of player (above camera), moving forward (+speed)
        // Opposing lanes also spawn ahead, but move toward the player (-speed).
        float baseSpawnY = playerCar.getY() + 2200f + extraY;

        TrafficCarType type = pickRandomTrafficType();
        if (type == null) return;

        final int MAX_ATTEMPTS = 7;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int laneIdx = pickSpawnLaneIndex();
            float x = laneX[laneIdx];
            float spawnY = baseSpawnY;

            if (isLaneClearForSpawn(laneIdx, x, spawnY)) {
                float speed = MathUtils.random(type.minSpeed, type.maxSpeed);

                if (isOpposingLane(laneIdx)) {
                    // Opposing traffic: move downward toward the player
                    float boost = MathUtils.random(OPPOSING_SPEED_BOOST_MIN, OPPOSING_SPEED_BOOST_MAX) + opposingBoostExtra;
                    speed += boost;
                    speed = -speed; // reverse direction
                }

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

    private void updateLogic(float delta) {
        updateHeat(delta);

        boolean moveForward = Gdx.input.isKeyPressed(Input.Keys.W);
        boolean brake = Gdx.input.isKeyPressed(Input.Keys.S);
        boolean turnLeft = Gdx.input.isKeyPressed(Input.Keys.A);
        boolean turnRight = Gdx.input.isKeyPressed(Input.Keys.D);

        playerCar.update(delta, moveForward, brake, turnLeft, turnRight, borderRectangles);

        float camY = playerCar.getY() + 300f;
        camera.position.set(roadCenterX, camY, 0);

        elapsedTime += delta;

        float dy = playerCar.getY() - startY;
        if (dy < 0) dy = 0;
        distanceScore = dy;

        int displayDistance = (int) (distanceScore / 10f);

        float rawSpeed = playerCar.getSpeed();
        float mph = rawSpeed / 10f;

        if (mph > 0f) {
            speedSumMphSeconds += mph * delta;
            speedSampleSeconds += delta;
            if (mph > maxSpeedMph) maxSpeedMph = mph;
        }


        int speedDisplay = (int) mph;
        speedLabel.setText("Speed: " + speedDisplay + " mph");

        updateMultiplier(delta, mph);
        multLabel.setText(String.format("x%.1f", scoreMultiplier));

        // --- Opposing lane bonus (risk reward) ---
        int playerLane = getNearestLaneIndex(playerCar.getX());
        boolean onOpposing = isOpposingLane(playerLane);
        boolean opposingQualified = onOpposing && (mph >= OPPOSING_MIN_MPH);

        if (opposingQualified) {
            opposingScoreAccumulator += OPPOSING_POINTS_PER_SEC * delta;
        }

        opposingLabel.setText("Opposing: +" + (int) opposingScoreAccumulator);

        int distancePoints = (int) (distanceScore / 10f);
        int timePoints = (int) (elapsedTime * 2f);
        int opposingPoints = (int) opposingScoreAccumulator;

        int rawScore = distancePoints + timePoints + bonusPoints + opposingPoints;
        score = (int) (rawScore * scoreMultiplier);

        scoreLabel.setText("Score: " + score);
        distanceLabel.setText("Dist: " + displayDistance + " m");
        timeLabel.setText(String.format("Time: %.1fs", elapsedTime));
        cashLabel.setText("$" + formatCash(CashManager.getCash()));

        // Spawn
        spawnTimer -= delta;
        if (spawnTimer <= 0f) {
            spawnTrafficBurst();
            spawnTimer = spawnInterval;
        }

        // Update traffic & collisions
        Rectangle playerRect = playerCar.getBoundingRectangle();

        for (int i = trafficCars.size() - 1; i >= 0; i--) {
            TrafficCar t = trafficCars.get(i);
            t.update(delta);

            // Despawn rules differ by direction:
            // If moving forward (+), despawn far behind.
            // If opposing (-), despawn far ahead  OR far below player.
            if (t.speed >= 0f) {
                if (t.y < playerCar.getY() - 2000f) {
                    trafficCars.remove(i);
                    continue;
                }
            } else {
                if (t.y < playerCar.getY() - 2000f) {
                    trafficCars.remove(i);
                    continue;
                }
                if (t.y > playerCar.getY() + 4000f) {
                    trafficCars.remove(i);
                    continue;
                }
            }

            if (playerRect.overlaps(t.bounds)) {
                onCrash();
                break;
            }

            // Near miss
            if (!t.nearMissAwarded && !playerRect.overlaps(t.bounds)) {
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
                    runNearMisses++; // telemetry
                }
            }
        }
    }

    private int calculateCashReward() {
        int distMeters = (int) (distanceScore / 10f);

        int distanceCash = distMeters / 20;
        int scoreCash = score / 15;
        int timeCash = (int) (elapsedTime / 5f);

        int total = distanceCash + scoreCash + timeCash;
        if (total < 0) total = 0;

        return total;
    }

    private void onCrash() {
        gameOver = true;
        runCrashCount++;

        // Telemetry
        runCrashCount++;
        long endMillis = System.currentTimeMillis();

        if (SupabaseAuth.isLoggedIn) {
            SupabaseGameData.submitRunTelemetry(
                "endless_two_way",
                runStartMillis,
                endMillis,
                elapsedTime,
                (int) (distanceScore / 10f),
                score,
                runCrashCount,
                runNearMisses,
                null,
                null,
                CarSelectionData.getSelectedCarTexture(),
                "0.1.0",
                () -> System.out.println("Telemetry saved"),
                (err) -> System.out.println("Telemetry failed: " + err)
            );
        }

        // Reset meters
        multiplierMeter = 0f;
        scoreMultiplier = 1.0f;
        opposingScoreAccumulator = 0f;

        // Leaderboard (server)
        if (SupabaseAuth.isLoggedIn) {
            SupabaseGameData.submitScore("endless_two_way", score);
        }

        // Cash
        int cashEarned = calculateCashReward();
        int distMeters = (int) (distanceScore / 10f);

        Runnable showCrashDialog = () -> {
            StringBuilder sb = new StringBuilder();
            sb.append("You crashed!\n")
                .append("Score: ").append(score).append("\n")
                .append("Distance: ").append(distMeters).append(" m\n")
                .append("Time: ").append(String.format("%.1f s", elapsedTime)).append("\n\n")
                .append("Cash earned: $").append(cashEarned).append("\n")
                .append("Total cash: $").append(formatCash(CashManager.getCash()));

            Dialog dialog = new Dialog("Crash!", skin) {
                @Override
                protected void result(Object obj) {
                    String choice = (String) obj;
                    if ("retry".equals(choice)) {
                        game.setScreen(new EndlessTwoWayScreen(game));
                    } else if ("modes".equals(choice)) {
                        game.setScreen(new GameModeSelectorScreen(game));
                    }
                }
            };

            dialog.text(sb.toString());
            dialog.button("Retry", "retry");
            dialog.button("Back to Modes", "modes");
            dialog.show(uiStage);
        };

        if (SupabaseAuth.isLoggedIn) {
            CashManager.addCashAndSync(cashEarned, "endless_two_way_reward");
            Gdx.app.postRunnable(showCrashDialog);
        } else {
            showCrashDialog.run();
        }
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
        uiStage.dispose();
        skin.dispose();
        playerCar.dispose();

        for (TrafficCarType type : trafficTypes) {
            type.texture.dispose();
        }
    }
}
