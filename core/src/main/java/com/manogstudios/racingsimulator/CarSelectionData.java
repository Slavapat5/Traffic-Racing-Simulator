package com.manogstudios.racingsimulator;

public class CarSelectionData {
    // Default selected car used before the player chooses a car in the garage
    private static String selectedCarTexture = "2014 Sazda FX5 Shiatto - Light Red.png";

    // Stores the selected car/paint texture from the garage
    public static void setSelectedCarTexture(String texturePath){
        selectedCarTexture = texturePath;
    }

    // Used by game modes to load the selected car texture and look up its stats.
    public static String getSelectedCarTexture(){
        return selectedCarTexture;
    }
}
