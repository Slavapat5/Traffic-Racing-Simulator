package com.manogstudios.racingsimulator;

import com.badlogic.gdx.math.MathUtils;

public class CarEarnings {

    private static final int MIN_BONUS_PER_MILE = 100;
    private static final int MAX_BONUS_PER_MILE = 800;

    private static final float BASE_PRICE = 20000f;
    private static final float TARGET_PRICE = 200000f;

    public static int getBonusPerMile(String carImageName) {
        if (carImageName == null || carImageName.isEmpty()) {
            return MIN_BONUS_PER_MILE;
        }

        CarDataBase.load();

        CarData car = CarDataBase.getCarByImage(carImageName);

        if (car == null) {
            return MIN_BONUS_PER_MILE;
        }

        float progress = (car.price - BASE_PRICE) / (TARGET_PRICE - BASE_PRICE);

        int bonus = Math.round(
            MIN_BONUS_PER_MILE + (progress * (MAX_BONUS_PER_MILE - MIN_BONUS_PER_MILE))
        );

        return MathUtils.clamp(bonus, MIN_BONUS_PER_MILE, MAX_BONUS_PER_MILE);
    }

    public static int calculateCarDistanceBonus(String carImageName, float distanceMeters) {
        float miles = distanceMeters / 1609.34f;
        int bonusPerMile = getBonusPerMile(carImageName);

        return Math.round(miles * bonusPerMile);
    }
}
