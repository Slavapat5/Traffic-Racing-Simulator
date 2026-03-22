package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardScreen implements Screen {

    private final Game game;
    private Stage stage;
    private Skin skin;

    private Table entriesTable;
    private Label titleLabel;
    private Label subtitleLabel;
    private Label playerBestLabel;
    private String currentModeKey = "free_ride";

    //  background
    private Texture bgTexture;
    private Image bgImage;

    public LeaderboardScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        if (!SupabaseAuth.isLoggedIn) {
            game.setScreen(new LoginScreen(game));
            return;
        }

        stage = new Stage(new ScreenViewport());
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        Gdx.input.setInputProcessor(stage);

        // Background image
        try {
            bgTexture = new Texture(Gdx.files.internal("Menu_Background.png"));
            bgImage = new Image(bgTexture);
            bgImage.setFillParent(true);
            stage.addActor(bgImage);
        } catch (Exception e) {
            bgTexture = null; // just black if missing
        }

        //  Root layout: center a card
        Table root = new Table();
        root.setFillParent(true);
        root.center();
        stage.addActor(root);

        //  Card
        Table card = new Table(skin);
        card.pad(25);
        card.defaults().pad(6).fillX();
        card.setBackground("default-round");
        card.setColor(0.08f, 0.08f, 0.08f, 0.92f);

        root.add(card).width(900).height(550);

        // --- Title + subtitle ---
        titleLabel = new Label("Leaderboards", skin);
        titleLabel.setAlignment(Align.center);
        titleLabel.setFontScale(1.5f);

        subtitleLabel = new Label("Global top players by mode", skin);
        subtitleLabel.setAlignment(Align.center);
        subtitleLabel.setColor(Color.LIGHT_GRAY);

        card.add(titleLabel).colspan(2).padBottom(4).center().row();
        card.add(subtitleLabel).colspan(2).padBottom(10).center().row();

        // Mode buttons row
        Table modeRow = new Table();
        modeRow.defaults().pad(4).width(150).height(40);

        addModeButton(modeRow, "Free Ride",       "free_ride");
        addModeButton(modeRow, "Time Trial",      "time_trial");
        addModeButton(modeRow, "Endless 1-Way",   "endless_one_way");
        addModeButton(modeRow, "Endless 2-Way",   "endless_two_way");
        addModeButton(modeRow, "Drag Sprint",     "drag_sprint");

        card.add(modeRow).colspan(2).padBottom(8).center().row();

        // Best score label
        playerBestLabel = new Label("Your best: 0", skin);
        playerBestLabel.setAlignment(Align.left);
        playerBestLabel.setColor(Color.LIGHT_GRAY);
        card.add(playerBestLabel).left().colspan(2).padBottom(4).row();

        //  Entries table
        entriesTable = new Table(skin);
        entriesTable.defaults().pad(4).left();
        entriesTable.setBackground("default-round");
        entriesTable.setColor(0.05f, 0.05f, 0.05f, 1f);

        ScrollPane scrollPane = new ScrollPane(entriesTable, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setSmoothScrolling(true);
        scrollPane.getStyle().background = null; // remove gray box

        card.add(scrollPane).expand().fill().colspan(2).padTop(4).padBottom(10).row();

        // Custom textured back button
        ImageButton backButton = new ImageButton(UIStyles.getBackButtonStyle());
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new PlayScreen(game));
            }
        });


        Table bottomRow = new Table();
        bottomRow.add(backButton).width(80).height(30).left();

        card.add(bottomRow).colspan(2).left().padTop(4);

        // Load default mode leaderboard
        loadLeaderboardForMode(currentModeKey);
    }

    private void addModeButton(Table parent, String label, final String modeKey) {
        TextButton button = new TextButton(label, skin);
        button.getLabel().setAlignment(Align.center);

        // Make it look a bit like a tab
        button.getLabel().setColor(Color.LIGHT_GRAY);

        button.addListener(new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                currentModeKey = modeKey;
                loadLeaderboardForMode(modeKey);
            }
        });

        parent.add(button);
    }


     // Load leaderboard for a given mode using Supabase global leaderboard.

    private void loadLeaderboardForMode(String modeKey) {
        titleLabel.setText("Leaderboards – " + prettifyModeName(modeKey));

        playerBestLabel.setText("Top 10 players for this mode");

        entriesTable.clear();

        Label loading = new Label("Loading leaderboard...", skin);
        loading.setAlignment(Align.center);
        entriesTable.add(loading).pad(10).colspan(3).center();

        SupabaseGameData.fetchLeaderboard(
            modeKey,
            10,                           // top 10 entries
            SupabaseAuth.accessToken,     // auth token for Supabase
            leaderboardEntries -> {
                Gdx.app.postRunnable(() -> {
                    List<SimpleEntry> uiEntries = new ArrayList<>();

                    for (SupabaseGameData.LeaderboardEntry e : leaderboardEntries) {
                        String name = (e.username != null && !e.username.isEmpty())
                            ? e.username
                            : "Player";

                        uiEntries.add(new SimpleEntry(name, e.score));
                    }

                    populateEntries(uiEntries);
                });
            }
        );
    }

    // Builds the table rows from a list of entries.
    private void populateEntries(List<SimpleEntry> entries) {
        entriesTable.clear();

        // Header row
        Label headerRank  = new Label("#",       skin);
        Label headerName  = new Label("Player",  skin);
        Label headerScore = new Label("Score",   skin);
        headerRank.setColor(Color.GOLD);
        headerName.setColor(Color.GOLD);
        headerScore.setColor(Color.GOLD);

        entriesTable.add(headerRank).width(40);
        entriesTable.add(headerName).expandX().left();
        entriesTable.add(headerScore).width(120).right();
        entriesTable.row();

        int rank = 1;
        for (SimpleEntry e : entries) {
            Label rankLabel  = new Label(String.valueOf(rank), skin);
            Label nameLabel  = new Label(e.username, skin);
            Label scoreLabel = new Label(String.valueOf(e.score), skin);

            // Highlight top 3
            if (rank == 1) {
                rankLabel.setColor(Color.GOLD);
                nameLabel.setColor(Color.GOLD);
                scoreLabel.setColor(Color.GOLD);
            } else if (rank == 2) {
                Color silver = new Color(0.8f, 0.8f, 0.9f, 1f);
                rankLabel.setColor(silver);
                nameLabel.setColor(silver);
                scoreLabel.setColor(silver);
            } else if (rank == 3) {
                Color bronze = new Color(0.8f, 0.6f, 0.3f, 1f);
                rankLabel.setColor(bronze);
                nameLabel.setColor(bronze);
                scoreLabel.setColor(bronze);
            } else {
                // Slight zebra effect with text colour
                if (rank % 2 == 0) {
                    nameLabel.setColor(Color.LIGHT_GRAY);
                    scoreLabel.setColor(Color.LIGHT_GRAY);
                }
            }

            entriesTable.add(rankLabel).width(40);
            entriesTable.add(nameLabel).expandX().left();
            entriesTable.add(scoreLabel).width(120).right();
            entriesTable.row();

            rank++;
        }

        if (entries.isEmpty()) {
            Label none = new Label("No scores yet for this mode.", skin);
            none.setColor(Color.LIGHT_GRAY);
            entriesTable.add(none).colspan(3).padTop(10);
        }
    }

    /** Local helper model */
    private static class SimpleEntry {
        String username;
        int score;

        SimpleEntry(String username, int score) {
            this.username = username;
            this.score = score;
        }
    }

    private String prettifyModeName(String modeKey) {
        switch (modeKey) {
            case "free_ride":         return "Free Ride";
            case "time_trial":        return "Time Trial";
            case "endless_one_way":   return "Endless One Way";
            case "endless_two_way":   return "Endless Two Way";
            case "drag_sprint":       return "Drag Sprint";
            default:                  return modeKey;
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();
    }

    @Override public void resize(int width, int height) { stage.getViewport().update(width, height, true); }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() { dispose(); }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();
        if (bgTexture != null) bgTexture.dispose();
    }
}
