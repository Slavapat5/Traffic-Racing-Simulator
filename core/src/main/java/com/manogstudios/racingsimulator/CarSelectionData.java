package com.manogstudios.racingsimulator;

public class CarSelectionData {
    // default car
    private static String selectedCarTexture = "2014 Sazda FX5 Shiatto - Light Red.png";

    // selecting car in garage screen
    public static void setSelectedCarTexture(String texturePath){
        selectedCarTexture = texturePath;
    }

    // game modes retrieving the selected car so the stats can be looked up
    public static String getSelectedCarTexture(){
        return selectedCarTexture;
    }
}
