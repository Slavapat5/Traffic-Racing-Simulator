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

        register("free_ride_25000",
            "Confident Driver",
            "Score 25,000 or more in a single Free Ride run.");

        register("free_ride_10km",
            "Road Trip",
            "Drive at least 10,000 m in a single Free Ride run.");

        register("free_ride_first_near_miss",
            "Close Call",
            "Get your first near miss in Free Ride.");

        register("free_ride_5_near_misses",
            "Threading the Needle",
            "Get 5 near misses in one Free Ride run.");

        register("free_ride_10_near_misses",
            "Traffic Dancer",
            "Get 10 near misses in one Free Ride run.");

        register("free_ride_100mph",
            "Triple Digits",
            "Reach 100 mph in Free Ride.");

        register("free_ride_120mph",
            "Flat Out",
            "Reach 120 mph in Free Ride.");

        register("endless_one_way_first_run",
            "First Endless Drive",
            "Finish an Endless One Way run for the first time.");

        register("endless_one_way_10000",
            "Endless Scorer",
            "Score 10,000 or more in Endless One Way.");

        register("endless_one_way_50000",
            "Endless Speed Demon",
            "Score 50,000 or more in Endless One Way.");

        register("endless_one_way_5km",
            "Endless Long Haul",
            "Drive at least 5,000 m in Endless One Way.");

        register("endless_one_way_300s",
            "Endless Survivor",
            "Survive for 300 seconds in Endless One Way.");

        register("endless_one_way_first_near_miss",
            "Close Call",
            "Get your first near miss in Endless One Way.");

        register("endless_two_way_first_run",
            "Two Way Starter",
            "Finish an Endless Two Way run for the first time.");

        register("endless_two_way_10000",
            "Two Way Scorer",
            "Score 10,000 or more in Endless Two Way.");

        register("endless_two_way_50000",
            "Two Way Speed Demon",
            "Score 50,000 or more in Endless Two Way.");

        register("endless_two_way_5km",
            "Two Way Long Haul",
            "Drive at least 5,000 m in Endless Two Way.");

        register("endless_two_way_300s",
            "Two Way Survivor",
            "Survive for 300 seconds in Endless Two Way.");

        register("endless_two_way_first_near_miss",
            "Two Way Close Call",
            "Get your first near miss in Endless Two Way.");

        register("endless_two_way_opposing_750",
            "Wrong Side Risk",
            "Earn 750 opposing lane bonus points in one Endless Two Way run.");

        register("endless_two_way_opposing_3000",
            "Wrong Side Expert",
            "Earn 3,000 opposing lane bonus points in one Endless Two Way run.");

        register("test_drive_first_run",
            "First Test Drive",
            "Finish a Test Drive run for the first time.");

        register("test_drive_1km",
            "Getting Comfortable",
            "Drive at least 1,000 m in Test Drive.");

        register("test_drive_3km",
            "Test Route",
            "Drive at least 3,000 m in Test Drive.");

        register("test_drive_60s",
            "One Minute Test",
            "Survive for 60 seconds in Test Drive.");

        register("test_drive_120s",
            "Extended Test",
            "Survive for 120 seconds in Test Drive.");

        register("test_drive_90mph",
            "Warming Up",
            "Reach 90 mph in Test Drive.");

        register("test_drive_120mph",
            "Pushed to the Limit",
            "Reach 120 mph in Test Drive.");

        register("test_drive_first_near_miss",
            "Practice Close Call",
            "Get your first near miss in Test Drive.");

        register("test_drive_5_near_misses",
            "Precision Practice",
            "Get 5 near misses in one Test Drive run.");
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

    public static Array<AchievementState> onEndlessTwoWayLiveUpdate(
        int score,
        int distMeters,
        float timeSeconds,
        int nearMisses,
        int opposingPoints
    ) {
        Array<AchievementState> newlyUnlocked = new Array<>();

        unlockIf(newlyUnlocked, "endless_two_way_10000", score >= 10_000);
        unlockIf(newlyUnlocked, "endless_two_way_50000", score >= 50_000);
        unlockIf(newlyUnlocked, "endless_two_way_5km", distMeters >= 5_000);
        unlockIf(newlyUnlocked, "endless_two_way_300s", timeSeconds >= 300f);
        unlockIf(newlyUnlocked, "endless_two_way_first_near_miss", nearMisses >= 1);
        unlockIf(newlyUnlocked, "endless_two_way_opposing_750", opposingPoints >= 750);
        unlockIf(newlyUnlocked, "endless_two_way_opposing_3000", opposingPoints >= 3000);

        if (newlyUnlocked.size > 0) {
            save();
        }

        return newlyUnlocked;
    }

    public static Array<AchievementState> onEndlessTwoWayFinished(
        int score,
        int distMeters,
        float timeSeconds,
        int nearMisses,
        int opposingPoints
    ) {
        Array<AchievementState> newlyUnlocked = new Array<>();

        unlockIf(newlyUnlocked, "endless_two_way_first_run", true);

        if (newlyUnlocked.size > 0) {
            save();
        }

        return newlyUnlocked;
    }

    // Called after a Free Ride run ends, returns newly unlocked achievements.
    public static Array<AchievementState> onFreeRideLiveUpdate(
        int score,
        int distMeters,
        float timeSeconds,
        int nearMisses,
        float maxSpeedMph
    ) {
        Array<AchievementState> newlyUnlocked = new Array<>();

        unlockIf(newlyUnlocked, "free_ride_10000", score >= 10_000);
        unlockIf(newlyUnlocked, "free_ride_25000", score >= 25_000);
        unlockIf(newlyUnlocked, "free_ride_50000", score >= 50_000);

        unlockIf(newlyUnlocked, "free_ride_5km", distMeters >= 5_000);
        unlockIf(newlyUnlocked, "free_ride_10km", distMeters >= 10_000);

        unlockIf(newlyUnlocked, "free_ride_300s", timeSeconds >= 300f);

        unlockIf(newlyUnlocked, "free_ride_first_near_miss", nearMisses >= 1);
        unlockIf(newlyUnlocked, "free_ride_5_near_misses", nearMisses >= 5);
        unlockIf(newlyUnlocked, "free_ride_10_near_misses", nearMisses >= 10);

        unlockIf(newlyUnlocked, "free_ride_100mph", maxSpeedMph >= 100f);
        unlockIf(newlyUnlocked, "free_ride_120mph", maxSpeedMph >= 120f);

        if (newlyUnlocked.size > 0) {
            save();
        }

        return newlyUnlocked;
    }

    public static Array<AchievementState> onFreeRideFinished(
        int score,
        int distMeters,
        float timeSeconds,
        int nearMisses,
        float maxSpeedMph
    ) {
        Array<AchievementState> newlyUnlocked =
            onFreeRideLiveUpdate(score, distMeters, timeSeconds, nearMisses, maxSpeedMph);

        unlockIf(newlyUnlocked, "free_ride_first_run", true);

        if (newlyUnlocked.size > 0) {
            save();
        }

        return newlyUnlocked;
    }

    public static Array<AchievementState> onFreeRideFinished(
        int score,
        int distMeters,
        float timeSeconds
    ) {
        return onFreeRideFinished(score, distMeters, timeSeconds, 0, 0f);
    }

    public static Array<AchievementState> onTestDriveLiveUpdate(
        int distMeters,
        float timeSeconds,
        int nearMisses,
        float maxSpeedMph
    ) {
        Array<AchievementState> newlyUnlocked = new Array<>();

        unlockIf(newlyUnlocked, "test_drive_1km", distMeters >= 1_000);
        unlockIf(newlyUnlocked, "test_drive_3km", distMeters >= 3_000);

        unlockIf(newlyUnlocked, "test_drive_60s", timeSeconds >= 60f);
        unlockIf(newlyUnlocked, "test_drive_120s", timeSeconds >= 120f);

        unlockIf(newlyUnlocked, "test_drive_90mph", maxSpeedMph >= 90f);
        unlockIf(newlyUnlocked, "test_drive_120mph", maxSpeedMph >= 120f);

        unlockIf(newlyUnlocked, "test_drive_first_near_miss", nearMisses >= 1);
        unlockIf(newlyUnlocked, "test_drive_5_near_misses", nearMisses >= 5);

        if (newlyUnlocked.size > 0) {
            save();
        }

        return newlyUnlocked;
    }

    public static Array<AchievementState> onTestDriveFinished(
        int distMeters,
        float timeSeconds,
        int nearMisses,
        float maxSpeedMph
    ) {
        Array<AchievementState> newlyUnlocked =
            onTestDriveLiveUpdate(distMeters, timeSeconds, nearMisses, maxSpeedMph);

        unlockIf(newlyUnlocked, "test_drive_first_run", true);

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

    public static Array<AchievementState> onEndlessOneWayLiveUpdate(
        int score,
        int distMeters,
        float timeSeconds,
        int nearMisses
    ) {
        Array<AchievementState> newlyUnlocked = new Array<>();

        unlockIf(newlyUnlocked, "endless_one_way_10000", score >= 10_000);
        unlockIf(newlyUnlocked, "endless_one_way_50000", score >= 50_000);
        unlockIf(newlyUnlocked, "endless_one_way_5km", distMeters >= 5_000);
        unlockIf(newlyUnlocked, "endless_one_way_300s", timeSeconds >= 300f);
        unlockIf(newlyUnlocked, "endless_one_way_first_near_miss", nearMisses >= 1);

        if (newlyUnlocked.size > 0) {
            save();
        }

        return newlyUnlocked;
    }

    public static Array<AchievementState> onEndlessOneWayFinished(
        int score,
        int distMeters,
        float timeSeconds,
        int nearMisses
    ) {
        Array<AchievementState> newlyUnlocked = new Array<>();

        unlockIf(newlyUnlocked, "endless_one_way_first_run", true);

        if (newlyUnlocked.size > 0) {
            save();
        }

        return newlyUnlocked;
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
