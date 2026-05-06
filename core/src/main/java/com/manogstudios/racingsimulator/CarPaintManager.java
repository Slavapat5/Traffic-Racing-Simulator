package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

import java.util.HashMap;
import java.util.Map;

public class CarPaintManager {

    private static final String PREF_NAME = "car_paint_preferences";
    private static final Map<String, String> selectedPaints = new HashMap<>();

    public static void load() {
        selectedPaints.clear();

        Preferences prefs = Gdx.app.getPreferences(PREF_NAME);

        for (CarData car : CarDataBase.getAllCars()) {
            String paintImage = prefs.getString(car.image, null);

            if (paintImage != null && !paintImage.isEmpty() && isValidPaintForCar(car, paintImage)) {
                selectedPaints.put(car.image, paintImage);
            }
        }
    }

    public static void save() {
        Preferences prefs = Gdx.app.getPreferences(PREF_NAME);

        prefs.clear();

        for (Map.Entry<String, String> entry : selectedPaints.entrySet()) {
            prefs.putString(entry.getKey(), entry.getValue());
        }

        prefs.flush();
    }

    public static String getSelectedPaintImage(CarData car) {
        if (car == null) return null;

        String savedPaint = selectedPaints.get(car.image);

        if (savedPaint != null && !savedPaint.isEmpty() && isValidPaintForCar(car, savedPaint)) {
            return savedPaint;
        }

        if (car.paints != null && !car.paints.isEmpty()) {
            return car.paints.get(0).image;
        }

        return car.image;
    }

    public static void setSelectedPaintImage(String baseCarImage, String paintImage) {
        if (baseCarImage == null || paintImage == null) return;

        CarData car = CarDataBase.getCarByImage(baseCarImage);

        if (car != null && !isValidPaintForCar(car, paintImage)) {
            System.err.println("Invalid paint ignored: " + paintImage + " for " + baseCarImage);
            return;
        }

        selectedPaints.put(baseCarImage, paintImage);
        save();
    }

    public static void setSelectedPaintImageFromCloud(String baseCarImage, String paintImage) {
        setSelectedPaintImage(baseCarImage, paintImage);
    }

    public static boolean isPaintSelected(CarData car, CarPaint paint) {
        if (car == null || paint == null) return false;

        String currentPaint = getSelectedPaintImage(car);
        return paint.image.equals(currentPaint);
    }

    public static boolean isValidPaintForCar(CarData car, String paintImage) {
        if (car == null || paintImage == null || paintImage.isEmpty()) return false;

        if (car.paints == null || car.paints.isEmpty()) {
            return paintImage.equals(car.image);
        }

        for (CarPaint paint : car.paints) {
            if (paint != null && paintImage.equals(paint.image)) {
                return true;
            }
        }

        return false;
    }

    public static void removePaintPreference(String baseCarImage) {
        if (baseCarImage == null) return;

        selectedPaints.remove(baseCarImage);
        save();
    }
}
