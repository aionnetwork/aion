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

package org.aion.crypto;

import static org.junit.Assert.assertEquals;

import java.util.Optional;
import org.aion.base.util.ByteUtil;
import org.junit.Test;

public class ChecksumTest {

    @Test
    public void testComputeA0Address() {
        byte[] input = HashUtil.h256(Integer.toHexString(0).getBytes());
        String input_address = ByteUtil.toHexString((HashUtil.h256(input)));
        String output_address = ByteUtil.toHexString(AddressSpecs.computeA0Address(input));
        assertEquals(output_address.substring(2), input_address.substring(2));
    }

    @Test
    public void testChecksum() {
        String input = "0xa08896b9366f09e5efb1fa2ed9f3820b865ae97adbc6f364d691eb17784c9b1b";
        String expected = "A08896B9366f09e5EfB1fA2ed9f3820B865AE97ADBc6f364D691eB17784c9b1b";
        assertEquals(Optional.of(expected), (AddressSpecs.checksummedAddress(input)));
    }

    @Test
    public void testChecksum0x() {
        String input =
                "a0x896b9366f09e5efb1fa2ed9f3820b865ae97adbc6f364d691eb17784c9b1b"; // 0x is not
                                                                                    // removed, h ==
                                                                                    // null
        assertEquals(Optional.empty(), (AddressSpecs.checksummedAddress(input)));
    }

    @Test
    public void testChecksumnull() {
        String input = "0xa~8896b9366f09e5efb1fa2ed9f3820b865ae97adbc6f364d691eb17784c9b1b";
        assertEquals(Optional.empty(), (AddressSpecs.checksummedAddress(input)));
    }
}
