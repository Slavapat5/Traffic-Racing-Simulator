package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;


public class PlayScreen implements Screen {
    private Game game;
    private Stage stage;
    private Skin skin;
    private TextButton playButton;
    private SpriteBatch batch;
    private Texture backgroundTexture;
    private Label versionLabel;
    private static final String GAME_VERSION = "Beta 1.4";



    public PlayScreen(Game game) {
        this.game = game;
        System.out.println("PlayScreen constructor called!");
    }

    @Override
    public void show() {
        SupabaseAuth.restoreSessionFromPrefs();


        if (!SupabaseAuth.isLoggedIn) {
            System.out.println("You must be logged in to play.");
            game.setScreen(new LoginScreen(game));
            return;
        }

        // tie local save managers to this Supabase user
        String userId = SupabaseAuth.userId;
        String token = SupabaseAuth.accessToken;
        System.out.println("Initializing save data for user: " + userId);

        CashManager.setCurrentUser(userId);
        CashManager.loadCash();

        CarOwnershipManager.setCurrentUser(userId);
        CarOwnershipManager.loadOwnedCars();

        HighScoreManager.setCurrentUser(userId);
        HighScoreManager.loadHighScores();
        AchievementsManager.setCurrentUser(userId);

        SupabaseGameData.loadProfile(userId, token, () -> {
            System.out.println("Profile loaded from Supabase. Cash = " + CashManager.getCash());
            // could refresh any cash labels here.
        });

        SupabaseGameData.loadOwnedCars(userId, token, () -> {
            System.out.println("Owned cars loaded from Supabase: " + CarOwnershipManager.getOwnedCars().size());
        });

        SupabaseGameData.loadHighScores(userId, token, () -> {
            System.out.println("High scores loaded from Supabase.");
        });


        System.out.println("PlayScreen show() method called!");
        batch = new SpriteBatch();
        stage = new Stage(new ScreenViewport());

        backgroundTexture = new Texture(Gdx.files.internal("TitleScreen1.png")); // TitleScreen1 and TitleScreen2 are both different versions

        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = new BitmapFont();
        buttonStyle.up = new TextureRegionDrawable(new TextureRegion(
            new Texture(Gdx.files.internal("PlayButton1.png"))));
        buttonStyle.down = new TextureRegionDrawable(new TextureRegion(
            new Texture(Gdx.files.internal("PlayButton2.png"))));

        skin = new Skin(Gdx.files.internal("uiskin.json"));
        versionLabel = new Label(GAME_VERSION, skin);
        versionLabel.setFontScale(1.6f);

        playButton = new TextButton("Play", buttonStyle);
        playButton.setTransform(true);
        playButton.setSize(300, 40);
        playButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (game != null) {
                    game.setScreen(new MenuScreen(game));
                }
            }
        });

        TextButton quitButton = new TextButton("Quit", buttonStyle);
        quitButton.setTransform(true);
        quitButton.setSize(300, 40);
        quitButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Gdx.app.exit();
            }
        });

        //  Leaderboard button
        TextButton leaderboardButton = new TextButton("Leaderboards", buttonStyle);
        leaderboardButton.setTransform(true);
        leaderboardButton.setSize(300, 40);
        leaderboardButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (game != null) {
                    game.setScreen(new LeaderboardScreen(game));
                }
            }
        });

        // Settings button
        TextButton settingsButton = new TextButton("Settings", buttonStyle);
        settingsButton.setTransform(true);
        settingsButton.setSize(300, 40);
        settingsButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (game != null) {
                    game.setScreen(new SettingsScreen(game));
                }
            }
        });

        // Achievements button
        TextButton achievementsButton = new TextButton("Achievements", buttonStyle);
        achievementsButton.setTransform(true);
        achievementsButton.setSize(300, 40);
        achievementsButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (game != null) {
                    game.setScreen(new AchievementsScreen(game));
                }
            }
        });



        Table table = new Table();
        table.setFillParent(true);
        table.bottom().right();
        table.padBottom(150);
        table.padRight(260);

        table.add(playButton).width(300).height(40).padBottom(10).right().bottom();
        table.row();
        table.add(achievementsButton).width(300).height(40).padBottom(10).right().bottom();
        table.row();
        table.add(leaderboardButton).width(300).height(40).padBottom(10).right().bottom();
        table.row();
        table.add(settingsButton).width(300).height(40).padBottom(10).right().bottom();
        table.row();
        table.add(quitButton).width(300).height(40).right().bottom();

        Table versionTable = new Table();
        versionTable.setFillParent(true);
        versionTable.bottom().left();
        versionTable.padLeft(20);
        versionTable.padBottom(20);

        versionTable.add(versionLabel);


// Privacy / data notice text
        Label privacyLabel = new Label(
            "By using an account, you agree that this game stores your email/login account, " +
                "username, leaderboard scores, owned cars, cash, achievements, and 2FA status using Supabase. " +
                "See Privacy Notice.",
            skin
        );

        privacyLabel.setFontScale(1f);
        privacyLabel.setColor(Color.LIGHT_GRAY);
        privacyLabel.setWrap(true);
        privacyLabel.setAlignment(Align.center);

        Table privacyTable = new Table();
        privacyTable.setFillParent(true);
        privacyTable.bottom();
        privacyTable.padBottom(10);
        privacyTable.padLeft(260);
        privacyTable.padRight(260);

        privacyTable.add(privacyLabel).width(900).center();


        stage.addActor(versionTable);
        stage.addActor(privacyTable);
        stage.addActor(table);

        Gdx.input.setInputProcessor(stage);
    }


    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.begin();
        batch.draw(backgroundTexture, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.end();

        stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {
        batch.dispose();
        backgroundTexture.dispose();
        skin.dispose();
        stage.dispose();
    }

    @Override
    public void dispose() {

    }
}
