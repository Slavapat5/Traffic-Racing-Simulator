package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
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

    private Texture defaultButtonUpTexture;
    private Texture defaultButtonDownTexture;
    private Texture defaultButtonOverTexture;
    private Texture cashLabelTexture;

    private TextButton.TextButtonStyle defaultButtonStyle;

    public GameModeSelectorScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        skin = new Skin(Gdx.files.internal("uiskin.json"));
        defaultButtonStyle = createDefaultButtonStyle();

        setupBackground();
        setupModeCardBackground();

        //  Root Layout Overlay
        Table root = new Table();
        root.setFillParent(true);
        root.top().padTop(90).padLeft(60).padRight(60).padBottom(110); // extra bottom pad for bottom controls
        stage.addActor(root);

        // Title row
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

        // MODE CARDS ROW
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

        //  CASH (TOP-LEFT)
        cashLabel = new Label("$" + formatCash(CashManager.getCash()), skin);
        cashLabel.setFontScale(1.2f);
        cashLabel.setAlignment(Align.center);

        Container<Label> cashContainer = new Container<>(cashLabel);
        cashContainer.setBackground(createCashLabelBackground());
        cashContainer.setColor(Color.BLACK);

        Table cashOverlay = new Table();
        cashOverlay.setFillParent(true);
        cashOverlay.top().left().padTop(15).padLeft(20);
        cashOverlay.add(cashContainer).width(200).height(50).left();
        stage.addActor(cashOverlay);

        //  RESET SCORES (BOTTOM-CENTER)
        TextButton resetButton = new TextButton("Reset Scores", defaultButtonStyle);
        resetButton.getLabel().setAlignment(Align.center);
        resetButton.getLabel().setFontScale(1.0f);
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
        bottomCenter.add(resetButton).center().width(220).height(46);
        stage.addActor(bottomCenter);

        //  BACK BUTTON (BOTTOM-RIGHT)
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
            bgTexture = null;
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
        card.pad(22, 18, 18, 18).defaults().space(7);
        card.setTransform(true);

        Label titleLabel = new Label(title, skin);
        titleLabel.setAlignment(Align.center);
        titleLabel.setFontScale(1.1f);

        card.add(titleLabel)
            .width(330)
            .height(48)
            .center()
            .padTop(6)
            .padBottom(0)
            .row();

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

        TextButton playButton = new TextButton("Play", defaultButtonStyle);
        playButton.getLabel().setAlignment(Align.center);
        playButton.getLabel().setFontScale(1.08f);

        buttonRow.add(playButton).width(145).height(42).padRight(10);

        TextButton leaderboardButton = new TextButton("Leaderboard", defaultButtonStyle);
        leaderboardButton.getLabel().setAlignment(Align.center);
        leaderboardButton.getLabel().setFontScale(0.98f);

        buttonRow.add(leaderboardButton).width(165).height(42);

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

        parent.add(card).width(380).height(280).pad(20);
    }

    private void showLeaderboardDialog(String modeKey) {
        if (!SupabaseAuth.isLoggedIn) {
            showSimpleLeaderboardMessage(
                "Leaderboard",
                "You must be logged in to view the leaderboard."
            );
            return;
        }

        Dialog loading = new Dialog("Leaderboard", createModernDialogStyle());
        loading.getTitleLabel().setAlignment(Align.center);
        loading.getTitleLabel().setFontScale(1.1f);
        loading.getContentTable().pad(22);

        Table loadingPanel = new Table();
        loadingPanel.setBackground(createLeaderboardPanelBackground());
        loadingPanel.pad(18);

        Label loadingLabel = new Label("Loading top scores...", skin);
        loadingLabel.setAlignment(Align.center);
        loadingLabel.setColor(Color.LIGHT_GRAY);

        loadingPanel.add(loadingLabel).width(320).center();

        TextButton closeLoadingButton = createDialogButton("Close");
        closeLoadingButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                loading.hide();
            }
        });

        loading.getContentTable().add(loadingPanel).width(380).padBottom(16).row();
        loading.getContentTable().add(closeLoadingButton).width(140).height(42).row();

        loading.show(stage);
        sizeLeaderboardDialog(loading);

        SupabaseGameData.fetchLeaderboard(
            modeKey,
            10,
            SupabaseAuth.accessToken,
            entries -> {
                Gdx.app.postRunnable(() -> {
                    loading.hide();

                    Dialog dialog = new Dialog(
                        "Leaderboard - " + prettifyModeName(modeKey),
                        createModernDialogStyle()
                    );

                    dialog.getTitleLabel().setAlignment(Align.center);
                    dialog.getTitleLabel().setFontScale(1.12f);

                    styleLeaderboardDialog(dialog, entries);

                    dialog.getButtonTable().clear();

                    TextButton closeButton = createDialogButton("Close");
                    closeButton.addListener(new ClickListener() {
                        @Override
                        public void clicked(InputEvent event, float x, float y) {
                            dialog.hide();
                        }
                    });

                    dialog.getButtonTable().add(closeButton).width(150).height(42).padTop(12);

                    dialog.show(stage);
                    sizeLeaderboardDialog(dialog);
                });
            }
        );
    }

    private void styleLeaderboardDialog(Dialog dialog,
                                        java.util.List<LeaderboardEntry> entries) {

        Table content = dialog.getContentTable();
        content.clear();
        content.pad(20);
        content.defaults().pad(4);

        Label subtitle = new Label("Best scores for this game mode", skin);
        subtitle.setColor(Color.LIGHT_GRAY);
        subtitle.setAlignment(Align.center);
        subtitle.setFontScale(0.9f);

        content.add(subtitle).width(420).center().padBottom(10).row();

        if (entries == null || entries.isEmpty()) {
            Table emptyPanel = new Table();
            emptyPanel.setBackground(createLeaderboardPanelBackground());
            emptyPanel.pad(20);

            Label none = new Label("No scores yet for this mode.", skin);
            none.setColor(Color.LIGHT_GRAY);
            none.setAlignment(Align.center);

            emptyPanel.add(none).width(340).center();

            content.add(emptyPanel).width(400).height(220).row();
            return;
        }

        Table outerPanel = new Table();
        outerPanel.setBackground(createLeaderboardPanelBackground());
        outerPanel.pad(14);

        Table header = new Table();
        header.defaults().pad(4).left();

        Label rankHeader = new Label("#", skin);
        Label nameHeader = new Label("Player", skin);
        Label scoreHeader = new Label("Score", skin);

        rankHeader.setColor(Color.GOLD);
        nameHeader.setColor(Color.GOLD);
        scoreHeader.setColor(Color.GOLD);

        header.add(rankHeader).width(40).left();
        header.add(nameHeader).expandX().left();
        header.add(scoreHeader).width(90).right();

        outerPanel.add(header).expandX().fillX().padBottom(8).row();

        Table entriesTable = new Table();
        entriesTable.defaults().pad(5).left();

        int rank = 1;
        for (LeaderboardEntry e : entries) {
            String username = (e.username != null && !e.username.isEmpty())
                ? e.username
                : "Player";

            Color rankColor = Color.LIGHT_GRAY;
            if (rank == 1) rankColor = Color.GOLD;
            else if (rank == 2) rankColor = new Color(0.80f, 0.80f, 0.85f, 1f);
            else if (rank == 3) rankColor = new Color(0.80f, 0.55f, 0.30f, 1f);

            Label rankLabel = new Label(String.valueOf(rank), skin);
            Label nameLabel = new Label(username, skin);
            Label scoreLabel = new Label(String.valueOf(e.score), skin);

            rankLabel.setColor(rankColor);
            nameLabel.setColor(Color.WHITE);
            scoreLabel.setColor(Color.CYAN);

            entriesTable.add(rankLabel).width(40).left();
            entriesTable.add(nameLabel).expandX().left();
            entriesTable.add(scoreLabel).width(90).right();
            entriesTable.row();

            rank++;
        }

        ScrollPane sp = new ScrollPane(entriesTable, skin);
        sp.setFadeScrollBars(true);
        sp.setScrollingDisabled(true, false);
        sp.setForceScroll(false, true);
        sp.setSmoothScrolling(true);
        sp.setOverscroll(false, false);

        outerPanel.add(sp).width(390).height(260).expand().fill().row();

        content.add(outerPanel).width(420).row();
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
        if ("drag_sprint".equals(modeKey)) {
            game.setScreen(new DragRaceScreen(game));
            return;
        }

        game.setScreen(new LocationSelectorScreen(game, modeKey));
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

    private Window.WindowStyle createModernDialogStyle() {
        Window.WindowStyle style = new Window.WindowStyle(skin.get(Window.WindowStyle.class));

        style.background = skin.newDrawable(
            "default-round",
            new Color(0.035f, 0.035f, 0.045f, 0.97f)
        );

        style.titleFont = skin.getFont("default-font");
        style.titleFontColor = Color.WHITE;

        return style;
    }

    private Drawable createLeaderboardPanelBackground() {
        return skin.newDrawable(
            "default-round",
            new Color(0.09f, 0.09f, 0.12f, 0.96f)
        );
    }

    private TextButton createDialogButton(String text) {
        TextButton button = new TextButton(text, defaultButtonStyle);
        button.getLabel().setAlignment(Align.center);
        button.getLabel().setFontScale(0.95f);
        return button;
    }

    private void sizeLeaderboardDialog(Dialog dialog) {
        float dialogWidth = Math.min(520f, stage.getWidth() - 80f);
        float dialogHeight = Math.min(500f, stage.getHeight() - 80f);

        dialog.setSize(dialogWidth, dialogHeight);
        dialog.setPosition(
            (stage.getWidth() - dialogWidth) / 2f,
            (stage.getHeight() - dialogHeight) / 2f
        );
    }

    private void showSimpleLeaderboardMessage(String title, String message) {
        Dialog dialog = new Dialog(title, createModernDialogStyle());
        dialog.getTitleLabel().setAlignment(Align.center);
        dialog.getTitleLabel().setFontScale(1.1f);
        dialog.getContentTable().pad(22);

        Table panel = new Table();
        panel.setBackground(createLeaderboardPanelBackground());
        panel.pad(18);

        Label messageLabel = new Label(message, skin);
        messageLabel.setWrap(true);
        messageLabel.setAlignment(Align.center);
        messageLabel.setColor(Color.LIGHT_GRAY);

        panel.add(messageLabel).width(360).center();

        TextButton closeButton = createDialogButton("OK");
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dialog.hide();
            }
        });

        dialog.getContentTable().add(panel).width(400).padBottom(16).row();
        dialog.getContentTable().add(closeButton).width(140).height(42).row();

        dialog.show(stage);
        sizeLeaderboardDialog(dialog);
    }

    private String formatCash(int cash) {
        return String.format("%,d", cash);
    }

    private Drawable createCashLabelBackground() {
        if (cashLabelTexture == null) {
            cashLabelTexture = new Texture(Gdx.files.internal("Cash_Label.png"));
        }

        TextureRegionDrawable drawable = new TextureRegionDrawable(new TextureRegion(cashLabelTexture));
        drawable.setMinWidth(0);
        drawable.setMinHeight(0);
        return drawable;
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

        if (defaultButtonUpTexture != null) defaultButtonUpTexture.dispose();
        if (defaultButtonDownTexture != null) defaultButtonDownTexture.dispose();
        if (defaultButtonOverTexture != null) defaultButtonOverTexture.dispose();
        if (cashLabelTexture != null) cashLabelTexture.dispose();
    }
}
