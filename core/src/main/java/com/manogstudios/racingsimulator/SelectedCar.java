package com.manogstudios.racingsimulator;

public class SelectedCar {
    private static String selectedCarImage = "Mazda MX-5 Miata - 2014.png"; // Default car if none are selected

    public static void set(String carImage) {
        selectedCarImage = carImage;
    }

    public static String get() {
        return selectedCarImage;
    }
}
