package com.manogstudios.racingsimulator;

public class CarSelectionData {
    private static String selectedCarTexture = "2014 Sazda FX5 Shiatto - Light Red.png";

    public static void setSelectedCarTexture(String texturePath){
        selectedCarTexture = texturePath;
    }

    public static String getSelectedCarTexture(){
        return selectedCarTexture;
    }
}
