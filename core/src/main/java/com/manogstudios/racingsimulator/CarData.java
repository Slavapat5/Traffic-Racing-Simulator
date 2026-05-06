package com.manogstudios.racingsimulator;

import java.util.List;

public class CarData {
    public String image;
    public String title;
    public String description;
    public int price;

    public int horsepower;
    public int weightKg;
    public String engine;
    public String longDescription;

    public List<CarPaint> paints;


    // No-args constructor required for JSON deserialization
    public CarData() {}
}
