/*******************************************************************************
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
 *     
 ******************************************************************************/

package org.aion.api.server.types;

import org.aion.base.util.ByteUtil;

import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * Base type for a numerical value derived from some JSON string, or vice versa
 */
public class NumericalValue {

    private static final Pattern numericPattern = Pattern.compile("(0x)?[0-9a-fA-F]+$");

    public static NumericalValue EMPTY = new NumericalValue("");
    private final BigInteger value;
    private String cachedStringValue;

    public NumericalValue(String in) {
        if (in.isEmpty()) {
            value = BigInteger.ZERO;
            return;
        }

        if (numericPattern.matcher(in).matches()) {
            // hexadecimal string
            value = ByteUtil.bytesToBigInteger(ByteUtil.hexStringToBytes(in));
        } else {
            // otherwise assume that this is an numeric string
            value = new BigInteger(in, 10);
        }
    }

    public NumericalValue(long in) {
        this.value = BigInteger.valueOf(in);
    }

    public NumericalValue(BigInteger in) {
        this.value = in;
    }

    public NumericalValue(byte[] in) {
        this.value = ByteUtil.bytesToBigInteger(in);
    }

    private void generateIntermediateState() {
        if (this.cachedStringValue == null)
            this.cachedStringValue = ByteUtil.toHexStringWithPrefix(this.value.toByteArray());
    }

    public String toHexString() {
        generateIntermediateState();
        return this.cachedStringValue;
    }

    public BigInteger toBigInteger() {
        return this.value;
    }

    @Override
    public String toString() {
        return toHexString();
    }
}
