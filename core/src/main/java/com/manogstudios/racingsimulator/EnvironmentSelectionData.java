package com.manogstudios.racingsimulator;

// Stores the currently selected environment choices between selection screens and gameplay screens.
public class EnvironmentSelectionData {
    private static String selectedLocation = "Plains";
    private static String selectedSeason = "Summer";

    public static void setSelectedLocation(String location) {
        selectedLocation = location;
    }

    public static String getSelectedLocation() {
        return selectedLocation;
    }

    public static void setSelectedSeason(String season) {
        selectedSeason = season;
    }

    public static String getSelectedSeason() {
        return selectedSeason;
    }
}
