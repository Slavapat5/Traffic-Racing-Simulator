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
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;


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
        TextButton.TextButtonStyle popupButtonStyle = createModernPopupButtonStyle();

        Dialog dialog = new Dialog("Daily Quests", createModernDialogStyle());
        dialog.getTitleLabel().setAlignment(Align.center);
        dialog.getTitleLabel().setFontScale(1.15f);
        dialog.getContentTable().pad(28);

        Label loadingLabel = new Label("Loading daily quests...", skin);
        loadingLabel.setAlignment(Align.center);
        loadingLabel.setColor(Color.LIGHT_GRAY);

        TextButton closeButton = new TextButton("Close", popupButtonStyle);
        closeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dialog.hide();
            }
        });

        dialog.getContentTable().add(loadingLabel).width(720).padBottom(20).row();
        dialog.getContentTable().add(closeButton).width(170).height(48).padTop(10).row();

        dialog.show(stage);
        resizeDailyQuestDialog(dialog);

        SupabaseGameData.fetchDailyQuests(quests -> {
            dialog.getContentTable().clear();
            dialog.getContentTable().pad(28);

            Label infoLabel = new Label(
                "Complete these challenges before tomorrow to earn extra cash.",
                skin
            );
            infoLabel.setWrap(true);
            infoLabel.setAlignment(Align.center);
            infoLabel.setColor(Color.LIGHT_GRAY);

            dialog.getContentTable().add(infoLabel).width(720).padBottom(20).row();

            if (quests == null || quests.isEmpty()) {
                Table emptyPanel = new Table();
                emptyPanel.setBackground(darkPanelDrawable());
                emptyPanel.pad(25);

                Label none = new Label("No daily quests available.", skin);
                none.setAlignment(Align.center);
                none.setColor(Color.LIGHT_GRAY);

                emptyPanel.add(none).center();

                dialog.getContentTable().add(emptyPanel).width(720).height(420).pad(10).row();
                dialog.getContentTable().add(closeButton).width(170).height(48).padTop(14).row();

                resizeDailyQuestDialog(dialog);
                return;
            }

            Table questList = new Table();
            questList.defaults().padBottom(12).left();

            for (SupabaseGameData.DailyQuest q : quests) {
                Table card = new Table();
                card.setBackground(darkQuestCardDrawable(q.completed));
                card.pad(18);
                card.defaults().left().padBottom(7);

                Label title = new Label(q.title, skin);
                title.setFontScale(1.15f);
                title.setColor(q.completed ? Color.GREEN : Color.WHITE);

                Label desc = new Label(q.description, skin);
                desc.setWrap(true);
                desc.setColor(Color.LIGHT_GRAY);

                int shownProgress = Math.min(q.progress, q.target);

                Label progress = new Label(
                    "Progress: " + shownProgress + " / " + q.target,
                    skin
                );
                progress.setColor(Color.WHITE);

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

                questList.add(card).width(700).fillX().padBottom(12).row();
            }

            ScrollPane scrollPane = new ScrollPane(questList, skin);
            scrollPane.setFadeScrollBars(false);
            scrollPane.setScrollingDisabled(true, false);
            scrollPane.setForceScroll(false, true);
            scrollPane.setSmoothScrolling(true);
            scrollPane.setOverscroll(false, false);

            dialog.getContentTable().add(scrollPane).width(740).height(510).row();
            dialog.getContentTable().add(closeButton).width(170).height(48).padTop(16).row();

            resizeDailyQuestDialog(dialog);

        }, error -> {
            dialog.getContentTable().clear();
            dialog.getContentTable().pad(28);

            Table errorPanel = new Table();
            errorPanel.setBackground(darkPanelDrawable());
            errorPanel.pad(25);

            Label errorLabel = new Label("Could not load daily quests:\n" + error, skin);
            errorLabel.setWrap(true);
            errorLabel.setAlignment(Align.center);
            errorLabel.setColor(Color.RED);

            errorPanel.add(errorLabel).width(650).center();

            dialog.getContentTable().add(errorPanel).width(720).height(420).padBottom(15).row();
            dialog.getContentTable().add(closeButton).width(170).height(48).padTop(10).row();

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
        TextButton.TextButtonStyle popupButtonStyle = createModernPopupButtonStyle();

        Dialog dialog = new Dialog("Daily Login Bonus", createModernDialogStyle());
        dialog.getTitleLabel().setAlignment(Align.center);
        dialog.getTitleLabel().setFontScale(1.15f);
        dialog.getContentTable().pad(28);

        Label infoLabel = new Label(
            "Claim one reward per day.\n" +
                "Log in every day to keep your streak.\n" +
                "If you miss a day, your streak resets.",
            skin
        );

        infoLabel.setWrap(true);
        infoLabel.setAlignment(Align.center);
        infoLabel.setColor(Color.LIGHT_GRAY);

        dialog.getContentTable().add(infoLabel).width(460).padBottom(18).row();

        Table rewardsPanel = new Table();
        rewardsPanel.setBackground(darkPanelDrawable());
        rewardsPanel.pad(18);
        rewardsPanel.defaults().pad(6).left();

        int[] rewards = {1000, 1500, 2000, 2500, 3000, 4000, 7500};

        for (int i = 0; i < rewards.length; i++) {
            Label rewardLabel = new Label(
                "Day " + (i + 1) + "    $" + formatCash(rewards[i]),
                skin
            );

            rewardLabel.setColor(i == rewards.length - 1 ? Color.GOLD : Color.WHITE);
            rewardsPanel.add(rewardLabel).width(360).left().row();
        }

        dialog.getContentTable().add(rewardsPanel).width(430).padBottom(18).row();

        TextButton claimButton = new TextButton("Claim Today", popupButtonStyle);
        TextButton closeButton = new TextButton("Close", popupButtonStyle);

        Table buttonRow = new Table();
        buttonRow.add(claimButton).width(190).height(48).padRight(12);
        buttonRow.add(closeButton).width(130).height(48);

        dialog.getContentTable().add(buttonRow).row();

        Label feedbackLabel = new Label("", skin);
        feedbackLabel.setWrap(true);
        feedbackLabel.setAlignment(Align.center);
        feedbackLabel.setColor(Color.LIGHT_GRAY);

        dialog.getContentTable().add(feedbackLabel).width(460).padTop(14).row();

        claimButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                claimButton.setDisabled(true);
                feedbackLabel.setColor(Color.LIGHT_GRAY);
                feedbackLabel.setText("Checking daily bonus...");

                SupabaseGameData.claimDailyLogin(result -> {
                    if (result.claimed) {
                        feedbackLabel.setColor(Color.GREEN);
                        feedbackLabel.setText(
                            "Claimed $" + formatCash(result.reward) + "!\n" +
                                "Current streak: " + result.streak + " day(s).\n" +
                                "New cash balance: $" + formatCash(result.cash)
                        );
                    } else {
                        feedbackLabel.setColor(Color.GOLD);
                        feedbackLabel.setText(
                            "You already claimed today's reward.\n" +
                                "Come back tomorrow.\n" +
                                "Current streak: " + result.streak + " day(s)."
                        );
                    }
                }, error -> {
                    claimButton.setDisabled(false);
                    feedbackLabel.setColor(Color.RED);
                    feedbackLabel.setText("Could not claim daily bonus:\n" + error);
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

        float dialogWidth = Math.min(560f, stage.getWidth() - 80f);
        float dialogHeight = Math.min(620f, stage.getHeight() - 80f);

        dialog.setSize(dialogWidth, dialogHeight);
        dialog.setPosition(
            (stage.getWidth() - dialogWidth) / 2f,
            (stage.getHeight() - dialogHeight) / 2f
        );
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

    private TextButton.TextButtonStyle createModernPopupButtonStyle() {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();

        style.up = skin.newDrawable(
            "default-round",
            new Color(0.16f, 0.16f, 0.20f, 1f)
        );

        style.down = skin.newDrawable(
            "default-round",
            new Color(0.08f, 0.08f, 0.11f, 1f)
        );

        style.over = skin.newDrawable(
            "default-round",
            new Color(0.24f, 0.24f, 0.30f, 1f)
        );

        style.font = skin.getFont("default-font");
        style.fontColor = Color.WHITE;
        style.overFontColor = Color.WHITE;
        style.downFontColor = Color.LIGHT_GRAY;

        return style;
    }

    private Drawable darkPanelDrawable() {
        return skin.newDrawable(
            "default-round",
            new Color(0.09f, 0.09f, 0.12f, 0.96f)
        );
    }

    private Drawable darkQuestCardDrawable(boolean completed) {
        if (completed) {
            return skin.newDrawable(
                "default-round",
                new Color(0.07f, 0.16f, 0.10f, 0.96f)
            );
        }

        return skin.newDrawable(
            "default-round",
            new Color(0.10f, 0.10f, 0.14f, 0.96f)
        );
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
