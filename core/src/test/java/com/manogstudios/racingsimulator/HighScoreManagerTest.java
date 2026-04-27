package com.manogstudios.racingsimulator;

import com.manogstudios.racingsimulator.network.SupabaseAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class HighScoreManagerTest extends GdxTestBase {

    @BeforeEach
    void setUp() {
        SupabaseAuth.isLoggedIn = false; // avoids cloud submit path
        HighScoreManager.setCurrentUser("test_highscores");
        HighScoreManager.resetAll();
        HighScoreManager.clearScores();
    }

    @Test
    void getHighScore_returnsZeroForUnknownMode() {
        assertEquals(0, HighScoreManager.getHighScore("free_ride"));
    }

    @Test
    void submitScore_savesFirstScore() {
        HighScoreManager.submitScore("free_ride", 1200);
        assertEquals(1200, HighScoreManager.getHighScore("free_ride"));
    }

    @Test
    void submitScore_higherScoreReplacesOldScore() {
        HighScoreManager.submitScore("free_ride", 1200);
        HighScoreManager.submitScore("free_ride", 1500);

        assertEquals(1500, HighScoreManager.getHighScore("free_ride"));
    }

    @Test
    void submitScore_lowerScoreDoesNotReplaceOldScore() {
        HighScoreManager.submitScore("free_ride", 1200);
        HighScoreManager.submitScore("free_ride", 900);

        assertEquals(1200, HighScoreManager.getHighScore("free_ride"));
    }

    @Test
    void clearScores_removesScoresFromMemory() {
        HighScoreManager.submitScore("free_ride", 1000);
        HighScoreManager.clearScores();

        assertEquals(0, HighScoreManager.getHighScore("free_ride"));
    }

    @Test
    void getAllScores_returnsCopyNotOriginalMap() {
        HighScoreManager.submitScore("free_ride", 2000);

        Map<String, Integer> copy = HighScoreManager.getAllScores();
        copy.put("free_ride", 1);

        assertEquals(2000, HighScoreManager.getHighScore("free_ride"));
    }

    @Test
    void saveAndLoadHighScores_restoresSavedValues() {
        HighScoreManager.submitScore("free_ride", 2222);
        HighScoreManager.submitScore("time_trial", 3333);

        HighScoreManager.clearScores();
        HighScoreManager.loadHighScores();

        assertEquals(2222, HighScoreManager.getHighScore("free_ride"));
        assertEquals(3333, HighScoreManager.getHighScore("time_trial"));
    }
}
