package org.aion.rlp;

import static com.google.common.truth.Truth.assertThat;

import java.util.Arrays;
import org.aion.util.conversions.Hex;
import org.junit.Ignore;
import org.junit.Test;

public class UtilsTest {

    @Test
    public void testHexEncode_wNull() {
        assertThat(Utils.hexEncode(null)).isNull();
    }

    @Test
    @Ignore
    public void testHexEncode_wSingleByte() {
        for (byte b : Utils.encodingTable) {
            byte[] input = new byte[] {b};
            assertThat(Utils.hexEncode(input)).isEqualTo(Hex.encode(input));
        }
    }

    @Test
    @Ignore
    public void testHexEncode_woTerminatorByte() {
        String value = "1234567890abcdef";
        byte[] input = Hex.decode(value);

        assertThat(Utils.hexEncode(input)).isEqualTo(Hex.encode(input));
        assertThat(Hex.decode(Utils.hexEncode(input))).isEqualTo(input);
    }

    @Test
    @Ignore
    public void testHexEncode_wTerminatorByte() {
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
}
