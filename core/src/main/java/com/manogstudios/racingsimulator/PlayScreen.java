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


public class PlayScreen implements Screen {
    private Game game;
    private Stage stage;
    private Skin skin;
    private TextButton playButton;
    private TextButton leaderboardButton;
    private SpriteBatch batch;
    private Texture backgroundTexture;

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

        backgroundTexture = new Texture(Gdx.files.internal("PlayScreen0.png"));

        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = new BitmapFont();
        buttonStyle.up = new TextureRegionDrawable(new TextureRegion(
            new Texture(Gdx.files.internal("PlayButton1.png"))));
        buttonStyle.down = new TextureRegionDrawable(new TextureRegion(
            new Texture(Gdx.files.internal("PlayButton2.png"))));

        skin = new Skin(Gdx.files.internal("uiskin.json"));

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

        // ACHIEVEMENTS BUTTON
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
        table.add(quitButton).width(300).height(40).right().bottom();


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
