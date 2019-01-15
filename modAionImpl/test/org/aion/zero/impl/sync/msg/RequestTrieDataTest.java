package org.aion.zero.impl.sync.msg;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.sync.DatabaseType.DETAILS;
import static org.aion.zero.impl.sync.DatabaseType.STATE;
import static org.aion.zero.impl.sync.DatabaseType.STORAGE;

import java.util.ArrayList;
import java.util.List;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.aion.rlp.RLP;
import org.aion.zero.impl.sync.DatabaseType;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link RequestTrieData} messages.
 *
 * @author Alexandra Roatis
 */
@RunWith(JUnitParamsRunner.class)
public class RequestTrieDataTest {
    public static final byte[] nodeKey =
            new byte[] {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
                24, 25, 26, 27, 28, 29, 30, 31, 32
            };
    public static final byte[] altNodeKey =
            new byte[] {
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
                23, 24, 25, 26, 27, 28, 29, 30, 31
            };
    public static final byte[] zeroNodeKey = new byte[32];
    public static final byte[] smallNodeKey =
            new byte[] {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
                24, 25, 26, 27, 28, 29, 30, 31
            };
    public static final byte[] largeNodeKey =
            new byte[] {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
                24, 25, 26, 27, 28, 29, 30, 31, 32, 33
            };
    public static final byte[] emptyByteArray = new byte[] {};

    /**
     * Parameters for testing:
     *
     * <ul>
     *   <li>{@link #testDecode_correct(byte[], DatabaseType, int)}
     *   <li>{@link #testEncode_correct(byte[], DatabaseType, int)}
     *   <li>{@link #testEncodeDecode(byte[], DatabaseType, int)}
     * </ul>
     */
    @SuppressWarnings("unused")
    private Object correctParameters() {
        List<Object> parameters = new ArrayList<>();

        byte[][] keyOptions = new byte[][] {nodeKey, altNodeKey, zeroNodeKey};
        DatabaseType[] dbOptions = new DatabaseType[] {STATE, STORAGE, DETAILS};
        int[] limitOptions = new int[] {0, 1, 10, Integer.MAX_VALUE};

        // network and directory
        String[] net_values = new String[] {"mainnet", "invalid"};
        for (byte[] key : keyOptions) {
            for (DatabaseType db : dbOptions) {
                for (int limit : limitOptions) {
                    parameters.add(new Object[] {key, db, limit});
                }
            }
        }

        return parameters.toArray();
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_nullKey() {
        new RequestTrieData(null, STATE, 10);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_nullType() {
        new RequestTrieData(nodeKey, null, 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_negativeLimit() {
        new RequestTrieData(nodeKey, STATE, -10);
    }

    @Test
    public void testDecode_nullMessage() {
        assertThat(RequestTrieData.decode(null)).isNull();
    }

    @Test
    public void testDecode_emptyMessage() {
        assertThat(RequestTrieData.decode(emptyByteArray)).isNull();
    }

    @Test
    public void testDecode_missingType() {
        byte[] encoding = RLP.encodeList(RLP.encodeElement(nodeKey), RLP.encodeInt(0));
        assertThat(RequestTrieData.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_missingKey() {
        byte[] encoding = RLP.encodeList(RLP.encodeString(STATE.toString()), RLP.encodeInt(0));
        assertThat(RequestTrieData.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_missingLimit() {
        byte[] encoding =
                RLP.encodeList(RLP.encodeElement(nodeKey), RLP.encodeString(STATE.toString()));
        assertThat(RequestTrieData.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_additionalValue() {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeElement(nodeKey),
                        RLP.encodeString(STATE.toString()),
                        RLP.encodeInt(0),
                        RLP.encodeInt(10));
        assertThat(RequestTrieData.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_outOfOrder() {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeString(STATE.toString()),
                        RLP.encodeElement(nodeKey),
                        RLP.encodeInt(0));
        assertThat(RequestTrieData.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_incorrectType() {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeElement(nodeKey), RLP.encodeString("random"), RLP.encodeInt(10));
        assertThat(RequestTrieData.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_smallerKeySize() {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeElement(smallNodeKey),
                        RLP.encodeString(STATE.toString()),
                        RLP.encodeInt(10));
        assertThat(RequestTrieData.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_largerKeySize() {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeElement(largeNodeKey),
                        RLP.encodeString(STATE.toString()),
                        RLP.encodeInt(10));
        assertThat(RequestTrieData.decode(encoding)).isNull();
    }

    @Test
    @Parameters(method = "correctParameters")
    public void testDecode_correct(byte[] key, DatabaseType dbType, int limit) {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeElement(key),
                        RLP.encodeString(dbType.toString()),
                        RLP.encodeInt(limit));

        RequestTrieData message = RequestTrieData.decode(encoding);

        assertThat(message).isNotNull();
        assertThat(message.getNodeKey()).isEqualTo(key);
        assertThat(message.getDbType()).isEqualTo(dbType);
        assertThat(message.getLimit()).isEqualTo(limit);
    }

    @Test
    @Parameters(method = "correctParameters")
    public void testEncode_correct(byte[] key, DatabaseType dbType, int limit) {
        byte[] expected =
                RLP.encodeList(
                        RLP.encodeElement(key),
                        RLP.encodeString(dbType.toString()),
                        RLP.encodeInt(limit));

        RequestTrieData message = new RequestTrieData(key, dbType, limit);
        assertThat(message.encode()).isEqualTo(expected);
    }

    @Test
    @Parameters(method = "correctParameters")
    public void testEncodeDecode(byte[] key, DatabaseType dbType, int limit) {
        // encode
        RequestTrieData message = new RequestTrieData(key, dbType, limit);

        assertThat(message.getNodeKey()).isEqualTo(key);
        assertThat(message.getDbType()).isEqualTo(dbType);
        assertThat(message.getLimit()).isEqualTo(limit);

        byte[] encoding = message.encode();

        // decode
        RequestTrieData decoded = RequestTrieData.decode(encoding);

        assertThat(decoded).isNotNull();

        assertThat(decoded.getNodeKey()).isEqualTo(key);
        assertThat(decoded.getDbType()).isEqualTo(dbType);
        assertThat(decoded.getLimit()).isEqualTo(limit);
    }

    @Test
    public void testEncode_differentKey() {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeElement(nodeKey),
                        RLP.encodeString(STATE.toString()),
                        RLP.encodeInt(Integer.MAX_VALUE));

        RequestTrieData message = new RequestTrieData(altNodeKey, STATE, Integer.MAX_VALUE);
        assertThat(message.encode()).isNotEqualTo(encoding);
    }

    @Test
    public void testEncode_differentType() {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeElement(nodeKey),
                        RLP.encodeString(STATE.toString()),
                        RLP.encodeInt(Integer.MAX_VALUE));

        RequestTrieData message = new RequestTrieData(nodeKey, STORAGE, Integer.MAX_VALUE);
        assertThat(message.encode()).isNotEqualTo(encoding);
    }

    @Test
    public void testEncode_differentLimit() {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeElement(nodeKey),
                        RLP.encodeString(STATE.toString()),
                        RLP.encodeInt(Integer.MAX_VALUE));

        RequestTrieData message = new RequestTrieData(nodeKey, STATE, 0);
        assertThat(message.encode()).isNotEqualTo(encoding);
    }
}
