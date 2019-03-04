package org.aion.util.bytes;

import org.aion.util.string.StringUtils;
import org.junit.Assert;
import org.junit.Test;

public class AddressValidationTest {

    @Test
    public void allZeroPrefix() {
        String addr = "0x0000000000000000000000000000000000000000000000000000000000000000";

        Assert.assertFalse(StringUtils.isValidAddress(addr));
    }

    @Test
    public void allZeroNoPrefix() {
        String addr = "0000000000000000000000000000000000000000000000000000000000000000";

        Assert.assertFalse(StringUtils.isValidAddress(addr));
    }

    @Test
    public void burnPrefix() {
        String addr = "0xa000000000000000000000000000000000000000000000000000000000000000";

        Assert.assertTrue(StringUtils.isValidAddress(addr));
    }

    @Test
    public void burnNoPrefix() {
        String addr = "a000000000000000000000000000000000000000000000000000000000000000";

        Assert.assertTrue(StringUtils.isValidAddress(addr));
    }

    @Test
    public void previouslyGeneratedPrefix() {
        String addr = "0xa0ad207b4ae29a4e6219a8a8a1a82310de491194a33bd95907515a3c2196f357";
        Assert.assertTrue(StringUtils.isValidAddress(addr));
    }

    @Test
    public void previouslyGeneratedNoPrefix() {
        String addr = "a0ad207b4ae29a4e6219a8a8a1a82310de491194a33bd95907515a3c2196f357";
        Assert.assertTrue(StringUtils.isValidAddress(addr));
    }

    @Test
    public void incorrectLength() {
        String addr = "a0ad207b4ae29a4e6219a8a8a1a82310de491194a33bd95907515a3c2196f3";
        Assert.assertFalse(StringUtils.isValidAddress(addr));
    }
}
