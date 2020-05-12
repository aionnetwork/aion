package org.aion.zero.impl.db;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;
import org.aion.rlp.RLP;
import org.aion.util.types.ByteArrayWrapper;
import org.junit.Test;

/**
 * Unit tests for {@link TransformedCodeInfo}.
 *
 * @author Geoff Stuart
 */
public class TransformedCodeInfoTest {

    private static final byte[] encoded123 = RLP.encodeElement(new byte[] {1, 2, 3});

    private static final ByteArrayWrapper hash1 = ByteArrayWrapper.wrap(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2 });

    private static final ByteArrayWrapper hash2 = ByteArrayWrapper.wrap(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 3, 4 });

    private static final byte[] code1 = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3 };

    private static final byte[] code2 = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 3, 4, 5 };

    @Test
    public void testDecode_nullMessage() {
        assertNull(TransformedCodeSerializer.RLP_SERIALIZER.deserialize(null));
    }

    @Test
    public void testDecode_emptyMessage() {
        assertNull(TransformedCodeSerializer.RLP_SERIALIZER.deserialize(new byte[0]));
    }

    @Test
    public void testDecode_notAList() {
        assertNull(TransformedCodeSerializer.RLP_SERIALIZER.deserialize(encoded123));
    }

    @Test
    public void testDecode_notASubList() {
        byte[] encoding = RLP.encodeList(encoded123);
        assertNull(TransformedCodeSerializer.RLP_SERIALIZER.deserialize(encoding));
    }

    @Test
    public void testDecode_informationNotAList() {
        byte[] encoding = RLP.encodeList(RLP.encodeList(hash1.toBytes(), encoded123));
        assertNull(TransformedCodeSerializer.RLP_SERIALIZER.deserialize(encoding));
    }

    @Test
    public void testDecode_informationKeyNotAHash() {
        byte[] encoding = RLP.encodeList(RLP.encodeList(encoded123, RLP.encodeList(encoded123)));
        assertNull(TransformedCodeSerializer.RLP_SERIALIZER.deserialize(encoding));
    }

    @Test
    public void testDecodeEncode_emptyInfo() {
        TransformedCodeInfo info = new TransformedCodeInfo();

        byte[] serialized = TransformedCodeSerializer.RLP_SERIALIZER.serialize(info);
        TransformedCodeInfo deserialized = TransformedCodeSerializer.RLP_SERIALIZER.deserialize(serialized);
        checkInfoEquals(info, deserialized);
    }

    @Test
    public void testDecodeEncode_oneInfo() {
        TransformedCodeInfo info = new TransformedCodeInfo();
        info.add(hash1, 1, code1);

        byte[] serialized = TransformedCodeSerializer.RLP_SERIALIZER.serialize(info);
        TransformedCodeInfo deserialized = TransformedCodeSerializer.RLP_SERIALIZER.deserialize(serialized);
        checkInfoEquals(info, deserialized);
    }

    @Test
    public void testDecodeEncode_twoInfo() {
        TransformedCodeInfo info = new TransformedCodeInfo();
        info.add(hash1, 1, code1);
        info.add(hash1, 2, code1);

        byte[] serialized = TransformedCodeSerializer.RLP_SERIALIZER.serialize(info);
        TransformedCodeInfo deserialized = TransformedCodeSerializer.RLP_SERIALIZER.deserialize(serialized);
        checkInfoEquals(info, deserialized);
    }

    @Test
    public void testDecodeEncode_sameInfo() {
        TransformedCodeInfo info = new TransformedCodeInfo();
        info.add(hash1, 1, code1);
        info.add(hash1, 1, code1);

        byte[] serialized = TransformedCodeSerializer.RLP_SERIALIZER.serialize(info);
        TransformedCodeInfo deserialized = TransformedCodeSerializer.RLP_SERIALIZER.deserialize(serialized);
        checkInfoEquals(info, deserialized);
    }

    @Test
    public void testDecodeEncode_notEquals() {
        TransformedCodeInfo info = new TransformedCodeInfo();
        info.add(hash1, 1, code1);

        byte[] serialized = TransformedCodeSerializer.RLP_SERIALIZER.serialize(info);
        info.add(hash1, 2, code1);
        TransformedCodeInfo deserialized = TransformedCodeSerializer.RLP_SERIALIZER.deserialize(serialized);
        assertNotEquals(info.transformedCodeMap, deserialized.transformedCodeMap);
    }

    @Test
    public void testEquals() {
        TransformedCodeInfo info1 = new TransformedCodeInfo();
        TransformedCodeInfo info2 = new TransformedCodeInfo();
        assertEquals(info1.transformedCodeMap, info2.transformedCodeMap);

        info1.add(hash1, 1, code1);
        info2.add(hash1, 1, code1);
        checkInfoEquals(info1, info2);

        info1.add(hash1, 2, code1);
        info2.add(hash1, 2, code2);
        assertTrue(checkInfoNotEqual(info1, info2));
    }

    @Test
    public void testInputAndRetrieval() {
        TransformedCodeInfo info = new TransformedCodeInfo();
        info.add(hash1, 1, code1);
        info.add(hash2, 1, code1);
        info.add(hash1, 2, code2);
        info.add(hash2, 2, code2);

        assertEquals(2, info.transformedCodeMap.size());
        assertEquals(2, info.transformedCodeMap.get(hash1).size());
        assertEquals(2, info.transformedCodeMap.get(hash2).size());
        assertArrayEquals(code1, info.transformedCodeMap.get(hash1).get(1));
        assertArrayEquals(code2, info.transformedCodeMap.get(hash1).get(2));
        assertArrayEquals(code1, info.transformedCodeMap.get(hash2).get(1));
        assertArrayEquals(code2, info.transformedCodeMap.get(hash2).get(2));
        assertArrayEquals(code1, info.getTransformedCode(hash1, 1));
        assertArrayEquals(code2, info.getTransformedCode(hash1, 2));
        assertArrayEquals(code1, info.getTransformedCode(hash2, 1));
        assertArrayEquals(code2, info.getTransformedCode(hash2, 2));

        byte[] serialized = TransformedCodeSerializer.RLP_SERIALIZER.serialize(info);
        TransformedCodeInfo deserialized = TransformedCodeSerializer.RLP_SERIALIZER.deserialize(serialized);
        checkInfoEquals(info, deserialized);

        // All the same test with the serialized and then deserialized data
        assertEquals(2, deserialized.transformedCodeMap.size());
        assertEquals(2, deserialized.transformedCodeMap.get(hash1).size());
        assertEquals(2, deserialized.transformedCodeMap.get(hash2).size());
        assertArrayEquals(code1, deserialized.transformedCodeMap.get(hash1).get(1));
        assertArrayEquals(code2, deserialized.transformedCodeMap.get(hash1).get(2));
        assertArrayEquals(code1, deserialized.transformedCodeMap.get(hash2).get(1));
        assertArrayEquals(code2, deserialized.transformedCodeMap.get(hash2).get(2));
        assertArrayEquals(code1, deserialized.getTransformedCode(hash1, 1));
        assertArrayEquals(code2, deserialized.getTransformedCode(hash1, 2));
        assertArrayEquals(code1, deserialized.getTransformedCode(hash2, 1));
        assertArrayEquals(code2, deserialized.getTransformedCode(hash2, 2));
    }

    private void checkInfoEquals(TransformedCodeInfo info1, TransformedCodeInfo info2) {
        for (Map.Entry<ByteArrayWrapper, Map<Integer, byte[]>> entry : info1.transformedCodeMap.entrySet()) {
            Map<Integer, byte[]> inner1 = entry.getValue();
            Map<Integer, byte[]> inner2 = info2.transformedCodeMap.get(entry.getKey());
            for (Map.Entry<Integer, byte[]> innerEntry : inner1.entrySet()) {
                assertArrayEquals(innerEntry.getValue(), inner2.get(innerEntry.getKey()));
            }
        }
    }

    private boolean checkInfoNotEqual(TransformedCodeInfo info1, TransformedCodeInfo info2) {
        for (Map.Entry<ByteArrayWrapper, Map<Integer, byte[]>> entry : info1.transformedCodeMap.entrySet()) {
            Map<Integer, byte[]> inner1 = entry.getValue();
            Map<Integer, byte[]> inner2 = info2.transformedCodeMap.get(entry.getKey());
            for (Map.Entry<Integer, byte[]> innerEntry : inner1.entrySet()) {
                if (!Arrays.equals(innerEntry.getValue(), inner2.get(innerEntry.getKey()))) {
                    return true;
                }
            }
        }
        return false;
    }
}
