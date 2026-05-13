package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.manogstudios.racingsimulator.network.SupabaseAuth;
import com.manogstudios.racingsimulator.network.SupabaseGameData;

import java.util.HashMap;
import java.util.Map;

public class HighScoreManager {

    private static String FILE_PATH = "highscores_default.txt";
    private static final Map<String, Integer> scores = new HashMap<>();

    public static void setCurrentUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            FILE_PATH = "highscores_default.txt";
        } else {
            FILE_PATH = "highscores_" + userId + ".txt";
        }
    }

    public static void loadHighScores() {
        scores.clear();

        FileHandle file = Gdx.files.local(FILE_PATH);
        if (!file.exists()) return;

        String content = file.readString().trim();
        if (content.isEmpty()) return;

        String[] lines = content.split("\n");
        for (String line : lines) {
            String[] parts = line.split("=");
            if (parts.length == 2) {
                String modeKey = parts[0].trim();
                try {
                    int value = Integer.parseInt(parts[1].trim());
                    scores.put(modeKey, value);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void saveHighScores() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            builder.append(entry.getKey())
                .append("=")
                .append(entry.getValue())
                .append("\n");
        }
        FileHandle file = Gdx.files.local(FILE_PATH);
        file.writeString(builder.toString(), false);
    }

    //Used by Supabase loader to clear before repopulating
    public static void clearScores() {
        scores.clear();
    }

    // Used when loading values from Supabase
    public static void setScoreFromCloud(String modeKey, int score) {
        scores.put(modeKey, score);
    }

    public static int getHighScore(String modeKey) {
        return scores.getOrDefault(modeKey, 0);
    }

    public static void submitScore(String modeKey, int newScore) {
        int current = getHighScore(modeKey);
        if (newScore > current) {
            scores.put(modeKey, newScore);
            saveHighScores();

            // Push to Supabase using Edge Function
            if (SupabaseAuth.isLoggedIn) {
                SupabaseGameData.submitScore(modeKey, newScore);
            }
        }
    }


    // Reset local highs for this user (file + in-memory)
    public static void resetAll() {
        scores.clear();
        saveHighScores();
    }

    public static Map<String, Integer> getAllScores() {
        return new HashMap<>(scores);
    }
}
