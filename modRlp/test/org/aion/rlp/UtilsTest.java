package org.aion.rlp;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.rlp.Utils.nibblesToPrettyString;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import org.junit.Ignore;
import org.junit.Test;

public class UtilsTest {

    @Test
    public void testHexEncode_wNull() {
        assertThat(Utils.hexEncode(null)).isNull();
    }

    @Test
    @Ignore
    public void testHexEncode_wSingleByte() throws IOException {
        for (byte b : Utils.encodingTable) {
            byte[] input = new byte[] {b};
            assertThat(Utils.hexEncode(input)).isEqualTo(Hex.encode(input));
        }
    }

    @Test
    @Ignore
    public void testHexEncode_woTerminatorByte() throws IOException {
        String value = "1234567890abcdef";
        byte[] input = Hex.decode(value);

        assertThat(Utils.hexEncode(input)).isEqualTo(Hex.encode(input));
        assertThat(Hex.decode(Utils.hexEncode(input))).isEqualTo(input);
    }

    @Test
    @Ignore
    public void testHexEncode_wTerminatorByte() throws IOException {
        String value = "1234567890abcdef";
        byte[] input = Hex.decode(value);

        // expecting an extra byte at the end of the array
        byte[] expected = Hex.encode(input);
        expected = Arrays.copyOf(expected, expected.length + 1);

        byte[] actual = Utils.hexEncodeWithTerminatorByte(input);
        assertThat(actual).isEqualTo(expected);
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

    @Test
    public void testNiceNiblesOutput_1() {
        byte[] test = {7, 0, 7, 5, 7, 0, 7, 0, 7, 9};
        String result = "\\x07\\x00\\x07\\x05\\x07\\x00\\x07\\x00\\x07\\x09";
        assertEquals(result, nibblesToPrettyString(test));
    }

    @Test
    public void testNiceNiblesOutput_2() {
        byte[] test = {7, 0, 7, 0xf, 7, 0, 0xa, 0, 7, 9};
        String result = "\\x07\\x00\\x07\\x0f\\x07\\x00\\x0a\\x00\\x07\\x09";
        assertEquals(result, nibblesToPrettyString(test));
    }

    @Test
    public void testToHexString() {
        assertEquals("", Hex.toHexString(null));
    }

    @Test
    public void testToHexString2() {

        byte[] testInput =
                new byte[] {
                    (byte) 0x01,
                    (byte) 0x23,
                    (byte) 0x45,
                    (byte) 0x67,
                    (byte) 0x89,
                    (byte) 0xab,
                    (byte) 0xcd,
                    (byte) 0xef
                };

        assertEquals("0123456789abcdef", Hex.toHexString(testInput));
    }
}
