package org.aion.rlp;

import static java.util.Arrays.copyOfRange;
import static org.aion.rlp.Utils.asUnsignedByteArray;
import static org.aion.rlp.Utils.concatenate;
import static org.aion.util.bytes.ByteUtil.byteArrayToInt;
import static org.aion.util.bytes.ByteUtil.intToBytesNoLeadZeroes;
import static org.aion.util.bytes.ByteUtil.isNullOrZeroArray;
import static org.aion.util.bytes.ByteUtil.isSingleZero;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;

/**
 * @author Roman Mandeleil 2014
 * @author modified by aion 2017
 */
public class RLP {

    // Allow for content up to size of 2^64 bytes
    // private static final double MAX_ITEM_LENGTH = Math.pow(256, 8);

    /**
     * Reason for threshold according to Vitalik Buterin: - 56 bytes maximizes the benefit of both
     * options - if we went with 60 then we would have only had 4 slots for long strings so RLP
     * would not have been able to store objects above 4gb - if we went with 48 then RLP would be
     * fine for 2^128 space, but that's way too much - so 56 and 2^64 space seems like the right
     * place to put the cutoff - also, that's where Bitcoin's variant does the cutoff
     */
    private static final int SIZE_THRESHOLD = 56;

    /**
     * For a single byte whose value is in the [0x00, 0x7f] range, that byte is its own RLP
     * encoding.
     *
     * <p>[0x80] If a string is 0-55 bytes long, the RLP encoding consists of a single byte with
     * value 0x80 plus the length of the string followed by the string. The range of the first byte
     * is thus [0x80, 0xb7].
     */
    private static final int OFFSET_SHORT_ITEM = 0x80;

    /**
     * [0xb7] If a string is more than 55 bytes long, the RLP encoding consists of a single byte
     * with value 0xb7 plus the length of the length of the string in binary form, followed by the
     * length of the string, followed by the string. For example, a length-1024 string would be
     * encoded as \xb9\x04\x00 followed by the string. The range of the first byte is thus [0xb8,
     * 0xbf].
     */
    private static final int OFFSET_LONG_ITEM = 0xb7;

    /**
     * [0xc0] If the total payload of a list (i.e. the combined length of all its items) is 0-55
     * bytes long, the RLP encoding consists of a single byte with value 0xc0 plus the length of the
     * list followed by the concatenation of the RLP encodings of the items. The range of the first
     * byte is thus [0xc0, 0xf7].
     */
    private static final int OFFSET_SHORT_LIST = 0xc0;

    /**
     * [0xf7] If the total payload of a list is more than 55 bytes long, the RLP encoding consists
     * of a single byte with value 0xf7 plus the length of the length of the list in binary form,
     * followed by the length of the list, followed by the concatenation of the RLP encodings of the
     * items. The range of the first byte is thus [0xf8, 0xff].
     */
    private static final int OFFSET_LONG_LIST = 0xf7;

    // DECODING

    public static int decodeInt(byte[] data, int index) {
        int value = 0;
        // NOTE: there are two ways zero can be encoded - 0x00 and
        // OFFSET_SHORT_ITEM

        if ((data[index] & 0xFF) < OFFSET_SHORT_ITEM) {
            return data[index];
        } else if ((data[index] & 0xFF) < OFFSET_LONG_ITEM) {

            byte length = (byte) (data[index] - OFFSET_SHORT_ITEM);
            byte pow = (byte) (length - 1);
            for (int i = 1; i <= length; ++i) {
                value += (data[index + i] & 0xFF) << (8 * pow);
                --pow;
            }
        } else {
            throw new RuntimeException("wrong decode attempt");
        }
        return value;
    }

    public static BigInteger decodeBigInteger(byte[] data, int index) {

        int prefix = data[index] & 0xFF;

        if (prefix < OFFSET_SHORT_ITEM) {
            return BigInteger.valueOf(data[index]);
        } else if (prefix <= OFFSET_LONG_ITEM) {
            int length = prefix - OFFSET_SHORT_ITEM;

            byte[] copy = new byte[length];
            System.arraycopy(data, index + 1, copy, 0, Math.min(data.length - index - 1, length));

            return new BigInteger(1, copy);
        } else if (prefix < OFFSET_SHORT_LIST) {
            int lengthOfLength = prefix - OFFSET_LONG_ITEM;

            // find length of number
            byte[] copy = new byte[lengthOfLength];
            System.arraycopy(
                    data, index + 1, copy, 0, Math.min(data.length - index - 1, lengthOfLength));

            int length = new BigInteger(1, copy).intValue();

            // get value
            copy = new byte[length];
            System.arraycopy(
                    data,
                    index + 1 + lengthOfLength,
                    copy,
                    0,
                    Math.min(data.length - index - 1 - lengthOfLength, length));

            return new BigInteger(1, copy);
        } else {
            throw new RuntimeException("wrong decode attempt");
        }
    }

    private static int calcLength(int lengthOfLength, byte[] msgData, int pos) {
        byte pow = (byte) (lengthOfLength - 1);
        int length = 0;
        for (int i = 1; i <= lengthOfLength; ++i) {
            length += (msgData[pos + i] & 0xFF) << (8 * pow);
            --pow;
        }
        return length;
    }

    /**
     * Parse wire byte[] message into RLP elements
     *
     * @param msgData - raw RLP data
     * @return rlpList - outcome of recursive RLP structure
     */
    public static RLPList decode2(byte[] msgData) {
        RLPList rlpList = new RLPList();
        fullTraverse(msgData, 0, 0, msgData == null ? 0 : msgData.length, rlpList);
        return rlpList;
    }

    /** @implNote Considers only encodings of one byte. */
    public static RLPElement decode2OneItem(byte[] msgData, int startPos) {
        RLPList rlpList = new RLPList();
        fullTraverse(msgData, 0, startPos, startPos + 1, rlpList);
        return rlpList.get(0);
    }

    /** Get exactly one message payload */
    private static void fullTraverse(
            byte[] msgData, int level, int startPos, int endPos, RLPList rlpList) {

        try {
            if (msgData == null || msgData.length == 0) {
                return;
            }
            int pos = startPos;

            while (pos < endPos) {

                int type = msgData[pos] & 0xFF;

                if (type < OFFSET_SHORT_ITEM) { // single byte item
                    byte[] item = {(byte) type};

                    RLPItem rlpItem = new RLPItem(item);
                    rlpList.add(rlpItem);
                    ++pos;
                } else if (type == OFFSET_SHORT_ITEM) { // null item
                    RLPItem rlpItem = new RLPItem(ByteUtil.EMPTY_BYTE_ARRAY);
                    rlpList.add(rlpItem);
                    ++pos;
                } else if (type <= OFFSET_LONG_ITEM) {
                    // It's an item less than 55 bytes long,
                    // data[0] - 0x80 == length of the item

                    byte length = (byte) (type - OFFSET_SHORT_ITEM);

                    byte[] item = new byte[length];
                    System.arraycopy(msgData, pos + 1, item, 0, length);

                    // not used
                    // byte[] rlpPrefix = new byte[2];
                    // System.arraycopy(msgData, pos, rlpPrefix, 0, 2);

                    RLPItem rlpItem = new RLPItem(item);
                    rlpList.add(rlpItem);
                    pos += 1 + length;
                } else if (type < OFFSET_SHORT_LIST) {

                    // It's an item with a payload more than 55 bytes
                    // data[0] - 0xB7 = how much next bytes allocated for
                    // the length of the string
                    byte lengthOfLength = (byte) (type - OFFSET_LONG_ITEM);
                    int length = calcLength(lengthOfLength, msgData, pos);

                    // now we can parse an item for data[1]..data[length]
                    byte[] item = new byte[length];
                    System.arraycopy(msgData, pos + lengthOfLength + 1, item, 0, length);

                    // not used
                    // byte[] rlpPrefix = new byte[lengthOfLength + 1];
                    // System.arraycopy(msgData, pos, rlpPrefix, 0, lengthOfLength + 1);

                    RLPItem rlpItem = new RLPItem(item);
                    rlpList.add(rlpItem);
                    pos += lengthOfLength + length + 1;
                } else if (type <= OFFSET_LONG_LIST) {
                    // It's a list with a payload less than 55 bytes
                    byte length = (byte) (type - OFFSET_SHORT_LIST);

                    byte[] rlpData = new byte[length + 1];
                    System.arraycopy(msgData, pos, rlpData, 0, length + 1);

                    RLPList newLevelList = new RLPList();
                    newLevelList.setRLPData(rlpData);

                    if (length > 0) {
                        fullTraverse(msgData, level + 1, pos + 1, pos + length + 1, newLevelList);
                    }
                    rlpList.add(newLevelList);

                    pos += 1 + length;
                } else {
                    // logger.debug("fullTraverse: level: " + level + " startPos: "
                    // + pos + " endPos: " + endPos);
                    // It's a list with a payload more than 55 bytes
                    // data[0] - 0xF7 = how many next bytes allocated
                    // for the length of the list
                    byte lengthOfLength = (byte) (type - OFFSET_LONG_LIST);
                    int length = calcLength(lengthOfLength, msgData, pos);

                    byte[] rlpData = new byte[lengthOfLength + length + 1];
                    System.arraycopy(msgData, pos, rlpData, 0, lengthOfLength + length + 1);

                    RLPList newLevelList = new RLPList();
                    newLevelList.setRLPData(rlpData);

                    fullTraverse(
                            msgData,
                            level + 1,
                            pos + lengthOfLength + 1,
                            pos + lengthOfLength + length + 1,
                            newLevelList);
                    rlpList.add(newLevelList);

                    pos += lengthOfLength + length + 1;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "RLP wrong encoding ("
                            + Hex.toHexString(msgData, startPos, endPos - startPos > 1024 ? 1024 : endPos - startPos)
                            + ")",
                    e);
        } catch (OutOfMemoryError e) {
            throw new RuntimeException(
                    "Invalid RLP (excessive mem allocation while parsing) ("
                            + Hex.toHexString(msgData, startPos, endPos - startPos)
                            + ")",
                    e);
        }
    }

    /**
     * Reads any RLP encoded byte-array and returns all objects as byte-array or list of byte-arrays
     *
     * @param data RLP encoded byte-array
     * @param pos position in the array to start reading
     * @return DecodeResult encapsulates the decoded items as a single Object and the final read
     *     position
     */
    public static DecodeResult decode(byte[] data, int pos) {
        if (data == null || data.length < 1) {
            return null;
        }
        int prefix = data[pos] & 0xFF;
        if (prefix == OFFSET_SHORT_ITEM) {
            // means no length or 0
            return new DecodeResult(pos + 1, "");
        } else if (prefix < OFFSET_SHORT_ITEM) {
            // byte is its own RLP encoding
            return new DecodeResult(pos + 1, new byte[] {data[pos]});
        } else if (prefix <= OFFSET_LONG_ITEM) {
            // length of the encoded bytes
            int len = prefix - OFFSET_SHORT_ITEM;
            return new DecodeResult(pos + 1 + len, copyOfRange(data, pos + 1, pos + 1 + len));
        } else if (prefix < OFFSET_SHORT_LIST) {
            // length of length of the encoded bytes
            int lenlen = prefix - OFFSET_LONG_ITEM;
            // length of encoded bytes
            int lenbytes = byteArrayToInt(copyOfRange(data, pos + 1, pos + 1 + lenlen));
            return new DecodeResult(
                    pos + 1 + lenlen + lenbytes,
                    copyOfRange(data, pos + 1 + lenlen, pos + 1 + lenlen + lenbytes));
        } else if (prefix <= OFFSET_LONG_LIST) {
            // length of the encoded list
            int len = prefix - OFFSET_SHORT_LIST;
            pos++;
            return decodeList(data, pos, len);
        } else if (prefix < 0xFF) {
            // length of length of the encoded list
            int lenlen = prefix - OFFSET_LONG_LIST;
            // length of encoded bytes
            int lenlist = byteArrayToInt(copyOfRange(data, pos + 1, pos + 1 + lenlen));
            // start at position of first element in list
            pos = pos + lenlen + 1;
            return decodeList(data, pos, lenlist);
        } else {
            throw new RuntimeException(
                    "Only byte values between 0x00 and 0xFF are supported, but got: " + prefix);
        }
    }

    private static DecodeResult decodeList(byte[] data, int pos, int len) {
        List<Object> slice = new ArrayList<>();
        for (int i = 0; i < len; ) {
            // Get the next item in the data list and append it
            DecodeResult result = decode(data, pos);
            slice.add(result.getDecoded());
            // Increment pos by the amount bytes in the previous read
            int prevPos = result.getPos();
            i += (prevPos - pos);
            pos = prevPos;
        }
        return new DecodeResult(pos, slice.toArray());
    }

    // ENCODING

    /**
     * Turn Object into its RLP encoded equivalent of a byte-array Support for String, Integer,
     * BigInteger and Lists of any of these types.
     *
     * @param input as object or List of objects
     * @return byte[] RLP encoded
     */
    public static byte[] encode(Object input) {
        Value val = new Value(input);
        if (val.isList()) {
            List<Object> inputArray = val.asList();
            if (inputArray.isEmpty()) {
                return encodeLength(inputArray.size(), OFFSET_SHORT_LIST);
            }

            List<byte[]> obj = new ArrayList<>();
            int length = 0;

            for (Object object : inputArray) {
                byte[] temp = encode(object);
                obj.add(temp);
                length += temp.length;
            }

            byte[] prefix = encodeLength(length, OFFSET_SHORT_LIST);
            byte[] output = new byte[prefix.length + length];

            System.arraycopy(prefix, 0, output, 0, prefix.length);
            int pos = prefix.length;
            for (byte[] anObj : obj) {
                System.arraycopy(anObj, 0, output, pos, anObj.length);
                pos += anObj.length;
            }

            return output;
        } else {
            byte[] inputAsBytes = toBytes(input);
            if (inputAsBytes.length == 1 && (inputAsBytes[0] & 0xff) < 0x80) {
                return inputAsBytes;
            } else {
                byte[] firstByte = encodeLength(inputAsBytes.length, OFFSET_SHORT_ITEM);
                return concatenate(firstByte, inputAsBytes);
            }
        }
    }

    /** Integer limitation goes up to 2^31-1 so length can never be bigger than MAX_ITEM_LENGTH */
    public static byte[] encodeLength(int length, int offset) {
        if (length < SIZE_THRESHOLD) {
            byte firstByte = (byte) (length + offset);
            return new byte[] {firstByte};
        } else { // if (length < MAX_ITEM_LENGTH) -> always true
            byte[] binaryLength;
            if (length > 0xFF) {
                binaryLength = intToBytesNoLeadZeroes(length);
            } else {
                binaryLength = new byte[] {(byte) length};
            }
            byte firstByte = (byte) (binaryLength.length + offset + SIZE_THRESHOLD - 1);
            return concatenate(new byte[] {firstByte}, binaryLength);
        }
    }

    public static byte[] encodeByte(byte singleByte) {
        if ((singleByte & 0xFF) == 0) {
            return new byte[] {(byte) OFFSET_SHORT_ITEM};
        } else if ((singleByte & 0xFF) <= 0x7F) {
            return new byte[] {singleByte};
        } else {
            return new byte[] {(byte) (OFFSET_SHORT_ITEM + 1), singleByte};
        }
    }

    public static byte[] encodeShort(short singleShort) {
        if ((singleShort & 0xFF) == singleShort) {
            return encodeByte((byte) singleShort);
        } else {
            return new byte[] {
                (byte) (OFFSET_SHORT_ITEM + 2), (byte) (singleShort >>> 8), (byte) singleShort
            };
        }
    }

    public static byte[] encodeInt(int singleInt) {
        if ((singleInt & 0xFFFF) == singleInt) {
            return encodeShort((short) singleInt);
        } else if ((singleInt & 0xFFFFFF) == singleInt) {
            return new byte[] {
                (byte) (OFFSET_SHORT_ITEM + 3),
                (byte) (singleInt >>> 16),
                (byte) (singleInt >>> 8),
                (byte) singleInt
            };
        } else {
            return new byte[] {
                (byte) (OFFSET_SHORT_ITEM + 4),
                (byte) (singleInt >>> 24),
                (byte) (singleInt >>> 16),
                (byte) (singleInt >>> 8),
                (byte) singleInt
            };
        }
    }

    public static byte[] encodeLong(long l) {
        if ((l & 0x00000000FFFFFFFFL) == l) {
            return encodeInt((int) l);
        } else {
            byte[] out = new byte[9];
            out[0] = (byte) (OFFSET_SHORT_ITEM + 8);
            for (int i = 8; i > 0; i--) {
                out[i] = (byte) l;
                l >>>= 8;
            }
            return out;
        }
    }

    public static byte[] encodeString(String srcString) {
        return encodeElement(srcString.getBytes());
    }

    public static byte[] encodeBigInteger(BigInteger srcBigInteger) {
        if (srcBigInteger.equals(BigInteger.ZERO)) {
            return encodeByte((byte) 0);
        } else {
            return encodeElement(asUnsignedByteArray(srcBigInteger));
        }
    }

    public static byte[] encodeElement(byte[] srcData) {

        if (isNullOrZeroArray(srcData)) {
            return new byte[] {(byte) OFFSET_SHORT_ITEM};
        } else if (isSingleZero(srcData)) {
            return srcData;
        } else if (srcData.length == 1 && (srcData[0] & 0xFF) < 0x80) {
            return srcData;
        } else if (srcData.length < SIZE_THRESHOLD) {
            // length = 8X
            byte length = (byte) (OFFSET_SHORT_ITEM + srcData.length);

            byte[] data = new byte[srcData.length + 1];
            System.arraycopy(srcData, 0, data, 1, srcData.length);
            data[0] = length;

            return data;
        } else {
            // length of length = BX
            // prefix = [BX, [length]]
            int tmpLength = srcData.length;
            byte byteNum = 0;
            while (tmpLength != 0) {
                ++byteNum;
                tmpLength >>>= 8;
            }

            /*
             * Data = [0xBX, x1, .. xn, srcData]
             * X = 7 + byteNum (Number of bytes used to represent length of string)
             * x1 ... xn bytes of the length of the string
             *
             * Write directly to the data array to avoid allocating a new byte array, copying it later and throwing it away.
             *
             */
            byte[] data = new byte[srcData.length + 1 + byteNum];
            data[0] = (byte) (OFFSET_LONG_ITEM + byteNum);
            for (int i = 0; i < byteNum; i++) {
                data[byteNum - i] = (byte) (srcData.length >>> (8 * i));
            }
            System.arraycopy(srcData, 0, data, 1 + byteNum, srcData.length);

            return data;
        }
    }

    public static int calcElementPrefixSize(byte[] srcData) {

        if (isNullOrZeroArray(srcData)) {
            return 0;
        } else if (isSingleZero(srcData)) {
            return 0;
        } else if (srcData.length == 1 && (srcData[0] & 0xFF) < 0x80) {
            return 0;
        } else if (srcData.length < SIZE_THRESHOLD) {
            return 1;
        } else {
            // length of length = BX
            // prefix = [BX, [length]]
            int tmpLength = srcData.length;
            byte byteNum = 0;
            while (tmpLength != 0) {
                ++byteNum;
                tmpLength >>>= 8;
            }

            return 1 + byteNum;
        }
    }

    public static byte[] encodeListHeader(int size) {

        if (size == 0) {
            return new byte[] {(byte) OFFSET_SHORT_LIST};
        }

        byte[] header;
        if (size < SIZE_THRESHOLD) {

            header = new byte[1];
            header[0] = (byte) (OFFSET_SHORT_LIST + size);
        } else {
            // length of length = BX
            // prefix = [BX, [length]]
            int tmpLength = size;
            byte byteNum = 0;
            while (tmpLength != 0) {
                ++byteNum;
                tmpLength >>>= 8;
            }

            header = new byte[1 + byteNum];
            header[0] = (byte) (OFFSET_LONG_LIST + byteNum);

            for (int i = 0; i < byteNum; i++) {
                header[byteNum - i] = (byte) (size >>> (8 * i));
            }
        }

        return header;
    }

    /**
     * @implNote The interpretation of long element here is anything that requires an non-empty
     *     header and is not a list.
     */
    public static byte[] encodeLongElementHeader(int length) {

        if (length < SIZE_THRESHOLD) {

            if (length == 0) {
                return new byte[] {(byte) 0x80};
            } else {
                return new byte[] {(byte) (0x80 + length)};
            }

        } else {

            // length of length = BX
            // prefix = [BX, [length]]
            int tmpLength = length;
            byte byteNum = 0;
            while (tmpLength != 0) {
                ++byteNum;
                tmpLength >>>= 8;
            }

            byte[] header = new byte[1 + byteNum];
            header[0] = (byte) (OFFSET_LONG_ITEM + byteNum);
            for (int i = 0; i < byteNum; i++) {
                header[byteNum - i] = (byte) (length >>> (8 * i));
            }

            return header;
        }
    }

    public static byte[] encodeList(byte[]... elements) {

        if (elements == null) {
            return new byte[] {(byte) OFFSET_SHORT_LIST};
        }

        int totalLength = 0;
        for (byte[] element1 : elements) {
            totalLength += element1.length;
        }

        byte[] data;
        int copyPos;
        if (totalLength < SIZE_THRESHOLD) {

            data = new byte[1 + totalLength];
            data[0] = (byte) (OFFSET_SHORT_LIST + totalLength);
            copyPos = 1;
        } else {
            // length of length = BX
            // prefix = [BX, [length]]
            int tmpLength = totalLength;
            byte byteNum = 0;
            while (tmpLength != 0) {
                ++byteNum;
                tmpLength >>>= 8;
            }

            /*
             * Data = [0xCX, + length + rlp1, rlp2, rlp3, ....]
             * X = 0 + (Number of bytes used to represent length of lists)
             * rlp1 .. rlpx rlp encodings of each list
             *
             * Write directly to the data array to avoid allocating a new byte array, copying it later and throwing it away.
             *
             */
            data = new byte[1 + byteNum + totalLength];
            data[0] = (byte) (OFFSET_LONG_LIST + byteNum);
            for (int i = 0; i < byteNum; i++) {
                data[byteNum - i] = (byte) (totalLength >>> (8 * i));
            }
            copyPos = byteNum + 1;
        }
        for (byte[] element : elements) {
            System.arraycopy(element, 0, data, copyPos, element.length);
            copyPos += element.length;
        }
        return data;
    }

    /** Utility function to convert Objects into byte arrays */
    private static byte[] toBytes(Object input) {
        if (input instanceof byte[]) {
            return (byte[]) input;
        } else if (input instanceof String) {
            String inputString = (String) input;
            return inputString.getBytes();
        } else if (input instanceof Byte) {
            Byte inputByte = (Byte) input;
            return (inputByte == 0)
                    ? ByteUtil.EMPTY_BYTE_ARRAY
                    : asUnsignedByteArray(BigInteger.valueOf(inputByte));
        } else if (input instanceof Short) {
            Short inputShort = (Short) input;
            return (inputShort == 0)
                    ? ByteUtil.EMPTY_BYTE_ARRAY
                    : asUnsignedByteArray(BigInteger.valueOf(inputShort));
        } else if (input instanceof Integer) {
            Integer inputInt = (Integer) input;
            return (inputInt == 0)
                    ? ByteUtil.EMPTY_BYTE_ARRAY
                    : asUnsignedByteArray(BigInteger.valueOf(inputInt));
        } else if (input instanceof Long) {
            Long inputLong = (Long) input;
            return (inputLong == 0)
                    ? ByteUtil.EMPTY_BYTE_ARRAY
                    : asUnsignedByteArray(BigInteger.valueOf(inputLong));
        } else if (input instanceof BigInteger) {
            BigInteger inputBigInt = (BigInteger) input;
            return (inputBigInt.equals(BigInteger.ZERO))
                    ? ByteUtil.EMPTY_BYTE_ARRAY
                    : asUnsignedByteArray(inputBigInt);
        } else if (input instanceof Value) {
            Value val = (Value) input;
            return toBytes(val.asObj());
        }
        throw new RuntimeException(
                "Unsupported type "
                        + input.getClass().getSimpleName()
                        + ". Only accepting String, Byte, Short, Integer, Long and BigInteger for now.");
    }
}
