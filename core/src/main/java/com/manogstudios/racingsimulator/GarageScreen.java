package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
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
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.manogstudios.racingsimulator.network.SupabaseGameData;
import java.util.HashMap;
import java.util.Map;

import java.util.ArrayList;

public class GarageScreen implements Screen {

    private final Game game;
    private Stage stage;
    private Skin skin;

    private Table infoPanel;
    private Label selectedCarLabel;
    private Label selectedPriceLabel;

    // Displays the selected car's class and PI rating in the info panel
    private Label classLabel;

    private Label horsepowerLabel;
    private Label weightLabel;
    private Label engineLabel;
    private Label historyLabel;
    private Label cashLabel;
    private Image carPreviewImage;

    private Texture buttonUpTexture;
    private Texture buttonDownTexture;
    private Texture buttonOverTexture;
    private Texture cashLabelTexture;

    private TextButton.TextButtonStyle defaultButtonStyle;

    private ScrollPane scrollPane;
    private final Array<Table> carCards = new Array<>();

    private int currentCenterIndex = -1;
    private boolean snapping = false;
    private final float snapSpeed = 7f;

    private static final float CAR_BOX_WIDTH = 420f;
    private static final float CAR_BOX_PAD = 20f;

    private Table paintTable;
    private final Map<String, Image> carCardImages = new HashMap<>();

    public GarageScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        skin = new Skin(Gdx.files.internal("uiskin.json"));

        defaultButtonStyle = createDefaultButtonStyle();

        // Root layout
        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        root.setBackground(new TextureRegionDrawable(new TextureRegion(
            new Texture(Gdx.files.internal("Default_Background.png"))
        )));

        // Load data
        CarOwnershipManager.loadOwnedCars();
        CarDataBase.load();
        CarPaintManager.load();

        // === CAR LIST (HORIZONTAL) ===
        Table carListTable = new Table();
        carListTable.pad(25, 10, 35, 10);
        carListTable.defaults().space(30);
        carListTable.left();

        java.util.HashSet<String> displayedCars = new java.util.HashSet<>();

        for (String imagePath : CarOwnershipManager.getOwnedCars()) {
            CarData car = CarDataBase.getCarByImage(imagePath);

            if (car != null) {
                // Deduplicate by the resolved/new car image, not the original saved filename.
                if (displayedCars.add(car.image)) {
                    addCarCard(carListTable, car);
                }
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
        Texture leftTex = new Texture(Gdx.files.internal("Arrow2_V2.png"));
        Texture rightTex = new Texture(Gdx.files.internal("Arrow1_V2.png"));

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
        selectedCarLabel.setAlignment(Align.left);

        selectedPriceLabel = new Label("", skin);
        selectedPriceLabel.setFontScale(1.2f);
        selectedPriceLabel.setAlignment(Align.left);

        //  class label in info panel
        classLabel = new Label("", skin);
        classLabel.setFontScale(1.1f);
        classLabel.setAlignment(Align.left);
        classLabel.setColor(Color.LIGHT_GRAY);

        horsepowerLabel = new Label("", skin);
        weightLabel = new Label("", skin);
        engineLabel = new Label("", skin);

        historyLabel = new Label("", skin);
        historyLabel.setWrap(true);
        historyLabel.setAlignment(Align.left);
        historyLabel.setColor(Color.LIGHT_GRAY);

        Table statsTable = new Table();
        statsTable.defaults().left().padBottom(6);

        statsTable.add(selectedCarLabel).left().row();
        statsTable.add(selectedPriceLabel).left().row();
        statsTable.add(classLabel).left().row();
        statsTable.add(horsepowerLabel).left().row();
        statsTable.add(weightLabel).left().row();
        statsTable.add(engineLabel).left().row();

        paintTable = new Table();
        paintTable.left();

        Table infoContent = new Table();


        // Left column - selected car preview
        infoContent.add(carPreviewImage)
            .size(320, 180)
            .left()
            .top()
            .padRight(20);

        // Right column - selected car stats
        infoContent.add(statsTable)
            .expandX()
            .fillX()
            .top()
            .left()
            .row();


        // Bottom rows - long description and paint options
        infoContent.add(historyLabel)
            .colspan(2)
            .expandX()
            .fillX()
            .left()
            .top()
            .padTop(10)
            .width(700)
            .row();

        // paint buttons under description
        infoContent.add(paintTable)
            .colspan(2)
            .expandX()
            .fillX()
            .left()
            .padTop(10)
            .row();

        infoPanel.add(infoContent).expand().center();

        // bottom - info panel full width
        root.add(infoPanel).height(380).expandX().fillX().padTop(10);

        //  TOP BAR (CASH + BACK)
        cashLabel = new Label("$" + formatCash(CashManager.getCash()), skin);
        cashLabel.setFontScale(1.2f);
        cashLabel.setAlignment(Align.center);

        Container<Label> cashContainer = new Container<>(cashLabel);
        cashContainer.setBackground(createCashLabelBackground());
        cashContainer.center();

        Table topBar = new Table();
        topBar.top().pad(10);
        topBar.setFillParent(true);
        topBar.add(cashContainer).width(200).height(50).expandX().left().padLeft(75);

        ImageButton backButton = new ImageButton(UIStyles.getBackButtonStyle());
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });

        topBar.add(backButton).right().width(80).height(30).padRight(75);
        stage.addActor(topBar);

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

        // Keyboard Input
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

                            String selectedPaintImage = CarPaintManager.getSelectedPaintImage(car);
                            CarSelectionData.setSelectedCarTexture(selectedPaintImage);
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

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACKSPACE) {
                    game.setScreen(new MenuScreen(game));
                    return true;
                }
                return false;
            }
        });
    }

    // Add one car to horizontal row
    private void addCarCard(Table parent, CarData car) {
        Table carBox = new Table(skin);
        carBox.setBackground("default-round");
        carBox.setColor(0.1f, 0.1f, 0.1f, 0.85f);
        carBox.pad(8).defaults().space(6);
        carBox.setTransform(true);
        carBox.setUserObject(car);

        // Image
        try {
            String displayImage = CarPaintManager.getSelectedPaintImage(car);
            Texture carTexture = new Texture(Gdx.files.internal(displayImage));
            Image carImage = new Image(carTexture);
            carImage.setScaling(Scaling.fit);

            carCardImages.put(car.image, carImage);

            carBox.add(carImage).size(300, 150).row();
        } catch (Exception e) {
            System.err.println("Failed to load owned car image: " + car.image);
            e.printStackTrace();
        }

        // Title
        Label nameLabel = new Label(car.title, skin);
        nameLabel.setFontScale(1.2f);
        carBox.add(nameLabel).row();

        // Class/PI line on the card itself
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
        TextButton selectButton = new TextButton("Select", defaultButtonStyle);
        selectButton.getLabel().setAlignment(Align.center);
        selectButton.getLabel().setFontScale(1.0f);
        selectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String selectedPaintImage = CarPaintManager.getSelectedPaintImage(car);

                CarSelectionData.setSelectedCarTexture(selectedPaintImage);
                SelectedCar.set(selectedPaintImage);

                System.out.println("Selected car texture set to: " + selectedPaintImage);
                System.out.println("Selected car: " + car.title);

                if (game instanceof Main) {
                    ((Main) game).selectedCarName = car.title;
                }

                showCarInfo(car);
                game.setScreen(new GameModeSelectorScreen(game));
            }
        });
        // SELL button
        TextButton sellButton = new TextButton("Sell", defaultButtonStyle);
        sellButton.getLabel().setAlignment(Align.center);
        sellButton.getLabel().setFontScale(1.0f);

        sellButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showSellConfirmation(car);
            }
        });

        Table buttonRow = new Table();
        buttonRow.add(selectButton).width(140).height(40).padRight(8);
        buttonRow.add(sellButton).width(140).height(40);

        carBox.add(buttonRow).padTop(6).padBottom(6).row();

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

    private TextButton.TextButtonStyle createDefaultButtonStyle() {
        buttonUpTexture = new Texture(Gdx.files.internal("Default_Button.png"));
        buttonDownTexture = new Texture(Gdx.files.internal("Default_Button_Down.png"));
        buttonOverTexture = new Texture(Gdx.files.internal("Default_Button_Over.png"));

        TextureRegionDrawable up = new TextureRegionDrawable(new TextureRegion(buttonUpTexture));
        TextureRegionDrawable down = new TextureRegionDrawable(new TextureRegion(buttonDownTexture));
        TextureRegionDrawable over = new TextureRegionDrawable(new TextureRegion(buttonOverTexture));

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

    private void showCarInfo(CarData car) {
        try {
            String displayImage = CarPaintManager.getSelectedPaintImage(car);
            Texture carTexture = new Texture(Gdx.files.internal(displayImage));
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

        CarStats stats = CarRegistry.getStats(car.image);
        if (stats != null && stats.carClass != null) {
            classLabel.setText("Class: " + stats.carClass.name() + " " + stats.pi);
            classLabel.setColor(getClassColor(stats.carClass));
        } else {
            classLabel.setText("Class: ?");
            classLabel.setColor(Color.LIGHT_GRAY);
        }

        updatePaintButtons(car);
    }

    private void updateCarCardImage(CarData car) {
        if (car == null) return;

        Image cardImage = carCardImages.get(car.image);

        if (cardImage == null) {
            return;
        }

        try {
            String displayImage = CarPaintManager.getSelectedPaintImage(car);
            Texture newTexture = new Texture(Gdx.files.internal(displayImage));

            cardImage.setDrawable(
                new TextureRegionDrawable(new TextureRegion(newTexture))
            );
        } catch (Exception e) {
            System.err.println("Failed to update card image for: " + car.image);
            e.printStackTrace();
        }
    }

    private String formatCash(int cash) {
        return String.format("%,d", cash);
    }

    private Drawable createCashLabelBackground() {
        if (cashLabelTexture == null) {
            cashLabelTexture = new Texture(Gdx.files.internal("Cash_Label.png"));
        }

        TextureRegionDrawable drawable = new TextureRegionDrawable(new TextureRegion(cashLabelTexture));
        drawable.setMinWidth(0);
        drawable.setMinHeight(0);

        return drawable;
    }

    private void showSellConfirmation(CarData car) {
        if (car == null) return;

        String selectedTexture = CarSelectionData.getSelectedCarTexture();
        String selectedPaintForThisCar = CarPaintManager.getSelectedPaintImage(car);

        if (selectedTexture != null && selectedTexture.equals(selectedPaintForThisCar)) {
            showModernMessageDialog(
                "Can't Sell Selected Car",
                "You can't sell the car you're currently using.\n\nSelect another car first, then try again."
            );
            return;
        }

        if (CarOwnershipManager.getOwnedCars().size() <= 1) {
            showModernMessageDialog(
                "Can't Sell Last Car",
                "You need to keep at least one car in your garage."
            );
            return;
        }

        int refund = (int) (car.price * 0.75f);

        showModernConfirmDialog(
            "Sell Car?",
            "Are you sure you want to sell:\n\n" +
                car.title + "\n\n" +
                "Original price: $" + formatCash(car.price) + "\n" +
                "Refund: $" + formatCash(refund) + "\n\n" +
                "This will remove the car from your garage.",
            "Sell",
            "Cancel",
            () -> sellCar(car, refund)
        );
    }

    private Window.WindowStyle createModernDialogStyle() {
        Window.WindowStyle style = new Window.WindowStyle(skin.get(Window.WindowStyle.class));

        style.background = skin.newDrawable(
            "default-round",
            new Color(0.035f, 0.035f, 0.045f, 0.98f)
        );

        style.titleFont = skin.getFont("default-font");
        style.titleFontColor = Color.WHITE;

        return style;
    }

    private Drawable darkPopupPanelDrawable() {
        return skin.newDrawable(
            "default-round",
            new Color(0.09f, 0.09f, 0.12f, 0.96f)
        );
    }

    private void resizeSmallDialog(Dialog dialog) {
        float dialogWidth = Math.min(560f, stage.getWidth() - 80f);
        float dialogHeight = Math.min(360f, stage.getHeight() - 80f);

        dialog.setSize(dialogWidth, dialogHeight);
        dialog.setPosition(
            (stage.getWidth() - dialogWidth) / 2f,
            (stage.getHeight() - dialogHeight) / 2f
        );
    }

    private void showModernMessageDialog(String title, String message) {
        Dialog dialog = new Dialog(title, createModernDialogStyle());
        dialog.getTitleLabel().setAlignment(Align.center);
        dialog.getTitleLabel().setFontScale(1.15f);
        dialog.getContentTable().pad(26);

        Table messagePanel = new Table();
        messagePanel.setBackground(darkPopupPanelDrawable());
        messagePanel.pad(18);

        Label messageLabel = new Label(message, skin);
        messageLabel.setWrap(true);
        messageLabel.setAlignment(Align.center);
        messageLabel.setColor(Color.LIGHT_GRAY);

        messagePanel.add(messageLabel).width(430).center();

        TextButton okButton = new TextButton("OK", defaultButtonStyle);
        okButton.getLabel().setAlignment(Align.center);
        okButton.getLabel().setFontScale(1f);

        okButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dialog.hide();
            }
        });

        dialog.getContentTable().add(messagePanel).width(470).padBottom(20).row();
        dialog.getContentTable().add(okButton).width(150).height(36).row();

        dialog.show(stage);
        resizeSmallDialog(dialog);
    }

    private void showModernConfirmDialog(
        String title,
        String message,
        String confirmText,
        String cancelText,
        Runnable onConfirm
    ) {
        Dialog dialog = new Dialog(title, createModernDialogStyle());
        dialog.getTitleLabel().setAlignment(Align.center);
        dialog.getTitleLabel().setFontScale(1.15f);
        dialog.getContentTable().pad(26);

        Table messagePanel = new Table();
        messagePanel.setBackground(darkPopupPanelDrawable());
        messagePanel.pad(18);

        Label messageLabel = new Label(message, skin);
        messageLabel.setWrap(true);
        messageLabel.setAlignment(Align.center);
        messageLabel.setColor(Color.LIGHT_GRAY);

        messagePanel.add(messageLabel).width(430).center();

        TextButton confirmButton = new TextButton(confirmText, defaultButtonStyle);
        confirmButton.getLabel().setAlignment(Align.center);
        confirmButton.getLabel().setFontScale(1.0f);

        TextButton cancelButton = new TextButton(cancelText, defaultButtonStyle);
        cancelButton.getLabel().setAlignment(Align.center);
        cancelButton.getLabel().setFontScale(1.0f);

        confirmButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dialog.hide();
                if (onConfirm != null) {
                    onConfirm.run();
                }
            }
        });

        cancelButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dialog.hide();
            }
        });

        Table buttonRow = new Table();
        buttonRow.add(confirmButton).width(150).height(36).padRight(12);
        buttonRow.add(cancelButton).width(140).height(36);

        dialog.getContentTable().add(messagePanel).width(470).padBottom(20).row();
        dialog.getContentTable().add(buttonRow).row();

        dialog.show(stage);
        resizeSmallDialog(dialog);
    }

    private void updatePaintButtons(CarData car) {
        paintTable.clear();

        if (car == null || car.paints == null || car.paints.isEmpty()) {
            Label noPaintLabel = new Label("No paint options available.", skin);
            noPaintLabel.setColor(Color.LIGHT_GRAY);
            paintTable.add(noPaintLabel).left();
            return;
        }

        Label title = new Label("Paint:", skin);
        title.setFontScale(1.1f);
        title.setColor(Color.WHITE);
        paintTable.add(title).padRight(14);

        for (CarPaint paint : car.paints) {
            boolean selected = CarPaintManager.isPaintSelected(car, paint);

            Color paintColor = getPaintColor(paint.name);
            TextButton.TextButtonStyle paintStyle = createPaintButtonStyle(paintColor, selected);

            TextButton paintButton = new TextButton(selected ? "✓" : "", paintStyle);
            paintButton.getLabel().setAlignment(Align.center);
            paintButton.getLabel().setFontScale(1.1f);

            paintButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (CarPaintManager.isPaintSelected(car, paint)) {
                        showModernMessageDialog(
                            "Already Selected",
                            paint.name + " paint is already selected for this car."
                        );
                        return;
                    }

                    showPaintConfirmDialog(car, paint);
                }
            });

            Table paintOption = new Table();

            paintOption.add(paintButton).width(42).height(42).row();

            Label paintNameLabel = new Label(paint.name, skin);
            paintNameLabel.setFontScale(0.75f);
            paintNameLabel.setColor(selected ? Color.GREEN : Color.LIGHT_GRAY);
            paintNameLabel.setAlignment(Align.center);

            paintOption.add(paintNameLabel).width(70).center();

            paintTable.add(paintOption).padRight(8).top();
        }
    }

    private Color getPaintColor(String paintName) {
        if (paintName == null) return Color.WHITE;

        String name = paintName.toLowerCase();

        if (name.contains("black")) return Color.BLACK;
        if (name.contains("white")) return Color.WHITE;
        if (name.contains("red")) return Color.RED;
        if (name.contains("blue")) return Color.BLUE;
        if (name.contains("green")) return Color.GREEN;
        if (name.contains("yellow")) return Color.YELLOW;
        if (name.contains("orange")) return Color.ORANGE;
        if (name.contains("purple")) return new Color(0.55f, 0.20f, 0.90f, 1f);
        if (name.contains("pink")) return new Color(1f, 0.35f, 0.70f, 1f);
        if (name.contains("grey") || name.contains("gray")) return Color.GRAY;
        if (name.contains("silver")) return new Color(0.75f, 0.75f, 0.80f, 1f);
        if (name.contains("gold")) return Color.GOLD;
        if (name.contains("cyan")) return Color.CYAN;

        return Color.LIGHT_GRAY;
    }

    private TextButton.TextButtonStyle createPaintButtonStyle(Color baseColor, boolean selected) {
        Color upColor = new Color(baseColor);
        Color overColor = brightenColor(baseColor, 1.25f);
        Color downColor = darkenColor(baseColor, 0.75f);

        if (selected) {
            overColor = Color.GREEN;
        }

        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();

        style.up = skin.newDrawable("default-round", upColor);
        style.over = skin.newDrawable("default-round", overColor);
        style.down = skin.newDrawable("default-round", downColor);

        style.font = skin.getFont("default-font");
        style.fontColor = selected ? Color.BLACK : Color.CLEAR;
        style.overFontColor = selected ? Color.BLACK : Color.CLEAR;
        style.downFontColor = selected ? Color.BLACK : Color.CLEAR;

        return style;
    }

    private Color brightenColor(Color color, float amount) {
        return new Color(
            Math.min(color.r * amount, 1f),
            Math.min(color.g * amount, 1f),
            Math.min(color.b * amount, 1f),
            color.a
        );
    }

    private Color darkenColor(Color color, float amount) {
        return new Color(
            color.r * amount,
            color.g * amount,
            color.b * amount,
            color.a
        );
    }

    private void showPaintConfirmDialog(CarData car, CarPaint paint) {
        showModernConfirmDialog(
            "Paint Car?",
            "Paint " + car.title + " in " + paint.name + "?\n\n" +
                "Cost: $500",
            "Paint",
            "Cancel",
            () -> paintCar(car, paint)
        );
    }

    private void paintCar(CarData car, CarPaint paint) {
        stage.getRoot().setTouchable(Touchable.disabled);

        SupabaseGameData.paintCar(
            car.image,
            paint.image,
            newCash -> {
                stage.getRoot().setTouchable(Touchable.enabled);

                if (newCash >= 0) {
                    CashManager.setCash(newCash);
                    CashManager.saveCash();

                    if (cashLabel != null) {
                        cashLabel.setText("$" + formatCash(CashManager.getCash()));
                    }
                }

                CarPaintManager.setSelectedPaintImage(car.image, paint.image);

                showCarInfo(car);
                updateCarCardImage(car);
                updateCenterHighlight();

                showModernMessageDialog(
                    "Paint Updated",
                    car.title + " is now painted " + paint.name + "."
                );
            },
            err -> {
                stage.getRoot().setTouchable(Touchable.enabled);

                String msg;
                switch (err) {
                    case "insufficient_funds":
                        msg = "You don't have enough cash.";
                        break;
                    case "not_owned":
                        msg = "You do not own this car.";
                        break;
                    case "invalid_paint":
                        msg = "This paint is not available for this car.";
                        break;
                    case "not_logged_in":
                        msg = "Please log in again.";
                        break;
                    default:
                        msg = "Paint failed: " + err;
                        break;
                }

                showModernMessageDialog(
                    "Paint Failed",
                    msg
                );
            }
        );
    }

    private void sellCar(CarData car, int refund) {
        if (car == null) return;

        CarData carData = CarDataBase.getCarByImage(car.image);

        if (carData == null) {
            System.err.println("Couldn't find car data for selling: " + car.image);
            return;
        }

        boolean removed = CarOwnershipManager.removeCarResolved(car);

        if(!removed){
            showModernMessageDialog(
                "Sell Failed",
                "This car could not be removed from your owned cars."
            );
            return;
        }

        CashManager.addCash(refund);

        System.out.println("Sold car: " + car.title + " for $" + refund);

        String selectedTexture = CarSelectionData.getSelectedCarTexture();
        String selectedPaintForThisCar = CarPaintManager.getSelectedPaintImage(car);

        if (selectedTexture != null && selectedTexture.equals(selectedPaintForThisCar)) {
            if (!CarOwnershipManager.getOwnedCars().isEmpty()) {
                String firstOwned = new ArrayList<>(CarOwnershipManager.getOwnedCars()).get(0);
                CarData firstCar = CarDataBase.getCarByImage(firstOwned);

                if (firstCar != null) {
                    String firstPaint = CarPaintManager.getSelectedPaintImage(firstCar);
                    SelectedCar.set(firstPaint);
                    CarSelectionData.setSelectedCarTexture(firstPaint);
                }
            }
        }

        game.setScreen(new GarageScreen(game));
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
                card.setScale(1.0f);
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
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();

        if (buttonUpTexture != null) buttonUpTexture.dispose();
        if (buttonDownTexture != null) buttonDownTexture.dispose();
        if (buttonOverTexture != null) buttonOverTexture.dispose();
        if (cashLabelTexture != null) cashLabelTexture.dispose();
    }
}
