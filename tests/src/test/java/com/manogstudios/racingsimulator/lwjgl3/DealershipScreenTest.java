package com.manogstudios.racingsimulator.lwjgl3;

import com.badlogic.gdx.Game;
import com.manogstudios.racingsimulator.CarData;
import com.manogstudios.racingsimulator.CarOwnershipManager;
import com.manogstudios.racingsimulator.CashManager;
import com.manogstudios.racingsimulator.DealershipScreen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DealershipScreenTest {

    private DealershipScreen dealership;
    private CarData testCar;

    @BeforeEach
    public void setup() {
        // Dummy game object
        dealership = new DealershipScreen(new DummyGame());

        // Reset shared game state
        CashManager.setCash(10_000); // Set initial cash
        CarOwnershipManager.clearOwnedCars(); // Clear ownership

        // Dummy car
        testCar = new CarData();
        testCar.title = "Test Car";
        testCar.description = "A fast test car";
        testCar.image = "test_car.png";
        testCar.price = 5000;
    }

    @Test
    void testFormatCash_withThousands() {
        DealershipScreen screen = new DealershipScreen(new DummyGame());
        String formatted = screen.formatCash(1234567);
        assertEquals("1,234,567", formatted);
    }

    @Test
    public void testCashFormatting() {
        DealershipScreen screen = new DealershipScreen(new DummyGame());
        assertEquals("1,000", screen.formatCash(1000));
        assertEquals("12,345", screen.formatCash(12345));
        assertEquals("0", screen.formatCash(0));
    }



    @Test
    public void testBuyCarWithInsufficientCash() {
        CashManager.setCash(1000); // Not enough

        boolean bought = dealership.tryBuyCar(testCar);

        assertFalse(bought, "Car should not be bought if cash is insufficient");
        assertFalse(CarOwnershipManager.ownsCar(testCar.image), "Car should not be added to ownership");
        assertEquals(1000, CashManager.getCash(), "Cash should remain unchanged");
    }

    static class DummyGame extends Game {
        @Override public void create() {}
    }
}
