package org.aion.base.type;

import java.util.Arrays;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Bytesable;

public final class Hash256 implements Comparable<Hash256>, Bytesable<Hash256>, Cloneable {

    public static final int BYTES = 32;
    private static final Hash256 zeroHash = Hash256.wrap(new byte[BYTES]);

    private byte[] hash = new byte[BYTES];
    private int hashCode = 0;

    public Hash256(byte[] in) {

        if (in == null) {
            throw new IllegalArgumentException("Null input!");
        }

        if (in.length != BYTES) {
            throw new IllegalArgumentException();
        }

        setupData(in);
    }

    public Hash256(String in) {

        if (in == null) {
            throw new IllegalArgumentException("Null input!");
        }

        byte[] out = ByteUtil.hexStringToBytes(in);

        if (out.length != BYTES) {
            throw new IllegalArgumentException();
        }

        setupData(out);
    }

    public Hash256(final ByteArrayWrapper in) {

        if (in == null) {
            throw new IllegalArgumentException("Null input!");
        }

        byte[] data = in.getData();
        if (data == null || data.length != BYTES) {
            throw new IllegalArgumentException();
        }

        setupData(data);
    }

    private void setupData(final byte[] in) {
        this.hash = in;
        this.hashCode = Arrays.hashCode(in);
    }

    public static Hash256 wrap(final byte[] hash) {
        return new Hash256(hash);
    }

    public static Hash256 wrap(final String hash) {
        return new Hash256(hash);
    }

    public static Hash256 wrap(final ByteArrayWrapper hash) {
        return new Hash256(hash);
    }

    public final String toString() {
        return ByteUtil.toHexString(hash);
    }

    public final ByteArrayWrapper toByteArrayWrapper() {
        return ByteArrayWrapper.wrap(this.hash);
    }

    @Override
    public final byte[] toBytes() {
        return this.hash;
    }

    @Override
    public Hash256 clone() throws CloneNotSupportedException {
        try {
            return new Hash256(Arrays.copyOf(this.hash, BYTES));
        } catch (Exception e) {
            throw new CloneNotSupportedException(e.toString());
        }
    }

    public boolean equals(Object other) {
        if (!(other instanceof Hash256)) {
            return false;
        } else {
            byte[] otherAddress = ((Hash256) other).toBytes();
            return Arrays.compare(this.hash, otherAddress) == 0;
        }
    }

    public int hashCode() {
        return this.hashCode;
    }

    /**
     * Compares this object with the specified object for order. Returns a negative integer, zero,
     * or a positive integer as this object is less than, equal to, or greater than the specified
     * object.
     *
     * <p>
     *
     * <p>The implementor must ensure {@code sgn(x.compareTo(y)) == -sgn(y.compareTo(x))} for all
     * {@code x} and {@code y}. (This implies that {@code x.compareTo(y)} must throw an exception
     * iff {@code y.compareTo(x)} throws an exception.)
     *
     * <p>
     *
     * <p>The implementor must also ensure that the relation is transitive: {@code (x.compareTo(y) >
     * 0 && y.compareTo(z) > 0)} implies {@code x.compareTo(z) > 0}.
     *
     * <p>
     *
     * <p>Finally, the implementor must ensure that {@code x.compareTo(y)==0} implies that {@code
     * sgn(x.compareTo(z)) == sgn(y.compareTo(z))}, for all {@code z}.
     *
     * <p>
     *
     * <p>It is strongly recommended, but <i>not</i> strictly required that {@code
     * (x.compareTo(y)==0) == (x.equals(y))}. Generally speaking, any class that implements the
     * {@code Comparable} interface and violates this condition should clearly indicate this fact.
     * The recommended language is "Note: this class has a natural ordering that is inconsistent
     * with equals."
     *
     * <p>
     *
     * <p>In the foregoing description, the notation {@code sgn(}<i>expression</i>{@code )}
     * designates the mathematical <i>signum</i> function, which is defined to return one of {@code
     * -1}, {@code 0}, or {@code 1} according to whether the value of <i>expression</i> is negative,
     * zero, or positive, respectively.
     *
     * @param o the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object is less than, equal
     *     to, or greater than the specified object.
     * @throws NullPointerException if the specified object is null
     * @throws ClassCastException if the specified object's type prevents it from being compared to
     *     this object.
     */
    @Override
    public int compareTo(Hash256 o) {
        return Arrays.compare(this.hash, o.toBytes());
    }

    @Override
    public final Hash256 fromBytes(byte[] bs) {
        return new Hash256(bs);
    }

    public static final Hash256 ZERO_HASH() {
        return zeroHash;
    }
}
