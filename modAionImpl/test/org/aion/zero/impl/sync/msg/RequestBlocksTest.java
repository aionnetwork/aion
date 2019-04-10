package org.aion.zero.impl.sync.msg;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.p2p.V1Constants.HASH_SIZE;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.zero.impl.sync.Act;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link RequestBlocks} messages.
 *
 * @author Alexandra Roatis
 */
@RunWith(JUnitParamsRunner.class)
public class RequestBlocksTest {

    private static final byte[] hash =
            new byte[] {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
                24, 25, 26, 27, 28, 29, 30, 31, 32
            };
    private static final byte[] altHash =
            new byte[] {
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
                23, 24, 25, 26, 27, 28, 29, 30, 31
            };
    private static final byte[] zeroHash = new byte[HASH_SIZE];
    private static final byte[] smallHash =
            new byte[] {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
                24, 25, 26, 27, 28, 29, 30, 31
            };
    private static final byte[] largeHash =
            new byte[] {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
                24, 25, 26, 27, 28, 29, 30, 31, 32, 33
            };
    private final byte[] emptyByteArray = new byte[] {};

    private final byte isTrue = 1;
    private final byte isFalse = 0;

    @Test
    public void testHeader_newObject_withHeight() {
        RequestBlocks message = new RequestBlocks(10L, 10, true);
        // check message header
        assertThat(message.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(message.getHeader().getAction()).isEqualTo(Act.REQUEST_BLOCKS);
    }

    @Test
    public void testHeader_newObject_withHash() {
        RequestBlocks message = new RequestBlocks(hash, 10, true);
        // check message header
        assertThat(message.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(message.getHeader().getAction()).isEqualTo(Act.REQUEST_BLOCKS);
    }

    @Test
    public void testHeader_decode_withHeight() {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isTrue),
                        RLP.encode(10L),
                        RLP.encodeInt(10),
                        RLP.encodeByte(isFalse));
        RequestBlocks message = RequestBlocks.decode(encoding);
        // check message header
        assertThat(message).isNotNull();
        assertThat(message.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(message.getHeader().getAction()).isEqualTo(Act.REQUEST_BLOCKS);
    }

    @Test
    public void testHeader_decode_withHash() {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse),
                        RLP.encodeElement(hash),
                        RLP.encodeInt(10),
                        RLP.encodeByte(isFalse));
        RequestBlocks message = RequestBlocks.decode(encoding);
        // check message header
        assertThat(message).isNotNull();
        assertThat(message.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(message.getHeader().getAction()).isEqualTo(Act.REQUEST_BLOCKS);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_nullHash() {
        new RequestBlocks(null, 10, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_smallHash() {
        new RequestBlocks(smallHash, 10, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_largeHash() {
        new RequestBlocks(largeHash, 10, true);
    }

    @Test
    public void testDecode_nullMessage() {
        assertThat(RequestBlocks.decode(null)).isNull();
    }

    @Test
    public void testDecode_emptyMessage() {
        assertThat(RequestBlocks.decode(new byte[0])).isNull();
    }

    @Test
    public void testDecode_missingNumberFlag() {
        byte[] encoding =
                RLP.encodeList(RLP.encode(10L), RLP.encodeInt(10), RLP.encodeByte(isTrue));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        encoding =
                RLP.encodeList(RLP.encodeElement(hash), RLP.encodeInt(10), RLP.encodeByte(isTrue));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_missingStart() {
        byte[] encoding =
                RLP.encodeList(RLP.encodeByte(isTrue), RLP.encodeInt(10), RLP.encodeByte(isTrue));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        encoding =
                RLP.encodeList(RLP.encodeByte(isFalse), RLP.encodeInt(10), RLP.encodeByte(isTrue));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_missingCount() {
        byte[] encoding =
                RLP.encodeList(RLP.encodeByte(isTrue), RLP.encode(10L), RLP.encodeByte(isFalse));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        encoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse), RLP.encodeElement(hash), RLP.encodeByte(isFalse));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_missingOrder() {
        byte[] encoding =
                RLP.encodeList(RLP.encodeByte(isTrue), RLP.encode(10L), RLP.encodeInt(10));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        encoding =
                RLP.encodeList(RLP.encodeByte(isFalse), RLP.encodeElement(hash), RLP.encodeInt(10));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_additionalValue() {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isTrue),
                        RLP.encode(10L),
                        RLP.encodeInt(10),
                        RLP.encodeByte(isTrue),
                        RLP.encodeInt(10));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_incorrectNumberFlag() {
        // flag = 2
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte((byte) 2),
                        RLP.encode(10L),
                        RLP.encodeInt(10),
                        RLP.encodeByte(isFalse));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // flag = -1
        encoding =
                RLP.encodeList(
                        RLP.encodeByte((byte) -1),
                        RLP.encodeElement(hash),
                        RLP.encodeInt(10),
                        RLP.encodeByte(isTrue));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_incorrectStartHeight() {
        // conversion to negative value
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isTrue),
                        RLP.encode(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)),
                        RLP.encodeInt(10),
                        RLP.encodeByte(isFalse));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // 0 is outside the allowed range
        encoding = RLP.encodeList(RLP.encode(0L), RLP.encodeInt(10), RLP.encodeByte(isTrue));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_smallStartHash() {
        // conversion to negative value
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse),
                        RLP.encodeElement(smallHash),
                        RLP.encodeInt(10),
                        RLP.encodeByte(isFalse));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // 0 is outside the allowed range
        encoding = RLP.encodeList(RLP.encode(0L), RLP.encodeInt(10), RLP.encodeByte(isTrue));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_largeStartHash() {
        // conversion to negative value
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse),
                        RLP.encodeElement(largeHash),
                        RLP.encodeInt(10),
                        RLP.encodeByte(isFalse));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // 0 is outside the allowed range
        encoding = RLP.encodeList(RLP.encode(0L), RLP.encodeInt(10), RLP.encodeByte(isTrue));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_incorrectFlag() {
        // most of these interpretations can't really be prevented
        // cause the hash/height can be interpreted as a height/hash
        // these tests are for cases where we can validate the incorrect conversions

        // flag says hash but height (with encoding < 32 bytes) is given
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse),
                        RLP.encode(10L),
                        RLP.encode(10),
                        RLP.encodeByte(isTrue));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // flag says height but hash (with decoding outside long values) is given
        byte[] largeLongHash =
                new byte[] {
                    0, -128, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0
                };
        encoding =
                RLP.encodeList(
                        RLP.encodeByte(isTrue),
                        RLP.encodeElement(largeLongHash),
                        RLP.encodeInt(10),
                        RLP.encodeByte(isTrue));

        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_incorrectCount() {
        // using height
        // conversion to negative value
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isTrue),
                        RLP.encode(10L),
                        RLP.encode(1L + Integer.MAX_VALUE),
                        RLP.encodeByte(isTrue));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // 0 is outside the allowed range
        encoding =
                RLP.encodeList(
                        RLP.encodeByte(isTrue),
                        RLP.encode(10L),
                        RLP.encodeInt(0),
                        RLP.encodeByte(isTrue));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // using hash
        // conversion to negative value
        encoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse),
                        RLP.encodeElement(largeHash),
                        RLP.encode(1L + Integer.MAX_VALUE),
                        RLP.encodeByte(isTrue));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // 0 is outside the allowed range
        encoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse),
                        RLP.encodeElement(largeHash),
                        RLP.encodeInt(0),
                        RLP.encodeByte(isTrue));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_incorrectOrder() {
        // using height
        // max byte value
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isTrue),
                        RLP.encode(10L),
                        RLP.encodeInt(10),
                        RLP.encodeByte(Byte.MAX_VALUE));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // outside the allowed range
        encoding =
                RLP.encodeList(
                        RLP.encodeByte(isTrue),
                        RLP.encode(10L),
                        RLP.encodeInt(10),
                        RLP.encodeByte((byte) 2));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // conversion to negative value
        encoding =
                RLP.encodeList(
                        RLP.encodeByte(isTrue),
                        RLP.encode(10L),
                        RLP.encodeInt(10),
                        RLP.encode(Byte.MAX_VALUE + 1));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // using hash
        // max byte value
        encoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse),
                        RLP.encodeElement(largeHash),
                        RLP.encodeInt(10),
                        RLP.encodeByte(Byte.MAX_VALUE));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // outside the allowed range
        encoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse),
                        RLP.encodeElement(largeHash),
                        RLP.encodeInt(10),
                        RLP.encodeByte((byte) 2));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // conversion to negative value
        encoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse),
                        RLP.encodeElement(largeHash),
                        RLP.encodeInt(10),
                        RLP.encode(Byte.MAX_VALUE + 1));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_notAList() {
        // conversion to negative value
        byte[] encoding = RLP.encode(10L);
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    /**
     * Parameters for testing:
     *
     * <ul>
     *   <li>{@link #testDecode_correct(byte,Object, int, byte)}
     *   <li>{@link #testEncode_correct(byte,Object, int, byte)}
     *   <li>{@link #testEncodeDecode(byte,Object, int, byte)}
     * </ul>
     */
    @SuppressWarnings("unused")
    private Object correctParameters() {
        List<Object> parameters = new ArrayList<>();

        long[] startOptions = new long[] {1L, Integer.MAX_VALUE, Long.MAX_VALUE};
        int[] countOptions = new int[] {1, Byte.MAX_VALUE, Integer.MAX_VALUE};
        byte[] orderOptions = new byte[] {isTrue, isFalse};

        for (long start : startOptions) {
            for (int count : countOptions) {
                for (byte order : orderOptions) {
                    parameters.add(new Object[] {isTrue, start, count, order});
                }
            }
        }

        byte[][] hashOptions = new byte[][] {hash, altHash};
        for (byte[] start : hashOptions) {
            for (int count : countOptions) {
                for (byte order : orderOptions) {
                    parameters.add(new Object[] {isFalse, start, count, order});
                }
            }
        }

        return parameters.toArray();
    }

    @Test
    @Parameters(method = "correctParameters")
    public void testDecode_correct(byte flag, Object start, int count, byte order) {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(flag),
                        flag == isTrue ? RLP.encode(start) : RLP.encodeElement((byte[]) start),
                        RLP.encodeInt(count),
                        RLP.encodeByte(order));

        RequestBlocks message = RequestBlocks.decode(encoding);

        assertThat(message).isNotNull();
        assertThat(message.isNumber()).isEqualTo(flag == isTrue);
        if (flag == isTrue) {
            assertThat(message.getStartHeight()).isEqualTo(start);
            assertThat(message.getStartHash()).isNull();
        } else {
            assertThat(message.getStartHash()).isEqualTo(start);
            assertThat(message.getStartHeight()).isNull();
        }
        assertThat(message.getCount()).isEqualTo(count);
        assertThat(message.isDescending()).isEqualTo(order == isTrue);
    }

    @Test
    @Parameters(method = "correctParameters")
    public void testEncode_correct(byte flag, Object start, int count, byte order) {
        byte[] expected =
                RLP.encodeList(
                        RLP.encodeByte(flag),
                        flag == isTrue ? RLP.encode(start) : RLP.encodeElement((byte[]) start),
                        RLP.encodeInt(count),
                        RLP.encodeByte(order));

        RequestBlocks message;
        if (flag == isTrue) {
            message = new RequestBlocks((long) start, count, order == isTrue);
            assertThat(message.getStartHash()).isNull();
        } else {
            message = new RequestBlocks((byte[]) start, count, order == isTrue);
            assertThat(message.getStartHeight()).isNull();
        }
        assertThat(message.isNumber()).isEqualTo(flag == isTrue);
        assertThat(message.encode()).isEqualTo(expected);
    }

    @Test
    @Parameters(method = "correctParameters")
    public void testEncodeDecode(byte flag, Object start, int count, byte order) {
        // encode
        RequestBlocks message;
        if (flag == isTrue) {
            message = new RequestBlocks((long) start, count, order == isTrue);
            assertThat(message.getStartHeight()).isEqualTo(start);
            assertThat(message.getStartHash()).isNull();
        } else {
            message = new RequestBlocks((byte[]) start, count, order == isTrue);
            assertThat(message.getStartHash()).isEqualTo(start);
            assertThat(message.getStartHeight()).isNull();
        }

        assertThat(message.isNumber()).isEqualTo(flag == isTrue);
        assertThat(message.getCount()).isEqualTo(count);
        assertThat(message.isDescending()).isEqualTo(order == isTrue);

        byte[] encoding = message.encode();

        // decode
        RequestBlocks decoded = RequestBlocks.decode(encoding);

        assertThat(decoded).isNotNull();
        assertThat(decoded.isNumber()).isEqualTo(flag == isTrue);
        if (flag == isTrue) {
            assertThat(decoded.getStartHeight()).isEqualTo(start);
            assertThat(decoded.getStartHash()).isNull();
        } else {
            assertThat(decoded.getStartHash()).isEqualTo(start);
            assertThat(decoded.getStartHeight()).isNull();
        }
        assertThat(decoded.getCount()).isEqualTo(count);
        assertThat(decoded.isDescending()).isEqualTo(order == isTrue);
    }

    @Test
    public void testEncode_differentStartHeight() {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isTrue),
                        RLP.encode(1L),
                        RLP.encodeInt(1),
                        RLP.encodeByte(isTrue));

        RequestBlocks message = new RequestBlocks(2L, 1, true);
        assertThat(message.encode()).isNotEqualTo(encoding);
    }

    @Test
    public void testEncode_differentStartHash() {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse),
                        RLP.encodeElement(hash),
                        RLP.encodeInt(1),
                        RLP.encodeByte(isTrue));

        RequestBlocks message = new RequestBlocks(altHash, 1, true);
        assertThat(message.encode()).isNotEqualTo(encoding);
    }

    @Test
    public void testEncode_differentCount() {
        // using height
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isTrue),
                        RLP.encode(1L),
                        RLP.encodeInt(1),
                        RLP.encodeByte(isTrue));

        RequestBlocks message = new RequestBlocks(1L, 2, true);
        assertThat(message.encode()).isNotEqualTo(encoding);

        // using hash
        encoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse),
                        RLP.encodeElement(hash),
                        RLP.encodeInt(1),
                        RLP.encodeByte(isTrue));

        message = new RequestBlocks(hash, 2, true);
        assertThat(message.encode()).isNotEqualTo(encoding);
    }

    @Test
    public void testEncode_differentOrder() {
        // using height
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeByte(isTrue),
                        RLP.encode(1L),
                        RLP.encodeInt(1),
                        RLP.encodeByte(isTrue));

        RequestBlocks message = new RequestBlocks(1L, 1, false);
        assertThat(message.encode()).isNotEqualTo(encoding);

        // using hash
        encoding =
                RLP.encodeList(
                        RLP.encodeByte(isFalse),
                        RLP.encodeElement(hash),
                        RLP.encodeInt(1),
                        RLP.encodeByte(isTrue));

        message = new RequestBlocks(hash, 1, false);
        assertThat(message.encode()).isNotEqualTo(encoding);
    }
}
