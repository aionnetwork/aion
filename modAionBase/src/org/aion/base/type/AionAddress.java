package org.aion.base.type;

import java.util.Arrays;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Bytesable;

/**
 * The address class is a byte array wrapper represent fixed-32bytes array for the kernel account
 * (public key) has more security compare with 20bytes address blockchain system.
 *
 * @author jay
 */
public final class AionAddress
        implements org.aion.vm.api.interfaces.Address,
                Comparable<AionAddress>,
                Bytesable<AionAddress>,
                Cloneable {
    private static final AionAddress zeroAddr = AionAddress.wrap(new byte[SIZE]);
    private static final AionAddress emptyAddr = AionAddress.wrap(new byte[0]);

    private byte[] address;
    private int hashCode = 0;

    public AionAddress(final byte[] in) {

        if (in == null) {
            throw new IllegalArgumentException("Null input!");
        }

        if (in.length != SIZE && in.length != 0) {
            throw new IllegalArgumentException();
        }

        setupData(in);
    }

    public AionAddress(final ByteArrayWrapper in) {

        if (in == null) {
            throw new IllegalArgumentException("Null input!");
        }

        byte[] data = in.getData();
        if (data == null || (data.length != SIZE && data.length != 0)) {
            throw new IllegalArgumentException();
        }

        setupData(data);
    }

    public AionAddress(final String in) {

        if (in == null) {
            throw new IllegalArgumentException();
        }

        byte[] hexByte = ByteUtil.hexStringToBytes(in);

        if (hexByte.length != SIZE && hexByte.length != 0) {
            throw new IllegalArgumentException();
        }

        setupData(hexByte);
    }

    private void setupData(final byte[] in) {
        this.address = in;
        this.hashCode = Arrays.hashCode(in);
    }

    public static AionAddress wrap(final byte[] addr) {
        return new AionAddress(addr);
    }

    public static AionAddress wrap(final String addr) {
        return new AionAddress(addr);
    }

    public static AionAddress wrap(final ByteArrayWrapper addr) {
        return new AionAddress(addr);
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
    public final AionAddress clone() {
        if (this.address.length == 0) {
            return emptyAddr;
        } else {
            return new AionAddress(Arrays.copyOf(this.address, SIZE));
        }
    }

    public boolean equals(Object other) {
        if (!(other instanceof AionAddress)) {
            return false;
        } else {
            return Arrays.equals(this.address, ((AionAddress) other).toBytes());
        }
    }

    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public int compareTo(AionAddress o) {
        return Arrays.compare(this.address, o.toBytes());
    }

    public int compareTo(byte[] o) {
        return Arrays.compare(this.address, o);
    }

    @Override
    public final AionAddress fromBytes(byte[] bs) {
        return new AionAddress(bs);
    }

    public static AionAddress ZERO_ADDRESS() {
        return zeroAddr;
    }

    public static AionAddress EMPTY_ADDRESS() {
        return emptyAddr;
    }

    public boolean isEmptyAddress() {
        return Arrays.equals(address, emptyAddr.toBytes());
    }

    public boolean isZeroAddress() {
        return Arrays.equals(address, zeroAddr.toBytes());
    }
}
