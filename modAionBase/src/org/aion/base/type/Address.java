package org.aion.base.type;

import java.util.Arrays;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Bytesable;
import org.aion.base.util.FastByteComparisons;

/**
 * The address class is a byte array wrapper represent fixed-32bytes array for the kernel account
 * (public key) has more security compare with 20bytes address blockchain system.
 *
 * @author jay
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

    public static Address wrap(final byte[] addr) {
        return new Address(addr);
    }

    public static Address wrap(final String addr) {
        return new Address(addr);
    }

    public static Address wrap(final ByteArrayWrapper addr) {
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
            return FastByteComparisons.compareTo(
                            this.address,
                            0,
                            this.address.length,
                            otherAddress,
                            0,
                            otherAddress.length)
                    == 0;
        }
    }

    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public int compareTo(Address o) {
        return FastByteComparisons.compareTo(
                this.address, 0, ADDRESS_LEN, o.toBytes(), 0, o.toBytes().length);
    }

    public int compareTo(byte[] o) {
        return FastByteComparisons.compareTo(this.address, 0, ADDRESS_LEN, o, 0, o.length);
    }

    @Override
    public final Address fromBytes(byte[] bs) {
        return new Address(bs);
    }

    public static Address ZERO_ADDRESS() {
        return zeroAddr;
    }

    public static Address EMPTY_ADDRESS() {
        return emptyAddr;
    }

    public boolean isEmptyAddress() {
        return Arrays.equals(address, emptyAddr.toBytes());
    }

    public boolean isZeroAddress() {
        return Arrays.equals(address, zeroAddr.toBytes());
    }
}
