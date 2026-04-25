package com.manogstudios.racingsimulator;

public class EnvironmentThemeManager {

    public static EnvironmentTheme getCurrentTheme() {
        String location = EnvironmentSelectionData.getSelectedLocation();
        String season = EnvironmentSelectionData.getSelectedSeason();

        if (location == null) location = "Plains";
        if (season == null) season = "Summer";

        // Plains
        if (location.equals("Plains") && season.equals("Spring")) {
            return new EnvironmentTheme(
                "road_plain_spring.png",
                "surroundings_plain_spring.png",
                800f
            );
        }

        if (location.equals("Plains") && season.equals("Summer")) {
            return new EnvironmentTheme(
                "road_plain_summer.png",
                "surroundings_plain_summer.png",
                800f
            );
        }

        if (location.equals("Plains") && season.equals("Autumn")) {
            return new EnvironmentTheme(
                "road_plain_autumn.png",
                "surroundings_plain_autumn.png",
                800f
            );
        }

        if (location.equals("Plains") && season.equals("Winter")) {
            return new EnvironmentTheme(
                "road_plain_winter.png",
                "surroundings_plain_winter.png",
                800f
            );
        }

        // Desert
        if (location.equals("Desert") && season.equals("Spring")) {
            return new EnvironmentTheme(
                "road_desert_spring.png",
                "surroundings_desert_spring.png",
                800f
            );
        }

        if (location.equals("Desert") && season.equals("Summer")) {
            return new EnvironmentTheme(
                "road_desert_summer.png",
                "surroundings_desert_summer.png",
                800f
            );
        }

        if (location.equals("Desert") && season.equals("Autumn")) {
            return new EnvironmentTheme(
                "road_desert_autumn.png",
                "surroundings_desert_autumn.png",
                800f
            );
        }

        if (location.equals("Desert") && season.equals("Winter")) {
            return new EnvironmentTheme(
                "road_desert_winter.png",
                "surroundings_desert_winter.png",
                800f
            );
        }

        // Fallback
        return new EnvironmentTheme(
            "Segment1.png",
            "Default_Background.png",
            800f
        );
    }
}
