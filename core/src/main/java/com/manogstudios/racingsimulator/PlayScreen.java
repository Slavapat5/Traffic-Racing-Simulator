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
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;


public class PlayScreen implements Screen {
    private Game game;
    private Stage stage;
    private Skin skin;
    private TextButton playButton;
    private SpriteBatch batch;
    private Texture backgroundTexture;
    private Texture dailyBonusIconTexture;
    private Texture dailyQuestIconTexture;

    private Label versionLabel;
    private static final String GAME_VERSION = "Beta 1.8";



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

        dailyBonusIconTexture = new Texture(Gdx.files.internal("PlayScreen_Gift.png"));
        dailyQuestIconTexture = new Texture(Gdx.files.internal("PlayScreen_Quest.png"));

        ImageButton dailyBonusButton = new ImageButton(
            new TextureRegionDrawable(new TextureRegion(dailyBonusIconTexture))
        );

        dailyBonusButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showDailyBonusPopup();
            }
        });

        ImageButton dailyQuestsButton = new ImageButton(
            new TextureRegionDrawable(new TextureRegion(dailyQuestIconTexture))
        );

        dailyQuestsButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showDailyQuestsPopup();
            }
        });

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

        Table dailyBonusTable = new Table();
        dailyBonusTable.setFillParent(true);
        dailyBonusTable.top().left();
        dailyBonusTable.padTop(25);
        dailyBonusTable.padLeft(25);

        dailyBonusTable.add(dailyBonusButton).width(90).height(90).padRight(15);
        dailyBonusTable.add(dailyQuestsButton).width(90).height(90);

        stage.addActor(dailyBonusTable);


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

    private void showDailyQuestsPopup() {
        Dialog dialog = new Dialog("Daily Quests", skin);
        dialog.getContentTable().pad(25);

        Label loadingLabel = new Label("Loading daily quests...", skin);
        loadingLabel.setAlignment(Align.center);

        TextButton closeButton = new TextButton("Close", skin);
        closeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dialog.hide();
            }
        });

        dialog.getContentTable().add(loadingLabel).width(720).padBottom(20).row();
        dialog.getContentTable().add(closeButton).width(160).height(45).padTop(10).row();

        dialog.show(stage);
        resizeDailyQuestDialog(dialog);

        SupabaseGameData.fetchDailyQuests(quests -> {
            dialog.getContentTable().clear();
            dialog.getContentTable().pad(25);

            Label infoLabel = new Label(
                "Complete these challenges before tomorrow to earn extra cash.",
                skin
            );
            infoLabel.setWrap(true);
            infoLabel.setAlignment(Align.center);
            infoLabel.setColor(Color.LIGHT_GRAY);

            dialog.getContentTable().add(infoLabel).width(720).padBottom(18).row();

            if (quests == null || quests.isEmpty()) {
                Label none = new Label("No daily quests available.", skin);
                none.setAlignment(Align.center);

                dialog.getContentTable().add(none).width(720).height(420).pad(10).row();
                dialog.getContentTable().add(closeButton).width(160).height(45).padTop(12).row();

                resizeDailyQuestDialog(dialog);
                return;
            }

            Table questList = new Table(skin);
            questList.defaults().pad(10).left();

            for (SupabaseGameData.DailyQuest q : quests) {
                Table card = new Table(skin);
                card.setBackground("default-round");
                card.setColor(0.12f, 0.12f, 0.12f, 0.95f);
                card.pad(15);
                card.defaults().left().padBottom(6);

                Label title = new Label(q.title, skin);
                title.setFontScale(1.2f);
                title.setColor(q.completed ? Color.GREEN : Color.WHITE);

                Label desc = new Label(q.description, skin);
                desc.setWrap(true);
                desc.setColor(Color.LIGHT_GRAY);

                int shownProgress = Math.min(q.progress, q.target);

                Label progress = new Label(
                    "Progress: " + shownProgress + " / " + q.target,
                    skin
                );

                Label reward = new Label(
                    q.completed
                        ? "Completed - Reward paid: $" + formatCash(q.rewardCash)
                        : "Reward: $" + formatCash(q.rewardCash),
                    skin
                );

                reward.setColor(q.completed ? Color.GREEN : Color.GOLD);

                card.add(title).left().row();
                card.add(desc).width(640).left().row();
                card.add(progress).left().row();
                card.add(reward).left().row();

                questList.add(card).width(690).fillX().padBottom(10).row();
            }

            ScrollPane scrollPane = new ScrollPane(questList, skin);
            scrollPane.setFadeScrollBars(false);
            scrollPane.setScrollingDisabled(true, false);
            scrollPane.setForceScroll(false, true);
            scrollPane.setSmoothScrolling(true);

            dialog.getContentTable().add(scrollPane).width(740).height(520).row();
            dialog.getContentTable().add(closeButton).width(160).height(45).padTop(14).row();

            resizeDailyQuestDialog(dialog);

        }, error -> {
            dialog.getContentTable().clear();
            dialog.getContentTable().pad(25);

            Label errorLabel = new Label("Could not load daily quests: " + error, skin);
            errorLabel.setWrap(true);
            errorLabel.setAlignment(Align.center);
            errorLabel.setColor(Color.RED);

            dialog.getContentTable().add(errorLabel).width(720).height(420).padBottom(15).row();
            dialog.getContentTable().add(closeButton).width(160).height(45).padTop(10).row();

            resizeDailyQuestDialog(dialog);
        });
    }

    private void resizeDailyQuestDialog(Dialog dialog) {
        float dialogWidth = Math.min(820f, stage.getWidth() - 80f);
        float dialogHeight = Math.min(700f, stage.getHeight() - 80f);

        dialog.setSize(dialogWidth, dialogHeight);
        dialog.setPosition(
            (stage.getWidth() - dialogWidth) / 2f,
            (stage.getHeight() - dialogHeight) / 2f
        );
    }

    private void showDailyBonusPopup() {
        Dialog dialog = new Dialog("Daily Login Bonus", skin);
        dialog.getContentTable().pad(20);

        Label infoLabel = new Label(
            "Claim one reward per day.\n" +
                "Log in every day to keep your streak.\n" +
                "If you miss a day, your streak resets.",
            skin
        );

        infoLabel.setWrap(true);
        infoLabel.setAlignment(Align.center);

        dialog.getContentTable().add(infoLabel).width(420).padBottom(15).row();

        Table rewardsTable = new Table(skin);
        rewardsTable.defaults().pad(6);

        int[] rewards = {1000, 1500, 2000, 2500, 3000, 4000, 7500};

        for (int i = 0; i < rewards.length; i++) {
            Label rewardLabel = new Label(
                "Day " + (i + 1) + ": $" + rewards[i],
                skin
            );

            rewardsTable.add(rewardLabel).left().row();
        }

        dialog.getContentTable().add(rewardsTable).padBottom(15).row();

        TextButton claimButton = new TextButton("Claim Today", skin);
        TextButton closeButton = new TextButton("Close", skin);

        Table buttonRow = new Table();
        buttonRow.add(claimButton).width(180).height(45).padRight(10);
        buttonRow.add(closeButton).width(120).height(45);

        dialog.getContentTable().add(buttonRow).row();

        Label feedbackLabel = new Label("", skin);
        feedbackLabel.setWrap(true);
        feedbackLabel.setAlignment(Align.center);

        dialog.getContentTable().add(feedbackLabel).width(420).padTop(10).row();

        claimButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                claimButton.setDisabled(true);
                feedbackLabel.setText("Checking daily bonus...");

                SupabaseGameData.claimDailyLogin(result -> {
                    if (result.claimed) {
                        feedbackLabel.setColor(Color.GREEN);
                        feedbackLabel.setText(
                            "Claimed $" + result.reward + "!\n" +
                                "Current streak: " + result.streak + " day(s).\n" +
                                "New cash balance: $" + result.cash
                        );
                    } else {
                        feedbackLabel.setColor(Color.YELLOW);
                        feedbackLabel.setText(
                            "You already claimed today's reward.\n" +
                                "Come back tomorrow.\n" +
                                "Current streak: " + result.streak + " day(s)."
                        );
                    }
                }, error -> {
                    claimButton.setDisabled(false);
                    feedbackLabel.setColor(Color.RED);
                    feedbackLabel.setText("Could not claim daily bonus: " + error);
                });
            }
        });

        closeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dialog.hide();
            }
        });

        dialog.show(stage);
    }

    private String formatCash(int cash) {
        return String.format("%,d", cash);
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

        if (backgroundTexture != null) backgroundTexture.dispose();
        if (dailyBonusIconTexture != null) dailyBonusIconTexture.dispose();
        if (dailyQuestIconTexture != null) dailyQuestIconTexture.dispose();

        skin.dispose();
        stage.dispose();
    }

    @Override
    public void dispose() {

    }
}
