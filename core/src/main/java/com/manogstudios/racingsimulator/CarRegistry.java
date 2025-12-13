package com.manogstudios.racingsimulator;

import java.util.HashMap;
import java.util.Map;

public class CarRegistry {
    private static final Map<String, CarStats> statsMap = new HashMap<>();

    static {
        // Speed = mph*10, acceleration = hp/2
        statsMap.put("Mazda MX-5 Miata - 2014.png", new CarStats(1340f, 84f, 3f));
        statsMap.put("Ford Fiesta ST - 2019.png", new CarStats(1440f, 100f, 3f));
        statsMap.put("Volkswagen Golf GTI Mk8 - 2019.png", new CarStats(1550f, 121f, 3f));
        statsMap.put("BMW 330i - 2025.png", new CarStats(1300f, 128f, 2.5f));
        statsMap.put("Ferrari 458 - 2015.png", new CarStats(2020f, 281f, 2f));
        statsMap.put("Lamborghini Huracan - 2015.png", new CarStats(2020f, 305f, 2.2f));
        statsMap.put("Mclaren 650s - 2015.png", new CarStats(2070f, 321f, 2.1f));
    }

    public static CarStats getStats(String carImageName) {
        return statsMap.getOrDefault(carImageName, new CarStats(1000f, 200f, 2f)); // fallback stats
    }
}
