package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CarOwnershipManager {
    private static String FILE_PATH = "owned_cars_default.txt";
    private static Set<String> ownedCars = new HashSet<>();

    public static void setCurrentUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            FILE_PATH = "owned_cars_default.txt";
        } else {
            FILE_PATH = "owned_cars_" + userId + ".txt";
        }
    }

    public static void loadOwnedCars() {
        ownedCars.clear();

        FileHandle file = Gdx.files.local(FILE_PATH);
        if (file.exists()) {
            String content = file.readString().trim();
            if (!content.isEmpty()) {
                String[] lines = content.split("\n");
                ownedCars.addAll(Arrays.asList(lines));
            }
        } else {
            // First time this user logs in, give starter car
            ownedCars.add("2014 Sazda FX5 Shiatto - Light Red.png"); //  starter car
            saveOwnedCars();
        }
    }

    public static void saveOwnedCars() {
        FileHandle file = Gdx.files.local(FILE_PATH);
        file.writeString(String.join("\n", ownedCars), false);
    }

    public static boolean ownsCar(String carImagePath) {
        return ownedCars.contains(carImagePath);
    }

    public static void addCar(String imagePath) {
        // Local-only. Cloud writes must be server-side
        addCarInternal(imagePath, false);
    }


    // This is ONLY for when loading cars FROM Supabase, to avoid re-uploading.
    public static void addCarFromCloud(String imagePath) {
        addCarInternal(imagePath, false);
    }

    private static void addCarInternal(String imagePath, boolean pushToCloud) {
        if (imagePath == null || imagePath.isEmpty()) return;

        if (!ownedCars.contains(imagePath)) {
            ownedCars.add(imagePath);
            saveOwnedCars();

            // Push to Supabase cloud (only if this was a local change, like buying a car)
            if (pushToCloud && SupabaseAuth.isLoggedIn) {
                SupabaseGameData.saveOwnedCar(
                    SupabaseAuth.userId,
                    SupabaseAuth.accessToken,
                    imagePath
                );
            }
        }
    }


    public static void removeCar(String imagePath) {
        if (ownedCars.contains(imagePath)) {
            ownedCars.remove(imagePath);
            saveOwnedCars();

            if (SupabaseAuth.isLoggedIn) {
                SupabaseGameData.removeOwnedCar(
                    SupabaseAuth.userId,
                    SupabaseAuth.accessToken,
                    imagePath
                );
            }
        }
    }


    public static void clearOwnedCars() {
        ownedCars.clear();
        saveOwnedCars();
    }

    public static Set<String> getOwnedCars() {
        return ownedCars;
    }
}
