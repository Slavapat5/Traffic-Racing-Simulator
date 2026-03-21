package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;
import com.manogstudios.racingsimulator.network.SupabaseGameData.LeaderboardEntry;

public class GameModeSelectorScreen implements Screen {

    private final Game game;
    private Stage stage;
    private Skin skin;

    private Label cashLabel;
    private ScrollPane scrollPane;

    // Background + card texture
    private Texture bgTexture;
    private Image bgImage;
    private Texture modeCardTexture;
    private Drawable modeCardBackground;

    public GameModeSelectorScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        skin = new Skin(Gdx.files.internal("uiskin.json"));

        setupBackground();
        setupModeCardBackground();

        // === ROOT LAYOUT OVERLAY ===
        Table root = new Table();
        root.setFillParent(true);
        root.top().padTop(90).padLeft(60).padRight(60).padBottom(110); // extra bottom pad for bottom controls
        stage.addActor(root);

        // --- Title row ---
        Label titleLabel = new Label("Select Game Mode", skin);
        titleLabel.setAlignment(Align.center);
        titleLabel.setFontScale(1.4f);

        Label subtitleLabel = new Label("Choose how you want to drive today", skin);
        subtitleLabel.setAlignment(Align.center);
        subtitleLabel.setColor(Color.LIGHT_GRAY);
        subtitleLabel.setFontScale(0.9f);

        Table titleTable = new Table();
        titleTable.add(titleLabel).row();
        titleTable.add(subtitleLabel).padTop(4f);

        root.add(titleTable).expandX().center().padBottom(30).row();

        // === MODE CARDS ROW ===
        Table modeListTable = new Table();
        modeListTable.pad(10);
        modeListTable.defaults().space(40);
        modeListTable.left();

        addModeCard(modeListTable,
            "Free Ride",
            "Drive freely in traffic, no timer. Earn cash by distance, time, and near misses.",
            "free_ride");

        addModeCard(modeListTable,
            "Time Trial",
            "Beat the clock. Reach the finish line as fast as possible.",
            "time_trial");

        addModeCard(modeListTable,
            "Endless One Way",
            "Dodge traffic going one direction. Longer runs = higher scores.",
            "endless_one_way");

        addModeCard(modeListTable,
            "Endless Two Way",
            "Traffic in both directions. Higher risk, higher rewards.",
            "endless_two_way");

        addModeCard(modeListTable,
            "Drag Sprint",
            "Short drag race sprint. Nail your launch and gear shifts.",
            "drag_sprint");

        addModeCard(modeListTable,
            "Test Drive",
            "Take any owned car for a no-pressure drive to feel its speed, handling and acceleration.",
            "test_drive");

        scrollPane = new ScrollPane(modeListTable, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(false, true); // horizontal only
        scrollPane.setSmoothScrolling(true);
        scrollPane.setFlingTime(0.2f);
        scrollPane.setScrollbarsOnTop(false);

        //  Make scroll pane transparent & hide the scroll bar track
        ScrollPane.ScrollPaneStyle spStyle = new ScrollPane.ScrollPaneStyle(scrollPane.getStyle());
        spStyle.background = null;
        spStyle.hScroll = null;
        spStyle.hScrollKnob = null;
        scrollPane.setStyle(spStyle);

        root.add(scrollPane)
            .expand()
            .fill()
            .padTop(10)
            .row();

        // === CASH (TOP-LEFT) ===
        cashLabel = new Label("$" + formatCash(CashManager.getCash()), skin);
        cashLabel.setFontScale(1.2f);
        cashLabel.setAlignment(Align.center);

        Container<Label> cashContainer = new Container<>(cashLabel);
        cashContainer.setBackground(createCashLabelBackground());
        cashContainer.setColor(Color.BLACK);

        Table cashOverlay = new Table();
        cashOverlay.setFillParent(true);
        cashOverlay.top().left().padTop(15).padLeft(20);
        cashOverlay.add(cashContainer).left();
        stage.addActor(cashOverlay);

        // === RESET SCORES (BOTTOM-CENTER) ===
        TextButton resetButton = new TextButton("Reset Scores", skin);
        resetButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Dialog dialog = new Dialog("Reset High Scores", skin) {
                    @Override
                    protected void result(Object obj) {
                        boolean confirmed = (Boolean) obj;
                        if (confirmed) {
                            HighScoreManager.resetAll();

                            if (SupabaseAuth.isLoggedIn) {
                                SupabaseGameData.resetHighScores(
                                    SupabaseAuth.userId,
                                    SupabaseAuth.accessToken
                                );
                            }

                            game.setScreen(new GameModeSelectorScreen(game));
                        }
                    }
                };

                dialog.text("Are you sure you want to reset all\nhigh scores for this account?");
                dialog.button("Yes", true);
                dialog.button("No", false);
                dialog.show(stage);
            }
        });

        Table bottomCenter = new Table();
        bottomCenter.setFillParent(true);
        bottomCenter.bottom().padBottom(18);
        bottomCenter.add(resetButton).center().width(200).height(45);
        stage.addActor(bottomCenter);

        // === BACK BUTTON (BOTTOM-RIGHT) ===
        ImageButton backButton = new ImageButton(UIStyles.getBackButtonStyle());
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });

        Table bottomRight = new Table();
        bottomRight.setFillParent(true);
        bottomRight.bottom().right().padRight(20).padBottom(18);
        bottomRight.add(backButton).width(80).height(30);
        stage.addActor(bottomRight);

        // Keyboard shortcut
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACKSPACE) {
                    game.setScreen(new MenuScreen(game));
                    return true;
                }
                return false;
            }
        });
    }

    private boolean hasLeaderboard(String modeKey) {
        switch (modeKey) {
            case "free_ride":
            case "time_trial":
            case "endless_one_way":
            case "endless_two_way":
            case "drag_sprint":
                return true;
            default:
                return false;
        }
    }

    // Background image
    private void setupBackground() {
        try {
            bgTexture = new Texture(Gdx.files.internal("login_bg.png"));
            bgImage = new Image(bgTexture);
            bgImage.setFillParent(true);
            stage.addActor(bgImage); // behind everything else
        } catch (Exception e) {
            bgTexture = null; // no background image, just default clear color
        }
    }

    // Mode card background
    private void setupModeCardBackground() {
        try {
            modeCardTexture = new Texture(Gdx.files.internal("mode_card_bg.png"));
            modeCardBackground = new TextureRegionDrawable(new TextureRegion(modeCardTexture));
        } catch (Exception e) {
            modeCardTexture = null;
            modeCardBackground = skin.newDrawable("default-round",
                new Color(0.1f, 0.1f, 0.1f, 0.9f));
        }
    }

    /** Create one mode card and add to horizontal list */
    private void addModeCard(Table parent, String title, String description, String modeKey) {
        Table card = new Table(skin);
        card.setBackground(modeCardBackground);
        card.pad(18).defaults().space(8);
        card.setTransform(true);

        Label titleLabel = new Label(title, skin);
        titleLabel.setAlignment(Align.center);
        titleLabel.setFontScale(1.1f);
        card.add(titleLabel).center().padBottom(4).row();

        Label descLabel = new Label(description, skin);
        descLabel.setColor(Color.LIGHT_GRAY);
        descLabel.setAlignment(Align.center);
        descLabel.setWrap(true);
        card.add(descLabel).width(280).padBottom(6).row();

        int bestScore = HighScoreManager.getHighScore(modeKey);
        Label highScoreLabel = new Label("Best: " + bestScore, skin);
        highScoreLabel.setAlignment(Align.center);
        highScoreLabel.setColor(Color.SKY);
        highScoreLabel.setFontScale(1.0f);
        card.add(highScoreLabel).padTop(2).row();

        Table buttonRow = new Table();
        TextButton playButton = new TextButton("Play", skin);
        buttonRow.add(playButton).width(110).height(40).padRight(6);

        TextButton leaderboardButton = new TextButton("Leaderboard", skin);
        buttonRow.add(leaderboardButton).width(130).height(40);

        boolean supported = hasLeaderboard(modeKey);
        leaderboardButton.setDisabled(!supported);
        leaderboardButton.getLabel().setColor(supported ? Color.WHITE : Color.GRAY);

        leaderboardButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (leaderboardButton.isDisabled()) return;
                showLeaderboardDialog(modeKey);
            }
        });

        card.add(buttonRow).padTop(6).center().row();

        playButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                startMode(modeKey);
            }
        });

        card.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y,
                                     int pointer, int button) {
                card.setScale(1.05f);
                return super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y,
                                int pointer, int button) {
                card.setScale(1f);
                super.touchUp(event, x, y, pointer, button);

                if (isOver(event.getListenerActor(), x, y)) {
                    startMode(modeKey);
                }
            }
        });

        parent.add(card).width(340).height(260).pad(20);
    }

    private void showLeaderboardDialog(String modeKey) {
        if (!SupabaseAuth.isLoggedIn) {
            Dialog d = new Dialog("Leaderboard", skin);
            d.text("You must be logged in to view the leaderboard.");
            d.button("OK");
            d.show(stage);
            return;
        }

        Dialog loading = new Dialog("Leaderboard", skin);
        loading.text("Loading top scores...");
        loading.button("Close");
        loading.show(stage);

        SupabaseGameData.fetchLeaderboard(
            modeKey,
            10,
            SupabaseAuth.accessToken,
            entries -> {
                Gdx.app.postRunnable(() -> {
                    loading.hide();

                    Dialog dialog = new Dialog(
                        "Top 10 – " + prettifyModeName(modeKey),
                        skin
                    );

                    styleLeaderboardDialog(dialog, entries);

                    dialog.button("Close");
                    dialog.show(stage);
                });
            }
        );
    }

    private void styleLeaderboardDialog(Dialog dialog,
                                        java.util.List<LeaderboardEntry> entries) {

        Table content = dialog.getContentTable();
        content.clear();
        content.pad(15);
        content.defaults().pad(4);

        content.setBackground("default-round");
        content.setColor(0.06f, 0.06f, 0.06f, 0.95f);

        if (entries == null || entries.isEmpty()) {
            Label none = new Label("No scores yet for this mode.", skin);
            none.setColor(Color.LIGHT_GRAY);
            none.setAlignment(Align.center);
            content.add(none).width(320).pad(10);
            return;
        }

        Table header = new Table();
        header.defaults().pad(3).left();

        Label rankHeader = new Label("#", skin);
        Label nameHeader = new Label("Player", skin);
        Label scoreHeader = new Label("Score", skin);

        rankHeader.setColor(Color.GOLD);
        nameHeader.setColor(Color.GOLD);
        scoreHeader.setColor(Color.GOLD);

        header.add(rankHeader).width(30).left();
        header.add(nameHeader).expandX().left();
        header.add(scoreHeader).width(80).right();

        content.add(header).expandX().fillX().row();

        Table entriesTable = new Table(skin);
        entriesTable.defaults().pad(3).left();
        entriesTable.setBackground("default-round");
        entriesTable.setColor(0.1f, 0.1f, 0.1f, 0.9f);

        int rank = 1;
        for (LeaderboardEntry e : entries) {
            String username = (e.username != null && !e.username.isEmpty())
                ? e.username
                : "Player";

            Label rankLabel = new Label(String.valueOf(rank), skin);
            Label nameLabel = new Label(username, skin);
            Label scoreLabel = new Label(String.valueOf(e.score), skin);

            rankLabel.setColor(Color.LIGHT_GRAY);
            nameLabel.setColor(Color.WHITE);
            scoreLabel.setColor(Color.CYAN);

            entriesTable.add(rankLabel).width(30).left();
            entriesTable.add(nameLabel).expandX().left();
            entriesTable.add(scoreLabel).width(80).right();
            entriesTable.row();

            rank++;
        }

        ScrollPane sp = new ScrollPane(entriesTable, skin);
        sp.setFadeScrollBars(true);
        sp.setScrollingDisabled(true, false);
        sp.setForceScroll(false, true);
        sp.setSmoothScrolling(true);
        sp.setOverscroll(false, false);

        content.row();
        content.add(sp)
            .width(360)
            .height(260)
            .expand()
            .fill();
    }

    private String prettifyModeName(String modeKey) {
        switch (modeKey) {
            case "free_ride":       return "Free Ride";
            case "time_trial":      return "Time Trial";
            case "endless_one_way": return "Endless One Way";
            case "endless_two_way": return "Endless Two Way";
            case "drag_sprint":     return "Drag Sprint";
            case "test_drive":      return "Test Drive";
            default:                return modeKey;
        }
    }

    /** Decides which screen to go to when a mode is selected */
    private void startMode(String modeKey) {
        switch (modeKey) {
            case "free_ride":
                game.setScreen(new FreeRideScreen(game));
                System.out.println("Start Free Ride");
                break;
            case "time_trial":
                game.setScreen(new TimeTrialScreen(game));
                System.out.println("Start Time Trial");
                break;
            case "endless_one_way":
                game.setScreen(new EndlessOneWayScreen(game));
                System.out.println("Start Endless One Way");
                break;
            case "endless_two_way":
                game.setScreen(new EndlessTwoWayScreen(game));
                System.out.println("Start Endless Two Way");
                break;
            case "drag_sprint":
                game.setScreen(new DragRaceScreen(game));
                System.out.println("Start Drag Sprint");
                break;
            case "test_drive":
                game.setScreen(new TestDriveScreen(game));
                System.out.println("Start Test Drive");
                break;
        }
    }

    private String formatCash(int cash) {
        return String.format("%,d", cash);
    }

    private Drawable createCashLabelBackground() {
        int width = 200;
        int height = 50;

        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.LIGHT_GRAY);
        pixmap.fill();

        pixmap.setColor(Color.BLACK);
        pixmap.drawRectangle(0, 0, width, height);

        Texture texture = new Texture(pixmap);
        pixmap.dispose();

        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    @Override
    public void render(float delta) {
        stage.act(delta);

        // Keep cash label live if cash changes during session
        cashLabel.setText("$" + formatCash(CashManager.getCash()));

        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();
        if (bgTexture != null) bgTexture.dispose();
        if (modeCardTexture != null) modeCardTexture.dispose();
    }
}
