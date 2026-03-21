package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
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
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.Array;

import java.util.ArrayList;

public class GarageScreen implements Screen {

    private final Game game;
    private Stage stage;
    private Skin skin;

    private Table infoPanel;
    private Label selectedCarLabel;
    private Label selectedPriceLabel;

    // NEW: Class/PI label (info panel)
    private Label classLabel;

    private Label horsepowerLabel;
    private Label weightLabel;
    private Label engineLabel;
    private Label historyLabel;
    private Label cashLabel;
    private Image carPreviewImage;

    private ScrollPane scrollPane;
    private final Array<Table> carCards = new Array<>();

    private int currentCenterIndex = -1;
    private boolean snapping = false;
    private final float snapSpeed = 7f;

    private static final float CAR_BOX_WIDTH = 420f;
    private static final float CAR_BOX_PAD = 20f;

    public GarageScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        skin = new Skin(Gdx.files.internal("uiskin.json"));

        // Root layout
        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        // Load data
        CarOwnershipManager.loadOwnedCars();
        CarDataBase.load();

        // === CAR LIST (HORIZONTAL) ===
        Table carListTable = new Table();
        carListTable.pad(10);
        carListTable.defaults().space(30);
        carListTable.left();

        for (String imagePath : CarOwnershipManager.getOwnedCars()) {
            CarData car = CarDataBase.getCarByImage(imagePath);
            if (car != null) {
                addCarCard(carListTable, car);
            } else {
                System.err.println("Missing car data for owned car: " + imagePath);
            }
        }

        scrollPane = new ScrollPane(carListTable, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(false, true); // horizontal only
        scrollPane.setSmoothScrolling(true);
        scrollPane.setFlingTime(0.2f);
        scrollPane.setScrollbarsOnTop(false);

        scrollPane.addListener(new InputListener() {
            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                snapping = true;    // begin snapping when user releases scroll
            }
        });

        // === ARROWS + CAROUSEL ROW ===
        Texture leftTex = new Texture(Gdx.files.internal("Arrow2.png"));
        Texture rightTex = new Texture(Gdx.files.internal("Arrow1.png"));

        ImageButton leftArrow = new ImageButton(new TextureRegionDrawable(new TextureRegion(leftTex)));
        ImageButton rightArrow = new ImageButton(new TextureRegionDrawable(new TextureRegion(rightTex)));

        leftArrow.getImage().setScaling(Scaling.fit);
        rightArrow.getImage().setScaling(Scaling.fit);

        leftArrow.setSize(64, 64);
        rightArrow.setSize(64, 64);

        Table garageRow = new Table();
        garageRow.add(leftArrow).padRight(10).width(60).expandY().center();
        garageRow.add(scrollPane).expand().fill();
        garageRow.add(rightArrow).padLeft(10).width(60).expandY().center();

        // TOP: arrows + scrollable ownership area
        root.add(garageRow).expand().fill().row();

        // === INFO PANEL (BOTTOM) ===
        infoPanel = new Table(skin);
        infoPanel.setBackground("default-round");
        infoPanel.setColor(0.1f, 0.1f, 0.1f, 1f);
        infoPanel.pad(20);
        infoPanel.defaults().space(10);

        carPreviewImage = new Image();
        carPreviewImage.setScaling(Scaling.fit);
        carPreviewImage.setSize(300, 150);

        selectedCarLabel = new Label("Select a car", skin);
        selectedCarLabel.setFontScale(1.4f);
        selectedCarLabel.setAlignment(Align.center);

        selectedPriceLabel = new Label("", skin);
        selectedPriceLabel.setFontScale(1.2f);
        selectedPriceLabel.setAlignment(Align.center);

        //  class label in info panel
        classLabel = new Label("", skin);
        classLabel.setFontScale(1.1f);
        classLabel.setAlignment(Align.center);
        classLabel.setColor(Color.LIGHT_GRAY);

        horsepowerLabel = new Label("", skin);
        weightLabel = new Label("", skin);
        engineLabel = new Label("", skin);

        historyLabel = new Label("", skin);
        historyLabel.setWrap(true);
        historyLabel.setAlignment(Align.topLeft);
        historyLabel.setColor(Color.LIGHT_GRAY);

        Table infoContent = new Table();
        infoContent.add(carPreviewImage).size(300, 150).row();
        infoContent.add(selectedCarLabel).center().row();
        infoContent.add(selectedPriceLabel).center().row();

        //  show class/PI right under price
        infoContent.add(classLabel).center().row();

        infoContent.add(horsepowerLabel).center().row();
        infoContent.add(weightLabel).center().row();
        infoContent.add(engineLabel).center().row();
        infoContent.add(historyLabel).width(300).padTop(10).colspan(2).row();

        infoPanel.add(infoContent).expand().center();

        // BOTTOM: info panel full width
        root.add(infoPanel).height(300).expandX().fillX().padTop(10);

        // === TOP BAR (CASH + BACK) ===
        cashLabel = new Label("$" + formatCash(CashManager.getCash()), skin);
        cashLabel.setFontScale(1.2f);
        cashLabel.setAlignment(Align.center);

        Container<Label> cashContainer = new Container<>(cashLabel);
        cashContainer.setBackground(createCashLabelBackground());
        cashContainer.setColor(Color.BLACK);

        Table topBar = new Table();
        topBar.top().left().pad(10);
        topBar.setFillParent(true);
        topBar.add(cashContainer).left();

        ImageButton backButton = new ImageButton(UIStyles.getBackButtonStyle());
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });

        topBar.add(backButton).right().width(80).height(30);
        stage.addActor(topBar);

        // ARROW BUTTONS USE SAME LOGIC AS KEYBOARD
        leftArrow.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                moveSelection(-1);
            }
        });

        rightArrow.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                moveSelection(1);
            }
        });

        // KEYBOARD INPUT
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.LEFT || keycode == Input.Keys.A) {
                    moveSelection(-1);
                    return true;
                }
                if (keycode == Input.Keys.RIGHT || keycode == Input.Keys.D) {
                    moveSelection(1);
                    return true;
                }
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
                    if (currentCenterIndex >= 0 && currentCenterIndex < carCards.size) {
                        Table card = carCards.get(currentCenterIndex);
                        Object obj = card.getUserObject();
                        if (obj instanceof CarData) {
                            CarData car = (CarData) obj;

                            CarSelectionData.setSelectedCarTexture(car.image);
                            if (game instanceof Main) {
                                ((Main) game).selectedCarName = car.title;
                            }

                            showCarInfo(car);
                            game.setScreen(new GameModeSelectorScreen(game));
                        }
                    }
                    return true;
                }
                return false;
            }
        });

        if (carCards.size > 0) {
            currentCenterIndex = 0;
            updateCenterHighlight();
            snapping = scrollPane.getMaxX() > 0;
        }
    }

    // === ADD ONE CAR CARD TO HORIZONTAL ROW ===
    private void addCarCard(Table parent, CarData car) {
        Table carBox = new Table(skin);
        carBox.setBackground("default-round");
        carBox.setColor(0.1f, 0.1f, 0.1f, 0.85f);
        carBox.pad(10).defaults().space(10);
        carBox.setTransform(true);
        carBox.setUserObject(car);

        // Image
        try {
            Texture carTexture = new Texture(Gdx.files.internal(car.image));
            Image carImage = new Image(carTexture);
            carImage.setScaling(Scaling.fit);
            carBox.add(carImage).size(300, 150).row();
        } catch (Exception e) {
            System.err.println("Failed to load owned car image: " + car.image);
            e.printStackTrace();
        }

        // Title
        Label nameLabel = new Label(car.title, skin);
        nameLabel.setFontScale(1.2f);
        carBox.add(nameLabel).row();

        // NEW: Class/PI line on the card itself
        CarStats stats = CarRegistry.getStats(car.image);
        String classText = (stats != null && stats.carClass != null)
            ? (stats.carClass.name() + " " + stats.pi)
            : "Class: ?";

        Label classPiLabel = new Label("Class: " + classText, skin);

        if (stats != null) {
            classPiLabel.setColor(getClassColor(stats.carClass));
        } else {
            classPiLabel.setColor(Color.LIGHT_GRAY);
        }

        carBox.add(classPiLabel).row();



        // Description
        Label descriptionLabel = new Label(car.description, skin);
        descriptionLabel.setColor(Color.LIGHT_GRAY);
        descriptionLabel.setAlignment(Align.center);
        descriptionLabel.setWrap(true);
        carBox.add(descriptionLabel).width(400).row();

        // Select button
        TextButton selectButton = new TextButton("Select", skin);
        selectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                CarSelectionData.setSelectedCarTexture(car.image);
                System.out.println("Selected car set to: " + car.image);
                System.out.println("Selected car: " + car.title);

                if (game instanceof Main) {
                    ((Main) game).selectedCarName = car.title;
                }

                showCarInfo(car);
                game.setScreen(new GameModeSelectorScreen(game));
            }
        });
        carBox.add(selectButton).padTop(10).row();

        // SELL button
        TextButton sellButton = new TextButton("Sell", skin);
        sellButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {

                String selectedTexture = CarSelectionData.getSelectedCarTexture();
                if (selectedTexture != null && selectedTexture.equals(car.image)) {
                    Dialog warningDialog = new Dialog("Can't Sell Selected Car", skin) { };
                    warningDialog.text("You can't sell the car you're currently using.");
                    warningDialog.button("OK");
                    warningDialog.show(stage);
                    return;
                }

                CarData carData = CarDataBase.getCarByImage(car.image);
                if (carData != null) {
                    int refund = (int) (carData.price * 0.75f);

                    CarOwnershipManager.removeCar(car.image);
                    CashManager.addCash(refund);

                    System.out.println("Sold car: " + car.title + " for $" + refund);

                    // If no cars left, assign default
                    if (CarOwnershipManager.getOwnedCars().isEmpty()) {
                        String defaultImage = "Mazda MX-5 Miata - 2014.png";
                        SelectedCar.set(defaultImage);
                        CarOwnershipManager.addCar(defaultImage);
                        CarOwnershipManager.saveOwnedCars();
                    } else {
                        if (SelectedCar.get().equals(car.image)) {
                            String firstOwned = new ArrayList<>(CarOwnershipManager.getOwnedCars()).get(0);
                            SelectedCar.set(firstOwned);
                            CarSelectionData.setSelectedCarTexture(firstOwned);
                        }
                    }

                    game.setScreen(new GarageScreen(game));
                } else {
                    System.err.println("Couldn't find car data for selling: " + car.image);
                }
            }
        });
        carBox.add(sellButton).padTop(10).row();

        // Whole card clickable just to update info panel
        carBox.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showCarInfo(car);
            }
        });

        parent.add(carBox).width(CAR_BOX_WIDTH).pad(CAR_BOX_PAD);
        carCards.add(carBox);
    }

    private void showCarInfo(CarData car) {
        try {
            Texture carTexture = new Texture(Gdx.files.internal(car.image));
            carPreviewImage.setDrawable(new TextureRegionDrawable(new TextureRegion(carTexture)));
        } catch (Exception e) {
            System.err.println("Failed to load preview image in garage: " + car.image);
        }

        selectedCarLabel.setText(car.title);
        selectedPriceLabel.setText("$" + String.format("%,d", car.price));
        horsepowerLabel.setText("Horsepower: " + car.horsepower);
        weightLabel.setText("Weight: " + car.weightKg + " kg");
        engineLabel.setText("Engine: " + car.engine);
        historyLabel.setText(car.longDescription);

        // set class label here too
        CarStats stats = CarRegistry.getStats(car.image);
        if (stats != null && stats.carClass != null) {
            classLabel.setText("Class: " + stats.carClass.name() + " " + stats.pi);
            classLabel.setColor(getClassColor(stats.carClass));
        } else {
            classLabel.setText("Class: ?");
            classLabel.setColor(Color.LIGHT_GRAY);
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

    private void moveSelection(int direction) {
        if (carCards.size == 0) return;

        if (currentCenterIndex < 0) currentCenterIndex = 0;

        int targetIndex = currentCenterIndex + direction;
        if (targetIndex < 0) targetIndex = 0;
        if (targetIndex > carCards.size - 1) targetIndex = carCards.size - 1;

        if (targetIndex == currentCenterIndex) return;

        currentCenterIndex = targetIndex;

        float maxX = scrollPane.getMaxX();
        if (maxX > 0 && carCards.size > 1) {
            float pageWidth = maxX / (carCards.size - 1);
            float targetX = currentCenterIndex * pageWidth;
            scrollPane.setScrollX(targetX);
        }

        updateCenterHighlight();
        snapping = false;
    }

    private void snapToClosestCar(float delta) {
        if (carCards.size == 0) return;

        float scrollX = scrollPane.getScrollX();
        float maxX = scrollPane.getMaxX();

        if (maxX <= 0 || carCards.size == 1) {
            if (currentCenterIndex != 0) {
                currentCenterIndex = 0;
                updateCenterHighlight();
            }
            snapping = false;
            return;
        }

        float pageWidth = maxX / (carCards.size - 1);

        float rawIndex = scrollX / pageWidth;
        int nearestIndex = Math.round(rawIndex);
        if (nearestIndex < 0) nearestIndex = 0;
        if (nearestIndex > carCards.size - 1) nearestIndex = carCards.size - 1;

        float targetX = nearestIndex * pageWidth;

        float newScroll = scrollX + (targetX - scrollX) * snapSpeed * delta;
        scrollPane.setScrollX(newScroll);

        if (currentCenterIndex != nearestIndex) {
            currentCenterIndex = nearestIndex;
            updateCenterHighlight();
        }

        if (Math.abs(newScroll - targetX) < 1f) {
            scrollPane.setScrollX(targetX);
            snapping = false;
        }
    }

    private Color getClassColor(CarClass carClass) {
        if (carClass == null) return Color.LIGHT_GRAY;

        switch (carClass) {
            case D:  return Color.RED;                         // D = red
            case C:  return Color.ORANGE;                      // C = orange
            case B:  return Color.YELLOW;                      // B = yellow
            case A:  return Color.GREEN;                       // A = green
            case S1: return Color.CYAN;                        // S1 = cyan
            case S2: return Color.BLUE;                         // S2 = blue
            case X:  return new Color(0.65f, 0.20f, 0.95f, 1f); // X = purple
            default: return Color.LIGHT_GRAY;
        }
    }


    private void updateCenterHighlight() {
        for (int i = 0; i < carCards.size; i++) {
            Table card = carCards.get(i);

            if (i == currentCenterIndex) {
                card.setScale(1.1f);
                card.setColor(0.25f, 0.25f, 0.25f, 1f);

                Object obj = card.getUserObject();
                if (obj instanceof CarData) {
                    showCarInfo((CarData) obj);
                }
            } else {
                card.setScale(1f);
                card.setColor(0.1f, 0.1f, 0.1f, 0.85f);
            }
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);

        if (snapping) {
            snapToClosestCar(delta);
        }

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
        stage.dispose();
        skin.dispose();
    }
}
