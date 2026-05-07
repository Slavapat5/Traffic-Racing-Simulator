package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class SeasonSelectorScreen implements Screen {

    private final Game game;
    private final String modeKey;
    private final String locationName;

    private Stage stage;
    private Skin skin;
    private Label cashLabel;
    private Texture bgTexture;
    private Image bgImage;

    private Texture defaultButtonUpTexture;
    private Texture defaultButtonDownTexture;
    private Texture defaultButtonOverTexture;
    private TextButton.TextButtonStyle defaultButtonStyle;



    private static final float SEASON_CARD_SIZE = 512f;

    public SeasonSelectorScreen(Game game, String modeKey, String locationName) {
        this.game = game;
        this.modeKey = modeKey;
        this.locationName = locationName;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        skin = new Skin(Gdx.files.internal("uiskin.json"));
        defaultButtonStyle = createDefaultButtonStyle();


        setupBackground();

        Table root = new Table();
        root.setFillParent(true);
        root.top().padTop(70).padLeft(40).padRight(40).padBottom(100);
        stage.addActor(root);

        Label titleLabel = new Label("Select Season", skin);
        titleLabel.setAlignment(Align.center);
        titleLabel.setFontScale(1.5f);

        Label subtitleLabel = new Label(
            "Mode: " + prettifyModeName(modeKey) + "   |   Location: " + locationName,
            skin
        );
        subtitleLabel.setAlignment(Align.center);
        subtitleLabel.setColor(Color.LIGHT_GRAY);

        Table titleTable = new Table();
        titleTable.add(titleLabel).row();
        titleTable.add(subtitleLabel).padTop(4);

        root.add(titleTable).expandX().center().padBottom(25).row();

        Table seasonListTable = new Table();
        seasonListTable.pad(20);
        seasonListTable.defaults().space(40);
        seasonListTable.left();

        addSeasonCard(
            seasonListTable,
            "Spring",
            "Fresh green scenery and mild weather.",
            "season_spring.png",
            new Color(0.45f, 0.70f, 0.35f, 1f)
        );

        addSeasonCard(
            seasonListTable,
            "Summer",
            "Bright skies and vivid warm colours.",
            "season_summer.png",
            new Color(0.85f, 0.75f, 0.25f, 1f)
        );

        addSeasonCard(
            seasonListTable,
            "Autumn",
            "Orange and brown tones with a cooler feel.",
            "season_autumn.png",
            new Color(0.75f, 0.40f, 0.15f, 1f)
        );

        addSeasonCard(
            seasonListTable,
            "Winter",
            "Cold icy tone with snow-style colouring.",
            "season_winter.png",
            new Color(0.70f, 0.85f, 0.95f, 1f)
        );

        ScrollPane scrollPane = new ScrollPane(seasonListTable, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(false, true);
        scrollPane.setSmoothScrolling(true);
        scrollPane.setFlingTime(0.2f);
        scrollPane.setScrollbarsOnTop(false);

        ScrollPane.ScrollPaneStyle spStyle = new ScrollPane.ScrollPaneStyle(scrollPane.getStyle());
        spStyle.background = null;
        spStyle.hScroll = null;
        spStyle.hScrollKnob = null;
        scrollPane.setStyle(spStyle);

        root.add(scrollPane).expand().fill().row();

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

        ImageButton backButton = new ImageButton(UIStyles.getBackButtonStyle());
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new LocationSelectorScreen(game, modeKey));
            }
        });

        Table bottomRight = new Table();
        bottomRight.setFillParent(true);
        bottomRight.bottom().right().padRight(20).padBottom(18);
        bottomRight.add(backButton).width(80).height(30);
        stage.addActor(bottomRight);

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACKSPACE) {
                    game.setScreen(new LocationSelectorScreen(game, modeKey));
                    return true;
                }
                return false;
            }
        });
    }

    private void addSeasonCard(Table parent,
                               String seasonName,
                               String description,
                               String previewImagePath,
                               Color fallbackColor) {

        Table card = new Table(skin);
        card.setBackground(skin.newDrawable("default-round", new Color(0.1f, 0.1f, 0.1f, 0.92f)));
        card.pad(18, 18, 24, 18);

        Label nameLabel = new Label(seasonName, skin);
        nameLabel.setAlignment(Align.center);
        nameLabel.setFontScale(1.15f);

        Actor previewActor = createPreviewActor(previewImagePath, fallbackColor, seasonName);

        Label descLabel = new Label(description, skin);
        descLabel.setWrap(true);
        descLabel.setAlignment(Align.center);
        descLabel.setColor(Color.LIGHT_GRAY);

        TextButton selectButton = new TextButton("Select", defaultButtonStyle);
        selectButton.getLabel().setAlignment(Align.center);
        selectButton.getLabel().setFontScale(1.0f);
        selectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onSeasonSelected(seasonName);
            }
        });

        card.add(nameLabel).width(520).center().padBottom(10).row();
        card.add(previewActor).size(SEASON_CARD_SIZE, SEASON_CARD_SIZE).center().padBottom(12).row();
        card.add(descLabel).width(500).center().padBottom(12).row();
        card.add(selectButton).width(180).height(56).center().row();

        card.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onSeasonSelected(seasonName);
            }
        });

        parent.add(card).width(560).pad(20);
    }

    private Actor createPreviewActor(String imagePath, Color fallbackColor, String labelText) {
        try {
            Texture texture = new Texture(Gdx.files.internal(imagePath));
            Image image = new Image(texture);
            image.setScaling(Scaling.fit);
            return image;
        } catch (Exception e) {
            Table placeholder = new Table();
            placeholder.setBackground(makeSolidDrawable(fallbackColor));

            Label label = new Label(labelText, skin);
            label.setFontScale(1.3f);
            label.setAlignment(Align.center);

            placeholder.add(label).expand().center();
            return placeholder;
        }
    }

    private void onSeasonSelected(String seasonName) {
        EnvironmentSelectionData.setSelectedSeason(seasonName);
        launchGameplayScreen();
    }

    private void launchGameplayScreen() {
        System.out.println("Mode = " + modeKey);
        System.out.println("Location = " + EnvironmentSelectionData.getSelectedLocation());
        System.out.println("Season = " + EnvironmentSelectionData.getSelectedSeason());

        switch (modeKey) {
            case "free_ride":
                game.setScreen(new FreeRideScreen(game));
                break;
            case "time_trial":
                game.setScreen(new TimeTrialScreen(game));
                break;
            case "endless_one_way":
                game.setScreen(new EndlessOneWayScreen(game));
                break;
            case "endless_two_way":
                game.setScreen(new EndlessTwoWayScreen(game));
                break;
            case "test_drive":
                game.setScreen(new TestDriveScreen(game));
                break;
            default:
                game.setScreen(new GameModeSelectorScreen(game));
                break;
        }
    }

    private void setupBackground() {
        try {
            bgTexture = new Texture(Gdx.files.internal("login_bg.png"));
            bgImage = new Image(bgTexture);
            bgImage.setFillParent(true);
            stage.addActor(bgImage);
        } catch (Exception e) {
            bgTexture = null;
        }
    }

    private Drawable makeSolidDrawable(Color color) {
        Pixmap pixmap = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();

        Texture texture = new Texture(pixmap);
        pixmap.dispose();

        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    private String prettifyModeName(String key) {
        switch (key) {
            case "free_ride": return "Free Ride";
            case "time_trial": return "Time Trial";
            case "endless_one_way": return "Endless One Way";
            case "endless_two_way": return "Endless Two Way";
            case "drag_sprint": return "Drag Sprint";
            case "test_drive": return "Test Drive";
            default: return key;
        }
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
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        cashLabel.setText("$" + formatCash(CashManager.getCash()));
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();
        if (bgTexture != null) bgTexture.dispose();

        if (defaultButtonUpTexture != null) defaultButtonUpTexture.dispose();
        if (defaultButtonDownTexture != null) defaultButtonDownTexture.dispose();
        if (defaultButtonOverTexture != null) defaultButtonOverTexture.dispose();
    }
}
