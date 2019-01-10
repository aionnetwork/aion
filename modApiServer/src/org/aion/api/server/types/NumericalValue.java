package org.aion.api.server.types;

import java.math.BigInteger;
import java.util.regex.Pattern;
import org.aion.base.util.ByteUtil;

/** Base type for a numerical value derived from some JSON string, or vice versa */
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
