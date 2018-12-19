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

import org.aion.base.util.TypeConverter;

public class AddressUtils {

    public static boolean isValid(final String address) {
        return address != null && !address.isEmpty() && isAddress(address);
    }

    public static boolean equals(final String addrOne, final String addrTwo) {
        return TypeConverter.toJsonHex(addrOne).equals(TypeConverter.toJsonHex(addrTwo));
    }

    private static boolean isAddress(final String address) {
        final boolean isFull = address.startsWith("0xa0") && address.length() == 66;
        final boolean isStripped = address.startsWith("a0") && address.length() == 64;
        final String strippedAddress = isFull ? address.substring(2) : (isStripped ? address : "");
        return strippedAddress.matches("[0-9a-f]+");
    }
}
