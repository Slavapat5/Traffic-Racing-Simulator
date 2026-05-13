package com.manogstudios.racingsimulator;

import java.util.HashMap;
import java.util.Map;

public class CarRegistry {
    private static final Map<String, CarStats> statsMap = new HashMap<>();

    static {
        // Speed = mph*10, acceleration = hp/2
        // PI + Class are computed automatically by CarStats(speed, accel, handling)

        CarStats sazdaFx5 = new CarStats(1340f, 84f, 3f);
        statsMap.put("Mazda MX-5 Miata - 2014.png", sazdaFx5); // Old filename support for older save data
        statsMap.put("2014 Sazda FX5 Shiatto - Light Red.png", sazdaFx5);
        statsMap.put("2014 Sazda FX5 Shiatto - Pink.png", sazdaFx5);

        CarStats nordSesta = new CarStats(1440f, 100f, 2.8f);
        statsMap.put("Ford Fiesta ST - 2019.png", nordSesta); // Old filename support for older save data
        statsMap.put("2019 Nord Sesta SV - Light Red.png", nordSesta);
        statsMap.put("2019 Nord Sesta SV - Pink.png", nordSesta);

        CarStats slovagenBall = new CarStats(1550f, 121f, 2.1f);
        statsMap.put("Volkswagen Golf GTI Mk8 - 2019.png", slovagenBall); // Old filename support for older save data
        statsMap.put("2019 Slovagen Ball GTR V8 - Light Red.png", slovagenBall);
        statsMap.put("2019 Slovagen Ball GTR V8 - Pink.png", slovagenBall);

        CarStats dmv365e = new CarStats(1300f, 128f, 2.0f);
        statsMap.put("BMW 330i - 2025.png", dmv365e); // Old filename support for older save data
        statsMap.put("2025 DMV 365e - Black.png", dmv365e);
        statsMap.put("2025 DMV 365e - Grey.png", dmv365e);
        statsMap.put("2025 DMV 365e - Silver.png", dmv365e);
        statsMap.put("2025 DMV 365e - White.png", dmv365e);

        CarStats ferrati460 = new CarStats(2020f, 281f, 3.4f);
        statsMap.put("Ferrari 458 - 2015.png", ferrati460); // Old filename support for older save data
        statsMap.put("2015 Ferrati 460 - Light Red.png", ferrati460);
        statsMap.put("2015 Ferrati 460 - Pink.png", ferrati460);

        CarStats lambiniUragan = new CarStats(2020f, 305f, 3.0f);
        statsMap.put("Lamborghini Huracan - 2015.png", lambiniUragan); // Old filename support for older save data
        statsMap.put("2015 Lambini Uragan - Orange.png", lambiniUragan);
        statsMap.put("2015 Lambini Uragan - Yellow.png", lambiniUragan);

        CarStats solaren660z = new CarStats(2070f, 321f, 3.2f);
        statsMap.put("Mclaren 650s - 2015.png", solaren660z); // Old filename support for older save data
        statsMap.put("2015 Solaren 660z - Light Green.png", solaren660z);
        statsMap.put("2015 Solaren 660z - Pink.png", solaren660z);

        CarStats ferratiSfx900 = new CarStats(2110f, 493f, 3.4f);
        statsMap.put("Ferrari SF90 Stradale - 2024.png", ferratiSfx900); // Old filename support for older save data
        statsMap.put("2024 Ferrati SFX900 Strattalius - Light Red.png", ferratiSfx900);
        statsMap.put("2024 Ferrati SFX900 Strattalius - Pink.png", ferratiSfx900);

        CarStats hellbunny = new CarStats(1990f, 359f, 1.6f);
        statsMap.put("DOJ Challenge RST Hellbunny - 2023.png", hellbunny); // Old filename support for older save data
        statsMap.put("2023 DOJ Challenge RST Hellbunny - Black.png", hellbunny);
        statsMap.put("2023 DOJ Challenge RST Hellbunny - Grey.png", hellbunny);
        statsMap.put("2023 DOJ Challenge RST Hellbunny - Silver.png", hellbunny);
        statsMap.put("2023 DOJ Challenge RST Hellbunny - White.png", hellbunny);


        CarStats sedecremStr = new CarStats(2080f, 309f, 2.2f);
        statsMap.put("2010 Sedecrem STR Solaren - Grey.png", sedecremStr);
        statsMap.put("2010 Sedecrem STR Solaren - Silver.png", sedecremStr);
        statsMap.put("Mercedes SLR Mclaren - 2010.png", sedecremStr);
    }

    public static CarStats getStats(String carImageName) {
        return statsMap.getOrDefault(carImageName, new CarStats(1000f, 200f, 2f));
    }

    // ---- AUTO PI + CLASS ----
    public static int calculatePI(float speed, float accel, float handling) {
        // speed is mph*10
        // accel is hp/2

        float mph = speed / 10f;
        float hp = accel * 2f;

        // Caps
        final float MPH_CAP = 350f;     // hypercar level top speed
        final float HP_CAP  = 1500f;    // hypercar ceiling
        final float HANDLING_CAP = 5.0f;

        // Normalise to 0..1 with clamp
        float s = com.badlogic.gdx.math.MathUtils.clamp(mph / MPH_CAP, 0f, 1f);
        float a = com.badlogic.gdx.math.MathUtils.clamp(hp  / HP_CAP,  0f, 1f);
        float h = com.badlogic.gdx.math.MathUtils.clamp(handling / HANDLING_CAP, 0f, 1f);


        // This makes midrange cars score lower, while still allowing extremes to climb.
        s = (float) Math.sqrt(s);
        a = (float) Math.sqrt(a);
        h = (float) Math.sqrt(h);

        // Weighted sum - These weights are tuneable
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
