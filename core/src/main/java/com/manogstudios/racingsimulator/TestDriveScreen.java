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

public class TestDriveScreen implements Screen {

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

    // Test drive should feel consistent: fixed spawn interval, no scaling.
    private float spawnInterval = 1.6f;

    // Session metrics (display only)
    private float startY;
    private float elapsedTime = 0f;
    private float distanceTravelled = 0f;
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

    private Label modeLabel;
    private Label speedLabel;
    private Label distanceLabel;
    private Label timeLabel;

    private float laneWidth;
    private float[] laneX;

    public TestDriveScreen(Game game) {
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

        // Traffic car types
        addTrafficType("BMW 330i - 2025.png", 600f, 600f, 5f);
        addTrafficType("Ford Fiesta ST - 2019.png", 600f, 600f, 4f);
        addTrafficType("Mazda MX-5 Miata - 2014.png", 600f, 600f, 3f);
        addTrafficType("Mclaren 650s - 2015.png", 600f, 600f, 1.5f);

        // Player car (selected)
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

        // Lane setup: 4 lanes
        laneWidth = roadWidth / 4f;
        laneX = new float[] {
            roadCenterX - laneWidth * 1.5f,
            roadCenterX - laneWidth * 0.5f,
            roadCenterX + laneWidth * 0.5f,
            roadCenterX + laneWidth * 1.5f
        };

        // Borders (keep car on road)
        borderRectangles = new ArrayList<>();
        float leftEdge = roadCenterX - roadWidth / 2f;
        float rightEdge = roadCenterX + roadWidth / 2f;
        borderRectangles.add(new Rectangle(leftEdge - 50f, -100000f, 50f, 200000f));
        borderRectangles.add(new Rectangle(rightEdge, -100000f, 50f, 200000f));

        setupUI();

        runStartMillis = System.currentTimeMillis();
        runCrashCount = 0;
        runNearMisses = 0;
        speedSumMphSeconds = 0f;
        speedSampleSeconds = 0f;
        maxSpeedMph = 0f;

    }

    private void setupUI() {
        Table topLeftInfo = new Table();
        topLeftInfo.setFillParent(true);
        topLeftInfo.top().left().padTop(18).padLeft(20);

        modeLabel = new Label("TEST DRIVE", skin);
        modeLabel.setFontScale(1.5f);
        modeLabel.setAlignment(Align.left);

        timeLabel = new Label("Time: 0.0s", skin);
        timeLabel.setFontScale(1.2f);
        timeLabel.setAlignment(Align.left);

        topLeftInfo.add(modeLabel).left().padBottom(8).row();
        topLeftInfo.add(timeLabel).left();

        uiStage.addActor(topLeftInfo);


        Table bottomRightInfo = new Table();
        bottomRightInfo.setFillParent(true);
        bottomRightInfo.bottom().right().padRight(35).padBottom(140);

        speedLabel = new Label("Speed: 0 mph", skin);
        speedLabel.setFontScale(2.3f);
        speedLabel.setAlignment(Align.right);

        distanceLabel = new Label("Dist: 0 m", skin);
        distanceLabel.setFontScale(1.9f);
        distanceLabel.setAlignment(Align.right);

        bottomRightInfo.add(speedLabel).right().padBottom(10).row();
        bottomRightInfo.add(distanceLabel).right();

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

        // --- World render ---
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

        // --- UI ---
        uiStage.act(delta);
        uiStage.draw();
    }

    private void updateLogic(float delta) {
        // Player input
        boolean moveForward = Gdx.input.isKeyPressed(Input.Keys.W);
        boolean brake = Gdx.input.isKeyPressed(Input.Keys.S);
        boolean turnLeft = Gdx.input.isKeyPressed(Input.Keys.A);
        boolean turnRight = Gdx.input.isKeyPressed(Input.Keys.D);

        playerCar.update(delta, moveForward, brake, turnLeft, turnRight, borderRectangles);

        // Camera follows the car
        float camY = playerCar.getY() + 300f;
        camera.position.set(roadCenterX, camY, 0);

        // Metrics (display only)
        elapsedTime += delta;

        float dy = playerCar.getY() - startY;
        if (dy < 0) dy = 0;
        distanceTravelled = dy;
        int displayDistance = (int) (distanceTravelled / 10f);

        timeLabel.setText(String.format("Time: %.1fs", elapsedTime));
        distanceLabel.setText("Dist: " + displayDistance + " m");

        float rawSpeed = playerCar.getSpeed();
        int mph = (int) (rawSpeed / 10f);

        if (mph > 0f) {
            speedSumMphSeconds += mph * delta;
            speedSampleSeconds += delta;
            if (mph > maxSpeedMph) maxSpeedMph = mph;
        }


        speedLabel.setText("Speed: " + mph + " mph");

        // Spawn traffic (fixed rate)
        spawnTimer -= delta;
        if (spawnTimer <= 0f) {
            spawnTrafficCar();
            spawnTimer = spawnInterval;
        }

        // Update traffic + collision
        Rectangle playerRect = playerCar.getBoundingRectangle();

        for (int i = trafficCars.size() - 1; i >= 0; i--) {
            TrafficCar t = trafficCars.get(i);
            t.update(delta);

            if (t.y < playerCar.getY() - 2000f) {
                trafficCars.remove(i);
                continue;
            }

            if (playerRect.overlaps(t.bounds)) {
                onCrash();
                break;
            }
        }
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



    private void onCrash() {
        gameOver = true;
        runCrashCount++;

        // Telemetry
        runCrashCount++;
        long endMillis = System.currentTimeMillis();
        if (SupabaseAuth.isLoggedIn) {
            SupabaseGameData.submitRunTelemetry(
                "test_drive",
                runStartMillis,
                endMillis,
                elapsedTime,
                (int) (distanceTravelled / 10f),
                0,              // score = 0 for test drive
                runCrashCount,
                0,              // near misses not tracked here
                null,
                null,
                CarSelectionData.getSelectedCarTexture(),
                "0.1.0",
                () -> System.out.println("Telemetry saved"),
                (err) -> System.out.println("Telemetry failed: " + err)
            );
        }

        int distMeters = (int) (distanceTravelled / 10f);

        Dialog dialog = new Dialog("Crash!", skin) {
            @Override
            protected void result(Object obj) {
                String choice = (String) obj;
                if ("retry".equals(choice)) {
                    game.setScreen(new TestDriveScreen(game));
                } else if ("modes".equals(choice)) {
                    game.setScreen(new GameModeSelectorScreen(game));
                }
            }
        };

        dialog.text("You crashed!\n\n"
            + "Distance: " + distMeters + " m\n"
            + "Time: " + String.format("%.1f s", elapsedTime) + "\n\n"
            + "No cash or scores are awarded in Test Drive.");
        dialog.button("Restart", "retry");
        dialog.button("Back to Modes", "modes");
        dialog.show(uiStage);
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
        if (batch != null) batch.dispose();
        if (roadTexture != null) roadTexture.dispose();
        if (surroundingsTexture != null) surroundingsTexture.dispose();
        if (uiStage != null) uiStage.dispose();
        if (skin != null) skin.dispose();
        if (playerCar != null) playerCar.dispose();

        for (TrafficCarType type : trafficTypes) {
            if (type.texture != null) type.texture.dispose();
        }
    }
}
