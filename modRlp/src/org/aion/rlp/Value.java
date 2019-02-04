package org.aion.rlp;

import static org.aion.rlp.Utils.EMPTY_BYTE_ARRAY;
import static org.aion.rlp.Utils.nibblesToPrettyString;
import static org.aion.rlp.Utils.oneByteToHexString;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

/**
 * Class to encapsulate an object and provide utilities for conversion
 *
 * @author ethereumJ 2014
 * @author modified by aion 2017
 */
public class Value {

    private Object value;
    private byte[] rlp;

    private boolean decoded = false;

    public static Value fromRlpEncoded(byte[] data) {

        if (data != null && data.length != 0) {
            Value v = new Value();
            v.init(data);
            return v;
        }
        return null;
    }

    private Value() {}

    private void init(byte[] rlp) {
        this.rlp = rlp;
    }

    public Value(Object obj) {

        this.decoded = true;
        if (obj == null) {
            return;
        }

        if (obj instanceof Value) {
            this.value = ((Value) obj).asObj();
        } else {
            this.value = obj;
        }
    }

    // Convert

    public Object asObj() {
        decode();
        return value;
    }

    public List<Object> asList() {
        decode();
        Object[] valueArray = (Object[]) value;
        return Arrays.asList(valueArray);
    }

    /**
     * @return the numerical value as an {@link Integer}. If called for values that are not integers
     *     or byte arrays it will return 0.
     */
    public int asInt() {
        decode();
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (isBytes()) {
            return new BigInteger(1, asBytes()).intValue();
        }
        return 0;
    }

    /**
     * @return the numerical value as a {@link Long}. If called for values that are not integers or
     *     byte arrays it will return 0.
     */
    public long asLong() {
        decode();
        if (value instanceof Long) {
            return (Long) value;
        } else if (isBytes()) {
            return new BigInteger(1, asBytes()).longValue();
        }
        return 0;
    }

    /**
     * @return the numerical value as a {@link BigInteger}. If called for values that are not
     *     numbers or byte arrays it will return {@link BigInteger#ZERO}.
     */
    public BigInteger asBigInt() {
        decode();
        if (value instanceof BigInteger) {
            return (BigInteger) value;
        } else if (isBytes()) {
            return new BigInteger(1, asBytes());
        } else if (value instanceof Byte) {
            return BigInteger.valueOf((Byte) value);
        } else if (value instanceof Short) {
            return BigInteger.valueOf((Short) value);
        } else if (value instanceof Integer) {
            return BigInteger.valueOf((Integer) value);
        } else if (value instanceof Long) {
            return BigInteger.valueOf((Long) value);
        }
        return BigInteger.ZERO;
    }

    /**
     * @implNote If called for values that are not strings or byte arrays it will return an empty
     *     string.
     */
    public String asString() {
        decode();
        if (isBytes()) {
            return new String((byte[]) value);
        } else if (isString()) {
            return (String) value;
        }
        return "";
    }

    /**
     * @implNote If called for values that are not strings or byte arrays it will return an empty
     *     byte array.
     */
    public byte[] asBytes() {
        decode();
        if (isBytes()) {
            return (byte[]) value;
        } else if (isString()) {
            return asString().getBytes();
        }
        return EMPTY_BYTE_ARRAY;
    }

    public byte[] getData() {
        return this.encode();
    }

    public Value get(int index) {
        if (isList()) {
            // Guard for OutOfBounds
            if (asList().size() <= index) {
                return new Value(null);
            }
            if (index < 0) {
                throw new RuntimeException("Negative index not allowed");
            }
            return new Value(asList().get(index));
        }
        // If this wasn't a slice you probably shouldn't be using this function
        return new Value(null);
    }

    // Utility

    private void decode() {
        if (!this.decoded) {
            this.value = RLP.decode(rlp, 0).getDecoded();
            this.decoded = true;
        }
    }

    public byte[] encode() {
        if (rlp == null) {
            rlp = RLP.encode(value);
        }
        return rlp;
    }

    public boolean cmp(Value o) {
        if (o == null) {
            return false;
        }
        if (rlp != null && o.rlp != null) {
            return Arrays.equals(rlp, o.rlp);
        } else {
            return Arrays.equals(this.encode(), o.encode());
        }

        // return DeepEquals.deepEquals(this, o);
    }

    // Checks

    public boolean isList() {
        decode();
        return value != null
                && value.getClass().isArray()
                && !value.getClass().getComponentType().isPrimitive();
    }

    public boolean isString() {
        decode();
        return value instanceof String;
    }

    public boolean isBytes() {
        decode();
        return value instanceof byte[];
    }

    // it's only if the isBytes() = true;
    private boolean isReadableString() {

        decode();
        int readableChars = 0;
        byte[] data = (byte[]) value;

        if (data.length == 1 && data[0] > 31 && data[0] < 126) {
            return true;
        }

        for (byte aData : data) {
            if (aData > 32 && aData < 126) {
                ++readableChars;
            }
        }

        return (double) readableChars / (double) data.length > 0.55;
    }

    public boolean isHashCode() {
        decode();
        return this.asBytes().length == 32;
    }

    public boolean isNull() {
        decode();
        return value == null;
    }

    private boolean isEmpty() {
        decode();
        return isNull() // null
                || isBytes() && asBytes().length == 0 // empty byte array
                || isList() && asList().isEmpty() // empty list
                || isString() && asString().isEmpty(); // empty string
    }

    public int length() {
        decode();
        if (isList()) {
            return asList().size();
        } else if (isBytes()) {
            return asBytes().length;
        } else if (isString()) {
            return asString().length();
        }
        return 0;
    }

    public String toString() {

        decode();
        StringBuilder stringBuilder = new StringBuilder();

        if (isList()) {

            Object[] list = (Object[]) value;

            // special case - key/value node
            if (list.length == 2) {

                stringBuilder.append("[ ");

                Value key = new Value(list[0]);

                byte[] keyNibbles = CompactEncoder.binToNibblesNoTerminator(key.asBytes());
                String keyString = nibblesToPrettyString(keyNibbles);
                stringBuilder.append(keyString);

                stringBuilder.append(",");

                Value val = new Value(list[1]);
                stringBuilder.append(val.toString());

                stringBuilder.append(" ]");
                return stringBuilder.toString();
            }
            stringBuilder.append(" [");

            for (int i = 0; i < list.length; ++i) {
                Value val = new Value(list[i]);
                if (val.isString() || val.isEmpty()) {
                    stringBuilder.append("'").append(val.toString()).append("'");
                } else {
                    stringBuilder.append(val.toString());
                }
                if (i < list.length - 1) {
                    stringBuilder.append(", ");
                }
            }
            stringBuilder.append("] ");

            return stringBuilder.toString();
        } else if (isEmpty()) {
            return "";
        } else if (isBytes()) {

            StringBuilder output = new StringBuilder();
            if (isHashCode()) {
                output.append(Hex.toHexString(asBytes()));
            } else if (isReadableString()) {
                output.append("'");
                for (byte oneByte : asBytes()) {
                    if (oneByte < 16) {
                        output.append("\\x").append(oneByteToHexString(oneByte));
                    } else {
                        output.append(Character.valueOf((char) oneByte));
                    }
                }
                output.append("'");
                return output.toString();
            }
            return Hex.toHexString(this.asBytes());
        } else if (isString()) {
            return asString();
        }
        return "Unexpected type";
    }
}
