package org.aion.base.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AddressValidationTest {

    @Test
    public void allZeroPrefix() {
        String addr = "0x0000000000000000000000000000000000000000000000000000000000000000";

        assertEquals(false, Utils.isValidAddress(addr));
    }

    @Test
    public void allZeroNoPrefix() {
        String addr = "0000000000000000000000000000000000000000000000000000000000000000";

        assertEquals(false, Utils.isValidAddress(addr));
    }

    @Test
    public void burnPrefix() {
        String addr = "0xa000000000000000000000000000000000000000000000000000000000000000";

        assertEquals(true, Utils.isValidAddress(addr));
    }

    @Test
    public void burnNoPrefix() {
        String addr = "a000000000000000000000000000000000000000000000000000000000000000";

        assertEquals(true, Utils.isValidAddress(addr));
    }

    @Test
    public void previouslyGeneratedPrefix() {
        String addr = "0xa0ad207b4ae29a4e6219a8a8a1a82310de491194a33bd95907515a3c2196f357";
        assertEquals(true, Utils.isValidAddress(addr));
    }

    @Test
    public void previouslyGeneratedNoPrefix() {
        String addr = "a0ad207b4ae29a4e6219a8a8a1a82310de491194a33bd95907515a3c2196f357";
        assertEquals(true, Utils.isValidAddress(addr));
    }

    @Test
    public void incorrectLength() {
        String addr = "a0ad207b4ae29a4e6219a8a8a1a82310de491194a33bd95907515a3c2196f3";
        assertEquals(false, Utils.isValidAddress(addr));
    }
}
