/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

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
