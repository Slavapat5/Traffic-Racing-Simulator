package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.TimeUtils;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;

public class AchievementsManager {

    public static class AchievementDef {
        public final String id;
        public final String name;
        public final String description;

        public AchievementDef(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }
    }

    public static class AchievementState {
        public final AchievementDef def;
        public boolean unlocked;
        public long unlockedAt; // millis timestamp

        public AchievementState(AchievementDef def) {
            this.def = def;
        }
    }

    private static final Array<AchievementState> achievements = new Array<>();
    private static String currentUserId = "default";
    private static Preferences prefs;

    static {
        // === Define achievements here ===
        register("free_ride_first_run",
            "First Ride",
            "Finish a Free Ride run for the first time.");

        register("free_ride_10000",
            "High Roller",
            "Score 10,000 or more in a single Free Ride run.");

        register("free_ride_50000",
            "Speed Demon",
            "Score 50,000 or more in a single Free Ride run.");

        register("free_ride_5km",
            "Long Haul",
            "Drive at least 5,000 m in a single Free Ride run.");

        register("free_ride_300s",
            "Survivor",
            "Survive for 300 seconds in a single Free Ride run.");
    }

    private static void register(String id, String name, String description) {
        achievements.add(new AchievementState(new AchievementDef(id, name, description)));
    }

    public static void setCurrentUser(String userId) {
        currentUserId = (userId == null || userId.isEmpty()) ? "default" : userId;
        prefs = Gdx.app.getPreferences("achievements_" + currentUserId);
        load();
    }

    public static Array<AchievementState> getAll() {
        return achievements;
    }

    public static boolean isUnlocked(String id) {
        AchievementState a = findById(id);
        return a != null && a.unlocked;
    }


    public static Array<AchievementState> unlock(String id) {
        Array<AchievementState> newly = new Array<>();
        unlockIf(newly, id, true);
        if (newly.size > 0) save();
        return newly;
    }

    // Called after a Free Ride run ends, returns newly unlocked achievements.
    public static Array<AchievementState> onFreeRideFinished(int score, int distMeters, float timeSeconds) {
        Array<AchievementState> newlyUnlocked = new Array<>();

        unlockIf(newlyUnlocked, "free_ride_first_run", true);
        unlockIf(newlyUnlocked, "free_ride_10000", score >= 10_000);
        unlockIf(newlyUnlocked, "free_ride_50000", score >= 50_000);
        unlockIf(newlyUnlocked, "free_ride_5km", distMeters >= 5_000);
        unlockIf(newlyUnlocked, "free_ride_300s", timeSeconds >= 300f);

        if (newlyUnlocked.size > 0) {
            save();
        }
        return newlyUnlocked;
    }


    public static void syncFromCloud(Runnable onDone) {
        if (!SupabaseAuth.isLoggedIn || SupabaseAuth.userId == null || SupabaseAuth.accessToken == null) {
            if (onDone != null) Gdx.app.postRunnable(onDone);
            return;
        }

        SupabaseGameData.loadAchievements(SupabaseAuth.userId, SupabaseAuth.accessToken, (JSONArray arr) -> {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject row = arr.getJSONObject(i);

                String achId = row.optString("achievement_id", null);
                String unlockedAtStr = row.optString("unlockedat",
                    row.optString("unlocked_at", null));

                if (achId == null) continue;

                AchievementState a = findById(achId);
                if (a == null) continue;

                a.unlocked = true;

                if (unlockedAtStr != null && !unlockedAtStr.isEmpty()) {
                    try {
                        a.unlockedAt = Instant.parse(unlockedAtStr).toEpochMilli();
                    } catch (Exception ignored) {
                        if (a.unlockedAt == 0L) a.unlockedAt = TimeUtils.millis();
                    }
                } else {
                    if (a.unlockedAt == 0L) a.unlockedAt = TimeUtils.millis();
                }
            }

            save();
            if (onDone != null) onDone.run();
        });
    }

    private static void load() {
        if (prefs == null) return;
        for (AchievementState a : achievements) {
            a.unlocked = prefs.getBoolean(a.def.id + "_unlocked", false);
            a.unlockedAt = prefs.getLong(a.def.id + "_time", 0L);
        }
    }

    private static void save() {
        if (prefs == null) return;
        for (AchievementState a : achievements) {
            prefs.putBoolean(a.def.id + "_unlocked", a.unlocked);
            prefs.putLong(a.def.id + "_time", a.unlockedAt);
        }
        prefs.flush();
    }

    private static void unlockIf(Array<AchievementState> newly, String id, boolean condition) {
        if (!condition) return;

        AchievementState a = findById(id);
        if (a == null || a.unlocked) return;

        a.unlocked = true;
        a.unlockedAt = TimeUtils.millis();
        newly.add(a);

        // Push to cloud using Edge Function
        if (SupabaseAuth.isLoggedIn) {
            SupabaseGameData.unlockAchievement(
                a.def.id,
                () -> System.out.println("Achievement unlocked in cloud: " + a.def.id),
                (err) -> System.out.println("unlockAchievement failed (" + a.def.id + "): " + err)
            );
        }
    }

    private static AchievementState findById(String id) {
        for (AchievementState a : achievements) {
            if (a.def.id.equals(id)) return a;
        }
        return null;
    }
}
