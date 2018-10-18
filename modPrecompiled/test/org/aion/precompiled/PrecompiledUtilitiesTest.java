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
package org.aion.precompiled;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class PrecompiledUtilitiesTest {

    @Test
    public void testPad_wLargerInputLength() {
        byte[] input = new byte[]{1, 2, 3};
        int newLength = input.length - 1;
        // returns null
        assertThat(PrecompiledUtilities.pad(input, newLength)).isNull();
    }

    @Test
    public void testPad_wEqualLengths() {
        byte[] input = new byte[]{1, 2, 3};
        int newLength = input.length;
        // unchanged
        assertThat(PrecompiledUtilities.pad(input, newLength)).isEqualTo(input);
    }

    @Test
    public void testPad_wSmallerInputLength() {
        byte[] input = new byte[]{1, 2, 3};
        int newLength = input.length + 1;
        byte[] expected = new byte[]{0, 1, 2, 3};
        assertThat(PrecompiledUtilities.pad(input, newLength)).isEqualTo(expected);
    }
}
