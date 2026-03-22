package com.manogstudios.racingsimulator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Preferences;

public class GameSettings {

    private static final String PREFS_NAME = "TrafficRacingSimulatorSettings";
    private static final String FULLSCREEN_KEY = "fullscreen";

    private static Preferences getPrefs() {
        return Gdx.app.getPreferences(PREFS_NAME);
    }

    public static boolean isFullscreenEnabled() {
        return getPrefs().getBoolean(FULLSCREEN_KEY, false);
    }

    public static void setFullscreenEnabled(boolean enabled) {
        getPrefs().putBoolean(FULLSCREEN_KEY, enabled);
        getPrefs().flush();
    }

    public static void applyDisplaySettings() {
        if (isFullscreenEnabled()) {
            Graphics.DisplayMode displayMode = Gdx.graphics.getDisplayMode();
            Gdx.graphics.setFullscreenMode(displayMode);
        } else {
            Gdx.graphics.setWindowedMode(1600, 900);
        }
    }
}
