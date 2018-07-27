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
package org.aion.rlp;

import static com.google.common.truth.Truth.assertThat;

import org.aion.base.util.Hex;
import org.junit.Test;

public class UtilsTest {

    @Test
    public void testHexEncode_wNull() {
        assertThat(Utils.hexEncode(null)).isNull();
    }

    @Test
    public void testHexEncode_wSingleByte() {
        for (byte b : Utils.encodingTable) {
            byte[] input = new byte[] {b};
            assertThat(Utils.hexEncode(input)).isEqualTo(Hex.encode(input));
        }
    }

    @Test
    public void testHexEncode_wString() {
        String value = "1234567890abcdef";
        byte[] input = Hex.decode(value);

        assertThat(Utils.hexEncode(input)).isEqualTo(Hex.encode(input));
        assertThat(Hex.decode(Utils.hexEncode(input))).isEqualTo(input);
    }

    @Test(expected = NullPointerException.class)
    public void testConcatenate_wNullA() {
        Utils.concatenate(new byte[] {1, 2}, null);
    }

    @Test(expected = NullPointerException.class)
    public void testConcatenate_wNullB() {
        Utils.concatenate(null, new byte[] {1, 2});
    }

    @Test
    public void testConcatenate() {
        assertThat(Utils.concatenate(new byte[] {1, 2}, new byte[] {3, 4}))
                .isEqualTo(new byte[] {1, 2, 3, 4});
    }
}
