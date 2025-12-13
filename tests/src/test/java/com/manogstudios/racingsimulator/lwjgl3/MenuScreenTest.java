package com.manogstudios.racingsimulator.lwjgl3;



import static org.junit.jupiter.api.Assertions.assertNotNull;


import com.manogstudios.racingsimulator.MenuScreen;
import org.junit.jupiter.api.Test;


public class MenuScreenTest {

    @Test
    public void testMenuScreenCreation() {
        DummyGame game = new DummyGame();
        MenuScreen screen = new MenuScreen(game);
        assertNotNull(screen);
    }

    static class DummyGame extends com.badlogic.gdx.Game {
        @Override
        public void create() {
            // No-op
        }
    }
}
