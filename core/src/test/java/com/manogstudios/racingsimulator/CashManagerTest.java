package com.manogstudios.racingsimulator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CashManagerTest {

    @BeforeEach
    void setUp() {
        CashManager.enableSaving = false;
        CashManager.enableCloudSync = false;
        CashManager.setCurrentUser("test_cash");
        CashManager.setCash(10000);
    }

    @Test
    void addCash_increasesCashCorrectly() {
        CashManager.addCash(500);
        assertEquals(10500, CashManager.getCash());
    }

    @Test
    void subtractCash_returnsTrueAndReducesCash_whenEnoughCashExists() {
        boolean result = CashManager.subtractCash(3000);

        assertTrue(result);
        assertEquals(7000, CashManager.getCash());
    }

    @Test
    void subtractCash_returnsFalseAndDoesNotChangeCash_whenNotEnoughCashExists() {
        boolean result = CashManager.subtractCash(15000);

        assertFalse(result);
        assertEquals(10000, CashManager.getCash());
    }

    @Test
    void setCash_updatesCashDirectly() {
        CashManager.setCash(25000);
        assertEquals(25000, CashManager.getCash());
    }

    @Test
    void subtractCash_allowsExactAmount() {
        boolean result = CashManager.subtractCash(10000);

        assertTrue(result);
        assertEquals(0, CashManager.getCash());
    }

    @Test
    void addCash_zeroLeavesCashUnchanged() {
        CashManager.addCash(0);
        assertEquals(10000, CashManager.getCash());
    }

    @Test
    void addCash_handlesNegativeValueAsDecrease() {
        CashManager.addCash(-500);
        assertEquals(9500, CashManager.getCash());
    }
}
