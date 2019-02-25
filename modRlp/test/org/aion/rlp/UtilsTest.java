package org.aion.rlp;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.rlp.Utils.encodingTable;
import static org.aion.rlp.Utils.hexMap;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.aion.util.conversions.Hex;
import org.junit.Test;

public class UtilsTest {

    @Test
    public void testHexEncode_wNull() {
        assertThat(Utils.hexEncode(null)).isNull();
    }

    @Test
    public void testHexEncode_wSingleByte() {
        for (byte b : encodingTable) {
            byte[] input = new byte[] {b};

            byte[] rlpHexEncodeOutput = Utils.hexEncode(input);
            byte[] hexEncodeOutput = Hex.encode(input);
            assertEquals(rlpHexEncodeOutput.length, hexEncodeOutput.length);
            for (int i = 0; i < rlpHexEncodeOutput.length; i++) {
                assertThat(rlpHexEncodeOutput[i])
                        .isEqualTo(hexMap.get((char) encodingTable[hexEncodeOutput[i] & 0xf]));
            }
        }
    }

    @Test
    public void testHexEncode_woTerminatorByte() {
        String value = "1234567890abcdef";
        byte[] input = Hex.decode(value);

        byte[] rlpHexEncodeOutput = Utils.hexEncode(input);
        byte[] expectOutput = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 10, 11, 12, 13, 14, 15};

        assertArrayEquals(rlpHexEncodeOutput, expectOutput);
    }

    @Test
    public void testHexEncode_wTerminatorByte() {
        String value = "1234567890abcdef";
        byte[] input = Hex.decode(value);

        // expecting an extra byte at the end of the array
        byte[] expectOutput = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 10, 11, 12, 13, 14, 15, 16};

        byte[] rlpHexEncodeOutput = Utils.hexEncodeWithTerminatorByte(input);

        assertArrayEquals(rlpHexEncodeOutput, expectOutput);
    }

    @Test(expected = NullPointerException.class)
    public void testConcatenate_wNullA() {
        Utils.concatenate(new byte[] {1, 2}, null);
    }

    @Test(expected = NullPointerException.class)
    public void testConcatenate_wNullB() {
        Utils.concatenate(null, new byte[] {1, 2});
    }

    @Test
    public void testConcatenate() {
        assertThat(Utils.concatenate(new byte[] {1, 2}, new byte[] {3, 4}))
                .isEqualTo(new byte[] {1, 2, 3, 4});
    }

    @Test
    public void testConcatenate_wEmptyFirstArray() {
        assertThat(Utils.concatenate(new byte[] {}, new byte[] {3, 4}))
                .isEqualTo(new byte[] {3, 4});
    }

    @Test
    public void testConcatenate_wEmptySecondArray() {
        assertThat(Utils.concatenate(new byte[] {1, 2}, new byte[] {}))
                .isEqualTo(new byte[] {1, 2});
    }

    @Test
    public void testConcatenate_wEmptyArrays() {
        assertThat(Utils.concatenate(new byte[] {}, new byte[] {})).isEqualTo(new byte[] {});
    }
}
