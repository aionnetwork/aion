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
package org.aion.wallet.util;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class AionAddressUtilsTest {
    @Test
    public void isValidWhenInputIsValid() {
        String full = "0xa0cafecafe111111111111111111111111111111111111111111111111111111";
        assertThat(AddressUtils.isValid(full), is(true));
        String stripped = "a0cafecafe111111111111111111111111111111111111111111111111111111";
        assertThat(AddressUtils.isValid(stripped), is(true));
    }

    @Test
    public void isValidWhenInputNotValid() {
        String tooShort = "0xa0cafecafe11111111111111111111111111111111111111111111111111111";
        assertThat(AddressUtils.isValid(tooShort), is(false));
        String tooLong = "0xa0cafecafe11111111111111111111111111111111111111111111111111111";
        assertThat(AddressUtils.isValid(tooLong), is(false));
        String uppercase = "0xa0CAFecafe11111111111111111111111111111111111111111111111111111";
        assertThat(AddressUtils.isValid(uppercase), is(false));
        String badPrefix1 = "xa0cafecafe1111111111111111111111111111111111111111111111111111111";
        assertThat(AddressUtils.isValid(badPrefix1), is(false));
        String badPrefix2 = "a1cafecafe111111111111111111111111111111111111111111111111111111";
        assertThat(AddressUtils.isValid(badPrefix2), is(false));
        String badPrefix3 = "0xa1cafecafe111111111111111111111111111111111111111111111111111111";
        assertThat(AddressUtils.isValid(badPrefix2), is(false));
    }
}
