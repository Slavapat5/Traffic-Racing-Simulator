package com.manogstudios.racingsimulator;

// Holds the texture paths and road width for a selected location/season theme
public class EnvironmentTheme {
    public final String roadTexturePath;
    public final String surroundingsTexturePath;
    public final float roadWidth;

    public EnvironmentTheme(String roadTexturePath, String surroundingsTexturePath, float roadWidth) {
        this.roadTexturePath = roadTexturePath;
        this.surroundingsTexturePath = surroundingsTexturePath;
        this.roadWidth = roadWidth;
    }
}
