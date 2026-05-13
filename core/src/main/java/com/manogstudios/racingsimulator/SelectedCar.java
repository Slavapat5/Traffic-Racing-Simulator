package com.manogstudios.racingsimulator;

public class SelectedCar {
    private static String selectedCarImage = "2014 Sazda FX5 Shiatto - Light Red.png"; // Default car if none are selected

    public static void set(String carImage) {
        selectedCarImage = carImage;
    }

    public static String get() {
        return selectedCarImage;
    }
}
