package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.Array;

public class AchievementsScreen implements Screen {

    private final Game game;
    private Stage stage;
    private Skin skin;

    public AchievementsScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        Gdx.input.setInputProcessor(stage);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(30);
        stage.addActor(root);

        // Title
        Label title = new Label("Achievements", skin);
        title.setFontScale(2f);
        title.setAlignment(Align.center);
        root.add(title).padBottom(20).center().row();

        // Description
        Label subtitle = new Label(
            "Complete challenges while playing to unlock achievements.",
            skin
        );
        subtitle.setAlignment(Align.center);
        subtitle.setColor(Color.LIGHT_GRAY);
        subtitle.setWrap(true);
        root.add(subtitle).width(600).padBottom(20).center().row();

        // List table with rounded background
        Table listTable = new Table(skin);
        listTable.defaults().pad(8).left();
        listTable.setBackground("default-round");
        listTable.setColor(0.08f, 0.08f, 0.08f, 0.95f);

        ScrollPane scrollPane = new ScrollPane(listTable, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);

        root.add(scrollPane).expand().fill().padBottom(20).row();

        // Populate entries
        Array<AchievementsManager.AchievementState> all = AchievementsManager.getAll();
        for (AchievementsManager.AchievementState a : all) {
            boolean unlocked = a.unlocked;

            Label nameLabel = new Label(a.def.name, skin);
            Label descLabel = new Label(a.def.description, skin);
            descLabel.setWrap(true);
            descLabel.setAlignment(Align.left);

            Label statusLabel = new Label(unlocked ? "Unlocked" : "Locked", skin);
            statusLabel.setColor(unlocked ? Color.GREEN : Color.GRAY);

            if (!unlocked) {
                nameLabel.setColor(Color.LIGHT_GRAY);
                descLabel.setColor(Color.DARK_GRAY);
            }

            // === Card-style box per achievement ===
            Table card = new Table(skin);
            card.setBackground("default-round");
            card.setColor(0.15f, 0.15f, 0.15f, 0.95f);
            card.pad(10);
            card.defaults().left().padBottom(4);

            card.add(nameLabel).left().expandX().row();
            card.add(descLabel).left().width(500).row();
            card.add(statusLabel).left();

            // Add card to main list with spacing around it
            listTable.add(card).expandX().fillX().pad(6, 10, 6, 10).row();
        }

        if (all.size == 0) {
            Label none = new Label("No achievements defined yet.", skin);
            listTable.add(none).pad(10).row();
        }

        // Back button
        TextButton backButton = new TextButton("Back", skin);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                game.setScreen(new PlayScreen(game));
            }
        });

        root.add(backButton).left().width(150);
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
    }
}
