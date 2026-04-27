package com.manogstudios.racingsimulator;

import com.manogstudios.racingsimulator.network.SupabaseAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class CarOwnershipManagerTest extends GdxTestBase {

    @BeforeEach
    void setUp() {
        SupabaseAuth.isLoggedIn = false;
        CarOwnershipManager.setCurrentUser("test_ownedcars_" + System.nanoTime());
        CarOwnershipManager.clearOwnedCars();
    }

    @Test
    void loadOwnedCars_addsStarterCarWhenNoFileExists() {
        CarOwnershipManager.setCurrentUser("starter_case_" + System.nanoTime());
        CarOwnershipManager.loadOwnedCars();

        assertTrue(CarOwnershipManager.ownsCar("Mazda MX-5 Miata - 2014.png"));
    }

    @Test
    void addCar_addsNewCar() {
        CarOwnershipManager.addCar("BMW 330i - 2025.png");

        assertTrue(CarOwnershipManager.ownsCar("BMW 330i - 2025.png"));
    }

    @Test
    void addCar_doesNotCreateDuplicates() {
        CarOwnershipManager.addCar("BMW 330i - 2025.png");
        CarOwnershipManager.addCar("BMW 330i - 2025.png");

        Set<String> owned = CarOwnershipManager.getOwnedCars();
        long count = owned.stream().filter(c -> c.equals("BMW 330i - 2025.png")).count();

        assertEquals(1, count);
    }

    @Test
    void removeCar_removesOwnedCar() {
        CarOwnershipManager.addCar("BMW 330i - 2025.png");
        CarOwnershipManager.removeCar("BMW 330i - 2025.png");

        assertFalse(CarOwnershipManager.ownsCar("BMW 330i - 2025.png"));
    }

    @Test
    void clearOwnedCars_emptiesCollection() {
        CarOwnershipManager.addCar("BMW 330i - 2025.png");
        CarOwnershipManager.addCar("Audi RS5 Coupe - 2024.png");

        CarOwnershipManager.clearOwnedCars();

        assertTrue(CarOwnershipManager.getOwnedCars().isEmpty());
    }

    @Test
    void saveAndLoadOwnedCars_restoresSavedCars() {
        CarOwnershipManager.setCurrentUser("save_load_case_" + System.nanoTime());

        CarOwnershipManager.addCar("BMW 330i - 2025.png");
        CarOwnershipManager.addCar("Audi RS5 Coupe - 2024.png");

        CarOwnershipManager.getOwnedCars().clear();

        CarOwnershipManager.loadOwnedCars();

        assertTrue(CarOwnershipManager.ownsCar("BMW 330i - 2025.png"));
        assertTrue(CarOwnershipManager.ownsCar("Audi RS5 Coupe - 2024.png"));
    }
}
