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
    private static final float VIEW_WIDTH = 1920f;
    private static final float VIEW_HEIGHT = 1080f;
    private static final float SEGMENT_WIDTH = 1920f;
    private static final float SEGMENT_HEIGHT = 1080f;
    private float roadCenterX = VIEW_WIDTH / 2f;
    private float roadWidth = 800f;




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

        // Road texture
        EnvironmentTheme theme = EnvironmentThemeManager.getCurrentTheme();

        roadTexture = new Texture(Gdx.files.internal(theme.roadTexturePath));
        surroundingsTexture = new Texture(Gdx.files.internal(theme.surroundingsTexturePath));
        roadWidth = theme.roadWidth;

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
        float startX = roadCenterX;   // roughly center lane
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



// Simple lane setup: 4 lanes across the road
        laneWidth = roadWidth / 4f;

        // positions at: center - 1.5w, center - 0.5w, center + 0.5w, center + 1.5w
        laneX = new float[] {
            roadCenterX - laneWidth * 1.5f,  // lane 0 (far left)
            roadCenterX - laneWidth * 0.5f,  // lane 1
            roadCenterX + laneWidth * 0.5f,  // lane 2
            roadCenterX + laneWidth * 1.5f   // lane 3 (far right)
        };

        // Borders (so car can't leave road)
        borderRectangles = new ArrayList<>();
        float leftEdge = roadCenterX - roadWidth / 2f;
        float rightEdge = roadCenterX + roadWidth / 2f;

        // Tall vertical rectangles as invisible walls
        borderRectangles.add(new Rectangle(leftEdge - 50f, -100000f, 50f, 200000f));
        borderRectangles.add(new Rectangle(rightEdge, -100000f, 50f, 200000f));

        // --- UI: cash + score + menu ---

        Table root = new Table();
        root.setFillParent(true);
        uiStage.addActor(root);

        // Top bar
        Table topBar = new Table();
        topBar.top().left().pad(10);
        topBar.setFillParent(true);

        // Cash label
        cashLabel = new Label("$" + formatCash(CashManager.getCash()), skin);
        cashLabel.setFontScale(1.2f);
        cashLabel.setAlignment(Align.left);
        topBar.add(cashLabel).left().padRight(20f);

        // Score label
        scoreLabel = new Label("Score: 0", skin);
        scoreLabel.setFontScale(1.2f);
        scoreLabel.setAlignment(Align.left);
        topBar.add(scoreLabel).left().expandX();

        // Distance label
        distanceLabel = new Label("Dist: 0 m", skin);
        distanceLabel.setFontScale(1.2f);
        distanceLabel.setAlignment(Align.left);
        topBar.add(distanceLabel).left().padRight(20f);

        //  Time label
        timeLabel = new Label("Time: 0.0s", skin);
        timeLabel.setFontScale(1.2f);
        timeLabel.setAlignment(Align.left);
        topBar.add(timeLabel).left().padRight(20f);

        // Speed label
        speedLabel = new Label("Speed: 0", skin);
        speedLabel.setFontScale(1.2f);
        speedLabel.setAlignment(Align.left);
        topBar.add(speedLabel).left().expandX();

        // Menu button & dropdown
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

        //  World render
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // Draw infinite road segments relative to camera
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

        // Draw traffic
        for (TrafficCar t : trafficCars) {
            t.render(batch);
        }

        // Draw player
        playerCar.render(batch);

        batch.end();

        // --- UI ---
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
        // Player input
        boolean moveForward = Gdx.input.isKeyPressed(Input.Keys.W);
        boolean brake = Gdx.input.isKeyPressed(Input.Keys.S);
        boolean turnLeft = Gdx.input.isKeyPressed(Input.Keys.A);
        boolean turnRight = Gdx.input.isKeyPressed(Input.Keys.D);

        playerCar.update(delta, moveForward, brake, turnLeft, turnRight, borderRectangles);

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
        scoreLabel.setText("Score: " + score);
        distanceLabel.setText("Dist: " + displayDistance + " m");
        timeLabel.setText(String.format("Time: %.1fs", elapsedTime));


        float rawSpeed = playerCar.getSpeed();

        // Convert from "mph * 10" back to mph
        float mph = rawSpeed / 10f;

        if (mph > 0f) {
            speedSumMphSeconds += mph * delta;
            speedSampleSeconds += delta;
            if (mph > maxSpeedMph) maxSpeedMph = mph;
        }


        int speedDisplay = (int) mph;

        speedLabel.setText("Speed: " + speedDisplay + " mph");


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
                        System.out.println("Near miss! +50 points");
                    }
                }
            }
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

    /** How much cash to award for this Free Ride run */
    private int calculateCashReward() {
        // Distance in "meters"
        int distMeters = (int) (distanceScore / 10f);

        // - 1 cash per 20 meters
        // - 1 cash per 15 score
        // - 1 cash per 5 seconds survived
        int distanceCash = distMeters / 20;
        int scoreCash    = score / 15;
        int timeCash     = (int) (elapsedTime / 5f);

        int total = distanceCash + scoreCash + timeCash;

        // Safety: never negative
        if (total < 0) total = 0;

        return total;
    }


    private void onCrash() {
        gameOver = true;
        runCrashCount++;

        long endMillis = System.currentTimeMillis();
        if (SupabaseAuth.isLoggedIn) {
            SupabaseGameData.submitRunTelemetry(
                "free_ride",
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


        // Local best
        HighScoreManager.submitScore("free_ride", score);
        int bestScore = HighScoreManager.getHighScore("free_ride");

        // Server leaderboard
        if (SupabaseAuth.isLoggedIn) {
            SupabaseGameData.submitScore("free_ride", score);
        }

        // Cash
        int cashEarned = calculateCashReward();
        int distMeters = (int) (distanceScore / 10f);

        // Achievements
        Array<AchievementsManager.AchievementState> newlyUnlocked =
            AchievementsManager.onFreeRideFinished(score, distMeters, elapsedTime);

        Runnable showCrashDialog = () -> {
            StringBuilder sb = new StringBuilder();
            sb.append("You crashed!\n")
                .append("Score: ").append(score).append("\n")
                .append("Distance: ").append(distMeters).append(" m\n")
                .append("Time: ").append(String.format("%.1f s", elapsedTime)).append("\n")
                .append("Best Score: ").append(bestScore).append("\n\n")
                .append("Cash earned: $").append(cashEarned).append("\n")
                .append("Total cash: $").append(formatCash(CashManager.getCash()));

            if (newlyUnlocked != null && newlyUnlocked.size > 0) {
                sb.append("\n\nAchievements unlocked:\n");
                for (AchievementsManager.AchievementState a : newlyUnlocked) {
                    sb.append("• ").append(a.def.name).append("\n");
                }
            }

            Dialog dialog = new Dialog("Crash!", skin) {
                @Override
                protected void result(Object obj) {
                    String choice = (String) obj;
                    if ("retry".equals(choice)) {
                        game.setScreen(new FreeRideScreen(game));
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
            CashManager.addCashAndSync(cashEarned, "free_ride_reward");
            Gdx.app.postRunnable(showCrashDialog);
        } else {
            showCrashDialog.run();
        }
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
        uiStage.dispose();
        skin.dispose();
        playerCar.dispose();

        for (TrafficCarType type : trafficTypes) {
            type.texture.dispose();
        }

    }

}
