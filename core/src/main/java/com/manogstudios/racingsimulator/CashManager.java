package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;

public class CashManager {
    private static String FILE_PATH = "cash_default.txt"; // fallback if no user
    private static int cash = 10000; // starting cash
    public static boolean enableSaving = true;
    public static boolean enableCloudSync = true;

    // tell CashManager which user is active
    public static void setCurrentUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            FILE_PATH = "cash_default.txt";
        } else {
            FILE_PATH = "cash_" + userId + ".txt";
        }
    }

    public static void loadCash() {
        FileHandle file = Gdx.files.local(FILE_PATH);
        if (file.exists()) {
            try {
                cash = Integer.parseInt(file.readString().trim());
            } catch (NumberFormatException e) {
                e.printStackTrace();
                cash = 10000; // fallback
            }
        } else {
            cash = 10000;   // starter cash
            saveCash();     // create  personal save file
        }
    }

    public static void saveCash() {
        if (!enableSaving) return;
        FileHandle file = Gdx.files.local(FILE_PATH);
        file.writeString(String.valueOf(cash), false);
    }

    public static int getCash() {
        return cash;
    }

    public static void addCash(int amount) {
        cash += amount;
        saveCash();
        if (enableCloudSync) {
            SupabaseGameData.saveCash(SupabaseAuth.userId, SupabaseAuth.accessToken, cash);
        }
    }

    public static boolean subtractCash(int amount) {
        if (cash >= amount) {
            cash -= amount;
            saveCash();
            if (enableCloudSync) {
                SupabaseGameData.saveCash(SupabaseAuth.userId, SupabaseAuth.accessToken, cash);
            }
            return true;
        }
        return false;
    }

    public static void addCashAndSync(int delta, String reason) {
        cash += delta;
        saveCash();

        if (!enableCloudSync || !SupabaseAuth.isLoggedIn) return;

        SupabaseGameData.adjustCash(delta, reason, newCash -> {
            if (newCash != null) {
                cash = newCash;
                saveCash();
            } else {
                System.out.println("addCashAndSync: server sync failed (reason=" + reason + ")");
            }
        });
    }

    public static void setCash(int newCash) {
        cash = newCash;
        saveCash();
        if (enableCloudSync) {
            SupabaseGameData.saveCash(SupabaseAuth.userId, SupabaseAuth.accessToken, cash);
        }
    }

}
