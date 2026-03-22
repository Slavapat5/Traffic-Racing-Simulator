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
        Table root = new Table();
        root.setFillParent(true);
        uiStage.addActor(root);

        Table topBar = new Table();
        topBar.top().left().pad(10);
        topBar.setFillParent(true);

        cashLabel = new Label("$" + formatCash(CashManager.getCash()), skin);
        cashLabel.setFontScale(1.2f);
        topBar.add(cashLabel).left().padRight(20f);

        playerInfoLabel = new Label("", skin);
        playerInfoLabel.setFontScale(1.1f);
        topBar.add(playerInfoLabel).left().padRight(30f);

        aiInfoLabel = new Label("", skin);
        aiInfoLabel.setFontScale(1.1f);
        topBar.add(aiInfoLabel).left().padRight(30f);

        statusLabel = new Label("Get ready...", skin);
        statusLabel.setFontScale(1.6f);
        statusLabel.setAlignment(Align.center);
        topBar.add(statusLabel).expandX().left();

        TextButton menuBtn = new TextButton("Menu", skin);
        topBar.add(menuBtn).right().width(90f);

        uiStage.addActor(topBar);

        final Table menuTable = new Table(skin);
        menuTable.setVisible(false);
        menuTable.defaults().pad(5).fillX().uniformX();
        menuTable.background("default-round");

        TextButton retryBtn = new TextButton("Retry", skin);
        TextButton modesBtn = new TextButton("Back to Modes", skin);
        TextButton homeBtn = new TextButton("Home", skin);

        menuTable.add(retryBtn).row();
        menuTable.add(modesBtn).row();
        menuTable.add(homeBtn).row();

        Table menuContainer = new Table();
        menuContainer.setFillParent(true);
        menuContainer.top().right().pad(10, 10, 0, 10);
        menuContainer.add(menuTable);
        uiStage.addActor(menuContainer);

        menuBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                menuTable.setVisible(!menuTable.isVisible());
            }
        });

        retryBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new DragRaceScreen(game));
            }
        });

        modesBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new GameModeSelectorScreen(game));
            }
        });

        homeBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });

        // initial UI text
        updateInfoLabels();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (!raceFinished) {
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
