package com.manogstudios.racingsimulator;

public class CarSelectionData {
    private static String selectedCarTexture = "Mazda MX-5 Miata - 2014.png";

    public static void setSelectedCarTexture(String texturePath){
        selectedCarTexture = texturePath;
    }

    public static String getSelectedCarTexture(){
        return selectedCarTexture;
    }
}
