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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/
package org.aion.base.type;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Bytesable;
import org.aion.base.util.FastByteComparisons;

import java.util.Arrays;

/**
 * The address class is a byte array wrapper represent fixed-32bytes array for
 * the kernel account (public key) has more security compare with 20bytes
 * address blockchain system.
 * 
 * @author jay
 *
 */
public final class Address implements Comparable<Address>, Bytesable<Address>, Cloneable {

    public static final int ADDRESS_LEN = 32;
    private static final Address zeroAddr = Address.wrap(new byte[ADDRESS_LEN]);
    private static final Address emptyAddr = Address.wrap(new byte[0]);

    private byte[] address;
    private int hashCode = 0;

    public Address(final byte[] in) {

        if (in == null) {
            throw new IllegalArgumentException("Null input!");
        }

        if (in.length != ADDRESS_LEN && in.length != 0) {
            throw new IllegalArgumentException();
        }

        setupData(in);
    }

    public Address(final ByteArrayWrapper in) {

        if (in == null) {
            throw new IllegalArgumentException("Null input!");
        }

        byte[] data = in.getData();
        if (data == null || (data.length != ADDRESS_LEN && data.length != 0)) {
            throw new IllegalArgumentException();
        }

        setupData(data);
    }

    public Address(final String in) {

        if (in == null) {
            throw new IllegalArgumentException();
        }

        byte[] hexByte = ByteUtil.hexStringToBytes(in);

        if (hexByte.length != ADDRESS_LEN && hexByte.length != 0) {
            throw new IllegalArgumentException();
        }

        setupData(hexByte);
    }

    private void setupData(final byte[] in) {
        this.address = in;
        this.hashCode = Arrays.hashCode(in);
    }

    public static final Address wrap(final byte[] addr) {
        return new Address(addr);
    }

    public static final Address wrap(final String addr) {
        return new Address(addr);
    }

    public static final Address wrap(final ByteArrayWrapper addr) {
        return new Address(addr);
    }

    public final String toString() {
        return ByteUtil.toHexString(address);
    }

    public final ByteArrayWrapper toByteArrayWrapper() {
        return ByteArrayWrapper.wrap(this.address);
    }

    @Override
    public final byte[] toBytes() {
        return this.address;
    }

    @Override
    public final Address clone() {
        if (this.address.length == 0) {
            return emptyAddr;
        } else {
            return new Address(Arrays.copyOf(this.address, ADDRESS_LEN));
        }
    }

    public boolean equals(Object other) {
        if (!(other instanceof Address)) {
            return false;
        } else {
            byte[] otherAddress = ((Address) other).toBytes();
            return FastByteComparisons.compareTo(this.address, 0, this.address.length, otherAddress, 0,
                    otherAddress.length) == 0;
        }
    }

    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public int compareTo(Address o) {
        return FastByteComparisons.compareTo(this.address, 0, ADDRESS_LEN, o.toBytes(), 0, o.toBytes().length);
    }

    public int compareTo(byte[] o) {
        return FastByteComparisons.compareTo(this.address, 0, ADDRESS_LEN, o, 0, o.length);
    }

    @Override
    public final Address fromBytes(byte[] bs) {
        return new Address(bs);
    }

    public static final Address ZERO_ADDRESS() {
        return zeroAddr;
    }

    public static final Address EMPTY_ADDRESS() {
        return emptyAddr;
    }
}
