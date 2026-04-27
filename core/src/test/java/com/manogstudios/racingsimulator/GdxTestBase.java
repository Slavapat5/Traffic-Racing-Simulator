package com.manogstudios.racingsimulator;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public abstract class GdxTestBase {

    private static HeadlessApplication application;

    @BeforeAll
    static void initGdx() {
        if (Gdx.app == null) {
            application = new HeadlessApplication(new ApplicationAdapter() {}, new HeadlessApplicationConfiguration());
        }
    }

    @AfterAll
    static void cleanupGdx() {
        if (application != null) {
            application.exit();
            application = null;
        }
    }
}
