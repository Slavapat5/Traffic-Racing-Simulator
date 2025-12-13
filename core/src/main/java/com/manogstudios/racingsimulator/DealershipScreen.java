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
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class DealershipScreen implements Screen {
    private final Game game;
    private Stage stage;
    private Skin skin;
    private Table infoPanel;
    private Label selectedCarLabel;
    private Label selectedPriceLabel;
    public int horsepower;
    public int weightKg;
    public String engine;
    private Label horsepowerLabel;
    private Label weightLabel;
    private Label engineLabel;
    private Label historyLabel;
    private Label cashLabel;
    private Image carPreviewImage;
    private static final float CAR_BOX_WIDTH = 420f;
    private static final float CAR_BOX_PAD   = 20f;
    float PAGE_WIDTH = CAR_BOX_WIDTH + (CAR_BOX_PAD * 2);
    private final float snapSpeed = 7f;
    private boolean snapping = false;
    private ScrollPane scrollPane;
    private final com.badlogic.gdx.utils.Array<Table> carCards = new com.badlogic.gdx.utils.Array<>();
    private int currentCenterIndex = -1;
    private static final float CAR_SLOT_WIDTH = 420f;

    public DealershipScreen(Game game) {
        this.game = game;
    }

    public boolean tryBuyCar(CarData car) {
        if (CashManager.getCash() >= car.price) {
            CashManager.subtractCash(car.price);
            CarOwnershipManager.addCar(car.image);
            return true;
        }
        return false;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        skin = new Skin(Gdx.files.internal("uiskin.json"));

        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        CarOwnershipManager.loadOwnedCars();
        CarDataBase.load();

        Table carListTable = new Table();
        carListTable.pad(10);
        carListTable.defaults().space(30);
        carListTable.left();

        // Add all cars in one horizontal row
        for (CarData car : CarDataBase.getAllCars()) {
            addCarToList(carListTable, car);
        }

        ScrollPane scrollPane = new ScrollPane(carListTable, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(false, true); // horizontal scroll only
        scrollPane.setSmoothScrolling(true);
        scrollPane.setFlingTime(0.2f);
        scrollPane.setScrollbarsOnTop(false);

        this.scrollPane = scrollPane;

        scrollPane.addListener(new InputListener() {
            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                snapping = true;    // Begin snapping when user releases scroll
            }
        });

        Texture leftTex = new Texture(Gdx.files.internal("Arrow2.png"));
        Texture rightTex = new Texture(Gdx.files.internal("Arrow1.png"));

        ImageButton leftArrow = new ImageButton(new TextureRegionDrawable(new TextureRegion(leftTex)));
        ImageButton rightArrow = new ImageButton(new TextureRegionDrawable(new TextureRegion(rightTex)));

        leftArrow.getImage().setScaling(Scaling.fit);
        rightArrow.getImage().setScaling(Scaling.fit);

        leftArrow.setSize(64, 64);
        rightArrow.setSize(64, 64);

        Table dealershipRow = new Table();
        dealershipRow.add(leftArrow).padRight(10).width(60).expandY().center();
        dealershipRow.add(scrollPane).expand().fill();
        dealershipRow.add(rightArrow).padLeft(10).width(60).expandY().center();

        leftArrow.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                moveSelection(-1);  // one car to the left
            }
        });

        rightArrow.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                moveSelection(1);   // one car to the right
            }
        });

        // --- Info panel ---
        infoPanel = new Table(skin);
        infoPanel.setBackground("default-round");
        infoPanel.setColor(0f, 0f, 0f, 1f);
        infoPanel.pad(20);
        infoPanel.defaults().space(10);

        historyLabel = new Label("Select a car to see its history.", skin);
        historyLabel.setWrap(true);
        historyLabel.setAlignment(Align.topLeft);
        historyLabel.setColor(Color.LIGHT_GRAY);

        selectedCarLabel = new Label("Select a car", skin);
        selectedCarLabel.setFontScale(1.5f);
        selectedCarLabel.setAlignment(Align.center);

        selectedPriceLabel = new Label("", skin);
        selectedPriceLabel.setFontScale(1.2f);
        selectedPriceLabel.setAlignment(Align.center);

        horsepowerLabel = new Label("", skin);
        weightLabel = new Label("", skin);
        engineLabel = new Label("", skin);

        carPreviewImage = new Image();
        carPreviewImage.setScaling(Scaling.fit);
        carPreviewImage.setSize(300, 300);

        Table infoContent = new Table();
        infoContent.add(carPreviewImage).size(300, 150).row();
        infoContent.add(selectedCarLabel).center().row();
        infoContent.add(selectedPriceLabel).center().row();
        infoContent.add(horsepowerLabel).center().row();
        infoContent.add(weightLabel).center().row();
        infoContent.add(engineLabel).center().row();
        infoContent.add(historyLabel).width(300).padTop(10).colspan(2).row();

        infoPanel.add(infoContent).expand().center();

        // Top: arrows + scrollable dealership area
        root.add(dealershipRow).expand().fill().row();
        // Bottom: info panel (full width)
        root.add(infoPanel).height(300).expandX().fillX().padTop(10);

        // === Keyboard input: A/D + LEFT/RIGHT + ENTER to buy (NEW) ===
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

                // NEW: Press ENTER or SPACE to attempt a purchase
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
                    CarData centeredCar = getCenteredCar();
                    if (centeredCar == null) return true;

                    // If already owned, just tell the player
                    if (CarOwnershipManager.ownsCar(centeredCar.image)) {
                        Dialog ownedDialog = new Dialog("Already Owned", skin);
                        ownedDialog.text("You already own this car.");
                        ownedDialog.button("OK");
                        ownedDialog.show(stage);
                        return true;
                    }

                    // Confirm purchase dialog
                    Dialog confirmDialog = new Dialog("Confirm Purchase", skin) {
                        @Override
                        protected void result(Object object) {
                            boolean yes = (Boolean) object;
                            if (yes) {
                                if (tryBuyCar(centeredCar)) {
                                    Dialog success = new Dialog("Purchase Complete", skin);
                                    success.text("You bought " + centeredCar.title +
                                        " for $" + String.format("%,d", centeredCar.price));
                                    success.button("OK");
                                    success.show(stage);

                                    // Refresh screen so button becomes "Owned" and cash updates
                                    game.setScreen(new DealershipScreen(game));
                                } else {
                                    Dialog fail = new Dialog("Not Enough Cash", skin);
                                    fail.text("You don't have enough cash to buy this car.");
                                    fail.button("OK");
                                    fail.show(stage);
                                }
                            }
                        }
                    };

                    confirmDialog.text(
                        "Buy " + centeredCar.title + " for $" +
                            String.format("%,d", centeredCar.price) + "?"
                    );
                    confirmDialog.button("Yes", true);
                    confirmDialog.button("No", false);
                    confirmDialog.show(stage);
                    return true;
                }

                return false;
            }
        });

        // Cash label + top bar
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

        // Custom textured back button
        ImageButton backButton = new ImageButton(UIStyles.getBackButtonStyle());
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });

// You can control its size here if needed
        topBar.add(backButton).right().width(80).height(30);


        stage.addActor(topBar);

        if (carCards.size > 0) {
            snapping = true; // first frame will snap to the nearest center card
        }
    }

    private void addCarToList(Table parent, CarData car) {
        Table carBox = new Table(skin);
        carBox.setBackground("default-round");
        carBox.setColor(0.1f, 0.1f, 0.1f, 0.85f);
        carBox.pad(10).defaults().space(10);
        carBox.setTransform(true);
        carBox.setUserObject(car);

        carBox.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectCar(car);
            }
        });

        try {
            Texture carTexture = new Texture(Gdx.files.internal(car.image));
            Image carImage = new Image(carTexture);
            carImage.setScaling(Scaling.fit);
            carBox.add(carImage).size(300, 150).row();
        } catch (Exception e) {
            System.err.println("Failed to load image: " + car.image);
        }

        carBox.add(new Label(car.title, skin)).row();

        Label descriptionLabel = new Label(car.description, skin);
        descriptionLabel.setColor(Color.LIGHT_GRAY);
        descriptionLabel.setAlignment(Align.center);
        descriptionLabel.setWrap(true);
        carBox.add(descriptionLabel).width(400).row();

        String formattedPrice = String.format("%,d", car.price);
        Label priceLabel = new Label("$" + formattedPrice, skin);
        priceLabel.setColor(Color.GREEN);
        priceLabel.setFontScale(1.2f);
        priceLabel.setAlignment(Align.center);
        carBox.add(priceLabel).row();

        // Buy button if not owned (mouse click)
        if (!CarOwnershipManager.ownsCar(car.image)) {
            TextButton buyButton = new TextButton("Buy", skin);
            buyButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (tryBuyCar(car)) {
                        System.out.println("Bought " + car.title);
                        game.setScreen(new DealershipScreen(game)); // Refresh screen
                    } else {
                        System.out.println("Not enough cash to buy " + car.title);
                    }
                }
            });
            carBox.add(buyButton).padTop(10).row();
        } else {
            Label ownedLabel = new Label("Owned", skin);
            ownedLabel.setFontScale(1.1f);
            carBox.add(ownedLabel).padTop(10).row();
        }

        parent.add(carBox).width(CAR_SLOT_WIDTH).pad(20);
        carCards.add(carBox);
    }

    public String formatCash(int cash) {
        return String.format("%,d", cash);
    }

    private void selectCar(CarData car) {
        try {
            Texture newTexture = new Texture(Gdx.files.internal(car.image));
            carPreviewImage.setDrawable(new TextureRegionDrawable(new TextureRegion(newTexture)));
        } catch (Exception e) {
            System.err.println("Failed to load preview image: " + car.image);
        }

        selectedCarLabel.setText(car.title);
        selectedPriceLabel.setText("Price: $" + String.format("%,d", car.price));
        horsepowerLabel.setText("Power: " + car.horsepower + " hp");
        weightLabel.setText("Weight: " + car.weightKg + " kg");
        engineLabel.setText("Engine: " + car.engine);
        historyLabel.setText(car.longDescription);
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
        if (carCards.size <= 1) return;

        float maxX = scrollPane.getMaxX();
        if (maxX <= 0) return;

        float pageWidth = maxX / (carCards.size - 1);

        float scrollX = scrollPane.getScrollX();
        float currentIndex = scrollX / pageWidth;
        int targetIndex = Math.round(currentIndex) + direction;

        if (targetIndex < 0) targetIndex = 0;
        if (targetIndex > carCards.size - 1) targetIndex = carCards.size - 1;

        float target = targetIndex * pageWidth;
        scrollPane.setScrollX(target);
        snapping = true;
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

    private void updateCenterHighlight() {
        for (int i = 0; i < carCards.size; i++) {
            Table card = carCards.get(i);

            if (i == currentCenterIndex) {
                card.setScale(1.1f);
                card.setColor(0.25f, 0.25f, 0.25f, 1f);

                Object obj = card.getUserObject();
                if (obj instanceof CarData) {
                    selectCar((CarData) obj);
                }
            } else {
                card.setScale(1f);
                card.setColor(0.1f, 0.1f, 0.1f, 0.85f);
            }
        }
    }

    // helper to figure out which car is currently selected
    private CarData getCenteredCar() {
        if (carCards.size == 0) return null;

        int index = currentCenterIndex;

        // If theres no valid index yet, approximate from scroll position
        if (index < 0) {
            float maxX = scrollPane.getMaxX();
            if (maxX <= 0 || carCards.size == 1) {
                index = 0;
            } else {
                float pageWidth = maxX / (carCards.size - 1);
                float rawIndex = scrollPane.getScrollX() / pageWidth;
                index = Math.round(rawIndex);
                if (index < 0) index = 0;
                if (index > carCards.size - 1) index = carCards.size - 1;
            }
        }

        Table card = carCards.get(index);
        Object obj = card.getUserObject();
        if (obj instanceof CarData) {
            return (CarData) obj;
        }
        return null;
    }

    @Override
    public void render(float delta) {
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
    }
}
