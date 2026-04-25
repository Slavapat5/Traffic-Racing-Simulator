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
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputListener;

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

    // Class label in info panel
    private Label classLabel;

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

    /**
     * Server-authoritative purchase via Edge Function.
     * - Prevents client-side cash cheating
     * - Prevents duplicate ownership server-side
     */
    public void tryBuyCarSecure(CarData car, Runnable onDoneUI) {

        if (CarOwnershipManager.ownsCar(car.image)) {
            Dialog d = new Dialog("Already Owned", skin);
            d.text("You already own this car.");
            d.button("OK");
            d.show(stage);
            return;
        }

        if (!SupabaseAuth.isLoggedIn) {
            Dialog d = new Dialog("Login Required", skin);
            d.text("Please log in to buy cars (cloud save).");
            d.button("OK");
            d.show(stage);
            return;
        }

        // Disable input while request runs (prevents double-buy)
        stage.getRoot().setTouchable(Touchable.disabled);

        // Important - SupabaseGameData.purchaseCar signature should be: purchaseCar(String carImage, int price, Consumer<Integer> onSuccessCash, Consumer<String> onFail)
        SupabaseGameData.purchaseCar(
            car.image,
            car.price,
            newCash -> {
                // Update local cash immediately if server returned it
                if (newCash >= 0) {
                    CashManager.setCash(newCash);
                    CashManager.saveCash();
                    if (cashLabel != null) {
                        cashLabel.setText("$" + formatCash(CashManager.getCash()));
                    }
                }

                // Re-pull owned cars from cloud so UI reflects server truth immediately
                SupabaseGameData.loadOwnedCars(
                    SupabaseAuth.userId,
                    SupabaseAuth.accessToken,
                    () -> {
                        Dialog success = new Dialog("Purchase Complete", skin);
                        success.text("You bought " + car.title + " for $" + String.format("%,d", car.price));
                        success.button("OK");
                        success.show(stage);

                        stage.getRoot().setTouchable(Touchable.enabled);

                        if (onDoneUI != null) onDoneUI.run();
                    }
                );
            },
            err -> {
                stage.getRoot().setTouchable(Touchable.enabled);

                String msg;
                switch (err) {
                    case "insufficient_funds": msg = "You don't have enough cash."; break;
                    case "already_owned": msg = "You already own this car."; break;
                    case "profile_not_found": msg = "Profile not found. Try re-logging."; break;
                    case "invalid_price": msg = "Invalid price (client/server mismatch)."; break;
                    case "not_logged_in": msg = "Please log in again."; break;
                    default: msg = "Purchase failed: " + err; break;
                }

                Dialog fail = new Dialog("Purchase Failed", skin);
                fail.text(msg);
                fail.button("OK");
                fail.show(stage);
            }
        );
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        skin = new Skin(Gdx.files.internal("uiskin.json"));

        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        root.setBackground(new TextureRegionDrawable(new TextureRegion(
            new Texture(Gdx.files.internal("Default_Background.png"))
        )));

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
        scrollPane.setScrollingDisabled(false, true); // horizontal only
        scrollPane.setSmoothScrolling(true);
        scrollPane.setFlingTime(0.2f);
        scrollPane.setScrollbarsOnTop(false);

        this.scrollPane = scrollPane;

        scrollPane.addListener(new InputListener() {
            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                snapping = true;
            }
        });

        Texture leftTex = new Texture(Gdx.files.internal("Arrow2_V2.png"));
        Texture rightTex = new Texture(Gdx.files.internal("Arrow1_V2.png"));

        ImageButton leftArrow = new ImageButton(new TextureRegionDrawable(new TextureRegion(leftTex)));
        ImageButton rightArrow = new ImageButton(new TextureRegionDrawable(new TextureRegion(rightTex)));

        leftArrow.getImage().setScaling(Scaling.fit);
        rightArrow.getImage().setScaling(Scaling.fit);

        leftArrow.setSize(64, 64);
        rightArrow.setSize(64, 64);

        Table dealershipRow = new Table();
        dealershipRow.add(leftArrow).padRight(10).width(60).expandY().center();
        Table carouselContainer = new Table();
        carouselContainer.setBackground(new TextureRegionDrawable(
            new TextureRegion(new Texture(Gdx.files.internal("carousel_bg.png")))
        ));
        carouselContainer.pad(20);

        carouselContainer.add(scrollPane).expand().fill();

        dealershipRow.add(carouselContainer).expand().fill();
        dealershipRow.add(rightArrow).padLeft(10).width(60).expandY().center();

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

        // --- Info panel ---
        infoPanel = new Table(skin);
        infoPanel.setBackground("default-round");
        infoPanel.setColor(0f, 0f, 0f, 1f);
        infoPanel.pad(20);
        infoPanel.defaults().space(10);

        historyLabel = new Label("Select a car to see its history.", skin);
        historyLabel.setWrap(true);
        historyLabel.setAlignment(Align.left);
        historyLabel.setColor(Color.LIGHT_GRAY);

        selectedCarLabel = new Label("Select a car", skin);
        selectedCarLabel.setFontScale(1.5f);
        selectedCarLabel.setAlignment(Align.left);

        selectedPriceLabel = new Label("", skin);
        selectedPriceLabel.setFontScale(1.2f);
        selectedPriceLabel.setAlignment(Align.left);

        classLabel = new Label("", skin);
        classLabel.setFontScale(1.1f);
        classLabel.setAlignment(Align.left);
        classLabel.setColor(Color.GOLD);

        horsepowerLabel = new Label("", skin);
        weightLabel = new Label("", skin);
        engineLabel = new Label("", skin);

        carPreviewImage = new Image();
        carPreviewImage.setScaling(Scaling.fit);
        carPreviewImage.setSize(300, 300);

        carPreviewImage = new Image();
        carPreviewImage.setScaling(Scaling.fit);

        Table statsTable = new Table();
        statsTable.defaults().left().padBottom(6);

        statsTable.add(selectedCarLabel).left().row();
        statsTable.add(selectedPriceLabel).left().row();
        statsTable.add(classLabel).left().row();
        statsTable.add(horsepowerLabel).left().row();
        statsTable.add(weightLabel).left().row();
        statsTable.add(engineLabel).left().row();

        Table infoContent = new Table();
        infoContent.defaults().pad(10);

// left = image
        infoContent.add(carPreviewImage)
            .size(320, 180)
            .left()
            .top()
            .padRight(20);

// right = stats
        infoContent.add(statsTable)
            .expandX()
            .fillX()
            .top()
            .left()
            .row();

// bottom = history across both columns
        infoContent.add(historyLabel)
            .colspan(2)
            .expandX()
            .fillX()
            .left()
            .top()
            .padTop(10)
            .width(700)
            .row();



        infoPanel.add(infoContent).expand().center();

        // Top: arrows + scrollable dealership area
        root.add(dealershipRow).expand().fill().row();
        // Bottom: info panel
        root.add(infoPanel).height(320).expandX().fillX().padTop(10);

        // Keyboard input
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
                    CarData centeredCar = getCenteredCar();
                    if (centeredCar == null) return true;

                    showPurchaseConfirmDialog(centeredCar);
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
        topBar.top().pad(10);
        topBar.setFillParent(true);
        topBar.add(cashContainer).expandX().left().padLeft(75);


        ImageButton backButton = new ImageButton(UIStyles.getBackButtonStyle());
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });

        topBar.add(backButton).right().width(80).height(30).padRight(75);
        stage.addActor(topBar);

        if (carCards.size > 0) {
            currentCenterIndex = 0;
            updateCenterHighlight();
            snapping = true;
        }
    }

    private void showPurchaseConfirmDialog(CarData car) {
        if (car == null) return;

        if (CarOwnershipManager.ownsCar(car.image)) {
            Dialog ownedDialog = new Dialog("Already Owned", skin);
            ownedDialog.text("You already own this car.");
            ownedDialog.button("OK");
            ownedDialog.show(stage);
            return;
        }

        Dialog confirmDialog = new Dialog("Confirm Purchase", skin) {
            @Override
            protected void result(Object object) {
                boolean yes = (Boolean) object;
                if (yes) {
                    tryBuyCarSecure(car, () -> game.setScreen(new DealershipScreen(game)));
                }
            }
        };

        confirmDialog.text(
            "Buy " + car.title + " for $" + String.format("%,d", car.price) + "?"
        );
        confirmDialog.button("Yes", true);
        confirmDialog.button("No", false);
        confirmDialog.show(stage);
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

        CarStats stats = CarRegistry.getStats(car.image);
        if (stats != null && stats.carClass != null) {
            Label classOnCard = new Label(stats.carClass.name() + " " + stats.pi, skin);
            classOnCard.setColor(getClassColor(stats.carClass));
            carBox.add(classOnCard).row();
        }

        Label descriptionLabel = new Label(car.description, skin);
        descriptionLabel.setColor(Color.LIGHT_GRAY);
        descriptionLabel.setAlignment(Align.center);
        descriptionLabel.setWrap(true);
        carBox.add(descriptionLabel).width(400).row();

        String formattedPrice = String.format("%,d", car.price);
        Label priceLabel = new Label("$" + formattedPrice, skin);
        priceLabel.setColor(Color.GREEN);
        priceLabel.setFontScale(1.2f);
        priceLabel.setAlignment(Align.left);
        carBox.add(priceLabel).row();

        if (!CarOwnershipManager.ownsCar(car.image)) {
            TextButton buyButton = new TextButton("Buy", skin);
            buyButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    showPurchaseConfirmDialog(car);
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

        CarStats stats = CarRegistry.getStats(car.image);
        if (stats != null && stats.carClass != null) {
            classLabel.setText("Class: " + stats.carClass.name() + "  (PI " + stats.pi + ")");
            classLabel.setColor(getClassColor(stats.carClass));
        } else {
            classLabel.setText("Class: N/A");
            classLabel.setColor(Color.LIGHT_GRAY);
        }
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

    private Color getClassColor(CarClass carClass) {
        if (carClass == null) return Color.LIGHT_GRAY;

        switch (carClass) {
            case D:  return Color.RED;
            case C:  return Color.ORANGE;
            case B:  return Color.YELLOW;
            case A:  return Color.GREEN;
            case S1: return Color.CYAN;
            case S2: return Color.BLUE;
            case X:  return new Color(0.65f, 0.20f, 0.95f, 1f);
            default: return Color.LIGHT_GRAY;
        }
    }

    private CarData getCenteredCar() {
        if (carCards.size == 0) return null;

        int index = currentCenterIndex;

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
        Gdx.gl.glClearColor(0, 0, 0, 1);
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
    }
}
