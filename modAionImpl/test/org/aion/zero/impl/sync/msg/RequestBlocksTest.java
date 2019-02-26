package org.aion.zero.impl.sync.msg;

import static com.google.common.truth.Truth.assertThat;

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

    @Test
    public void testDecode_nullMessage() {
        assertThat(RequestBlocks.decode(null)).isNull();
    }

    @Test
    public void testDecode_emptyMessage() {
        assertThat(RequestBlocks.decode(new byte[0])).isNull();
    }

    @Test
    public void testDecode_missingStart() {
        byte[] encoding = RLP.encodeList(RLP.encodeInt(10), RLP.encodeByte((byte) 1));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_missingCount() {
        byte[] encoding = RLP.encodeList(RLP.encode(10L), RLP.encodeByte((byte) 0));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_missingOrder() {
        byte[] encoding = RLP.encodeList(RLP.encode(10L), RLP.encodeInt(10));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_additionalValue() {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encode(10L),
                        RLP.encodeInt(10),
                        RLP.encodeByte((byte) 1),
                        RLP.encodeInt(10));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_incorrectStart() {
        // conversion to negative value
        byte[] encoding =
                RLP.encodeList(
                        RLP.encode(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)),
                        RLP.encodeInt(10),
                        RLP.encodeByte((byte) 0));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // 0 is outside the allowed range
        encoding = RLP.encodeList(RLP.encode(0L), RLP.encodeInt(10), RLP.encodeByte((byte) 1));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_incorrectCount() {
        // conversion to negative value
        byte[] encoding =
                RLP.encodeList(
                        RLP.encode(10L),
                        RLP.encode(1L + Integer.MAX_VALUE),
                        RLP.encodeByte((byte) 1));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // 0 is outside the allowed range
        encoding = RLP.encodeList(RLP.encode(10L), RLP.encodeInt(0), RLP.encodeByte((byte) 1));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_incorrectOrder() {
        // max byte value
        byte[] encoding =
                RLP.encodeList(RLP.encode(10L), RLP.encodeInt(10), RLP.encodeByte(Byte.MAX_VALUE));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // outside the allowed range
        encoding = RLP.encodeList(RLP.encode(10L), RLP.encodeInt(10), RLP.encodeByte((byte) 2));
        assertThat(RequestBlocks.decode(encoding)).isNull();

        // conversion to negative value
        encoding =
                RLP.encodeList(RLP.encode(10L), RLP.encodeInt(10), RLP.encode(Byte.MAX_VALUE + 1));
        assertThat(RequestBlocks.decode(encoding)).isNull();
    }

    /**
     * Parameters for testing:
     *
     * <ul>
     *   <li>{@link #testDecode_correct(long, int, byte)}
     *   <li>{@link #testEncode_correct(long, int, byte)}
     *   <li>{@link #testEncodeDecode(long, int, byte)}
     * </ul>
     */
    @SuppressWarnings("unused")
    private Object correctParameters() {
        List<Object> parameters = new ArrayList<>();

        long[] startOptions = new long[] {1L, Integer.MAX_VALUE, Long.MAX_VALUE};
        int[] countOptions = new int[] {1, Byte.MAX_VALUE, Integer.MAX_VALUE};
        byte[] orderOptions = new byte[] {0, 1};

        for (long start : startOptions) {
            for (int count : countOptions) {
                for (byte order : orderOptions) {
                    parameters.add(new Object[] {start, count, order});
                }
            }
        }

        return parameters.toArray();
    }

    @Test
    @Parameters(method = "correctParameters")
    public void testDecode_correct(long start, int count, byte order) {
        byte[] encoding =
                RLP.encodeList(RLP.encode(start), RLP.encodeInt(count), RLP.encodeByte(order));

        RequestBlocks message = RequestBlocks.decode(encoding);

        assertThat(message).isNotNull();
        assertThat(message.getStart()).isEqualTo(start);
        assertThat(message.getCount()).isEqualTo(count);
        assertThat(message.isDescending()).isEqualTo(order == 1);

        // check message header
        assertThat(message.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(message.getHeader().getAction()).isEqualTo(Act.REQUEST_BLOCKS);
    }

    @Test
    @Parameters(method = "correctParameters")
    public void testEncode_correct(long start, int count, byte order) {
        byte[] expected =
                RLP.encodeList(RLP.encode(start), RLP.encodeInt(count), RLP.encodeByte(order));

        RequestBlocks message = new RequestBlocks(start, count, order == 1);
        assertThat(message.encode()).isEqualTo(expected);

        // check message header
        assertThat(message.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(message.getHeader().getAction()).isEqualTo(Act.REQUEST_BLOCKS);
    }

    @Test
    @Parameters(method = "correctParameters")
    public void testEncodeDecode(long start, int count, byte order) {
        // encode
        RequestBlocks message = new RequestBlocks(start, count, order == 1);

        assertThat(message.getStart()).isEqualTo(start);
        assertThat(message.getCount()).isEqualTo(count);
        assertThat(message.isDescending()).isEqualTo(order == 1);

        byte[] encoding = message.encode();

        // decode
        RequestBlocks decoded = RequestBlocks.decode(encoding);

        assertThat(decoded).isNotNull();

        assertThat(decoded.getStart()).isEqualTo(start);
        assertThat(decoded.getCount()).isEqualTo(count);
        assertThat(decoded.isDescending()).isEqualTo(order == 1);
    }

    @Test
    public void testEncode_differentStart() {
        byte[] encoding =
                RLP.encodeList(RLP.encode(1L), RLP.encodeInt(1), RLP.encodeByte((byte) 1));

        RequestBlocks message = new RequestBlocks(2L, 1, true);
        assertThat(message.encode()).isNotEqualTo(encoding);
    }

    @Test
    public void testEncode_differentCount() {
        byte[] encoding =
                RLP.encodeList(RLP.encode(1L), RLP.encodeInt(1), RLP.encodeByte((byte) 1));

        RequestBlocks message = new RequestBlocks(1L, 2, true);
        assertThat(message.encode()).isNotEqualTo(encoding);
    }

    @Test
    public void testEncode_differentOrder() {
        byte[] encoding =
                RLP.encodeList(RLP.encode(1L), RLP.encodeInt(1), RLP.encodeByte((byte) 1));

        RequestBlocks message = new RequestBlocks(1L, 1, false);
        assertThat(message.encode()).isNotEqualTo(encoding);
    }
}
