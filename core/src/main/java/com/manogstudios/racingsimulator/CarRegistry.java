package com.manogstudios.racingsimulator;

import com.badlogic.gdx.math.MathUtils;
import java.util.HashMap;
import java.util.Map;

public class CarRegistry {
    private static final Map<String, CarStats> statsMap = new HashMap<>();

    static {
        // Speed = mph*10, acceleration = hp/2
        // PI + Class are computed automatically by CarStats(speed, accel, handling)
        statsMap.put("Mazda MX-5 Miata - 2014.png", new CarStats(1340f, 84f, 3f));
        statsMap.put("Ford Fiesta ST - 2019.png", new CarStats(1440f, 100f, 3f));
        statsMap.put("Volkswagen Golf GTI Mk8 - 2019.png", new CarStats(1550f, 121f, 3f));
        statsMap.put("BMW 330i - 2025.png", new CarStats(1300f, 128f, 2.5f));
        statsMap.put("Ferrari 458 - 2015.png", new CarStats(2020f, 281f, 2f));
        statsMap.put("Lamborghini Huracan - 2015.png", new CarStats(2020f, 305f, 2.2f));
        statsMap.put("Mclaren 650s - 2015.png", new CarStats(2070f, 321f, 2.1f));
    }

    public static CarStats getStats(String carImageName) {
        return statsMap.getOrDefault(carImageName, new CarStats(1000f, 200f, 2f));
    }

    // ---- AUTO PI + CLASS ----
    public static int calculatePI(float speed, float accel, float handling) {
        // speed is mph*10
        // accel is hp/2  (so "hp" ~= accel*2)

        float mph = speed / 10f;
        float hp = accel * 2f;

        // Caps
        final float MPH_CAP = 300f;     // ~Chiron-level top speed
        final float HP_CAP  = 1500f;    // hypercar ceiling
        final float HANDLING_CAP = 4.0f; //  handling seems 2.0..3.5-ish

        // Normalise to 0..1 with clamp
        float s = com.badlogic.gdx.math.MathUtils.clamp(mph / MPH_CAP, 0f, 1f);
        float a = com.badlogic.gdx.math.MathUtils.clamp(hp  / HP_CAP,  0f, 1f);
        float h = com.badlogic.gdx.math.MathUtils.clamp(handling / HANDLING_CAP, 0f, 1f);


        // This makes midrange cars score lower, while still allowing extremes to climb.
        s = (float) Math.sqrt(s);
        a = (float) Math.sqrt(a);
        h = (float) Math.sqrt(h);

        // ---- Weighted sum ----
        // Total is designed to be under ~900
        // Tune these weights to make speed/accel to matter more or less.
        float raw =
            (s * 650f) +   // speed contribution
                (a * 450f) +   // power/acceleration contribution
                (h * 120f);    // handling contribution

        return com.badlogic.gdx.math.MathUtils.clamp(Math.round(raw), 0, 999);
    }

    public static CarClass classFromPI(int pi) {
        if (pi < 350) return CarClass.D;
        if (pi < 500) return CarClass.C;
        if (pi < 650) return CarClass.B;
        if (pi < 800) return CarClass.A;
        if (pi < 900) return CarClass.S1;
        if (pi < 980) return CarClass.S2;
        return CarClass.X;
    }

}
