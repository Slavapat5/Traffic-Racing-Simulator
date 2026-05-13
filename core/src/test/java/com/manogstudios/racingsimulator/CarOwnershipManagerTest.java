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

        assertTrue(CarOwnershipManager.ownsCar("2014 Sazda FX5 Shiatto - Light Red.png"));
    }

    @Test
    void addCar_addsNewCar() {
        CarOwnershipManager.addCar("2025 DMV 365e - Grey.png");

        assertTrue(CarOwnershipManager.ownsCar("2025 DMV 365e - Grey.png"));
    }

    @Test
    void addCar_doesNotCreateDuplicates() {
        CarOwnershipManager.addCar("2025 DMV 365e - Grey.png");
        CarOwnershipManager.addCar("2025 DMV 365e - Grey.png");

        Set<String> owned = CarOwnershipManager.getOwnedCars();
        long count = owned.stream().filter(c -> c.equals("2025 DMV 365e - Grey.png")).count();

        assertEquals(1, count);
    }

    @Test
    void removeCar_removesOwnedCar() {
        CarOwnershipManager.addCar("2025 DMV 365e - Grey.png");
        CarOwnershipManager.removeCar("2025 DMV 365e - Grey.png");

        assertFalse(CarOwnershipManager.ownsCar("2025 DMV 365e - Grey.png"));
    }

    @Test
    void clearOwnedCars_emptiesCollection() {
        CarOwnershipManager.addCar("2025 DMV 365e - Grey.png");
        CarOwnershipManager.addCar("2010 Sedecrem STR Solaren - Silver.png");

        CarOwnershipManager.clearOwnedCars();

        assertTrue(CarOwnershipManager.getOwnedCars().isEmpty());
    }

    @Test
    void saveAndLoadOwnedCars_restoresSavedCars() {
        CarOwnershipManager.setCurrentUser("save_load_case_" + System.nanoTime());

        CarOwnershipManager.addCar("2025 DMV 365e - Grey.png");
        CarOwnershipManager.addCar("2010 Sedecrem STR Solaren - Silver.png");

        CarOwnershipManager.getOwnedCars().clear();

        CarOwnershipManager.loadOwnedCars();

        assertTrue(CarOwnershipManager.ownsCar("2025 DMV 365e - Grey.png"));
        assertTrue(CarOwnershipManager.ownsCar("2010 Sedecrem STR Solaren - Silver.png"));
    }
}
