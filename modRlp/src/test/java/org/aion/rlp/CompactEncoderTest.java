package org.aion.rlp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertArrayEquals;

import java.util.Arrays;
import org.junit.Test;

public class CompactEncoderTest {

    private static final byte T = 16; // terminator

    @Test
    public void testCompactEncode_wEmptyArray() {
        byte[] test = new byte[] {};
        byte[] expectedData = new byte[] {0x00};
        byte[] actualData = CompactEncoder.packNibbles(test);
        assertArrayEquals("odd terminated compact encode fail", expectedData, actualData);
        assertThat(CompactEncoder.hasTerminator(actualData)).isFalse();
    }

    @Test
    public void testCompactEncode_wSingleValueArray() {
        byte[] test = new byte[] {1};
        byte[] expectedData = new byte[] {0x11};
        byte[] actualData = CompactEncoder.packNibbles(test);
        assertArrayEquals("odd terminated compact encode fail", expectedData, actualData);
        assertThat(CompactEncoder.hasTerminator(actualData)).isFalse();
    }

    @Test
    public void testCompactEncodeOddCompact() {
        byte[] test = new byte[] {1, 2, 3, 4, 5};
        byte[] expectedData = new byte[] {0x11, 0x23, 0x45};
        byte[] actualData = CompactEncoder.packNibbles(test);
        assertArrayEquals("odd compact encode fail", expectedData, actualData);
        assertThat(CompactEncoder.hasTerminator(actualData)).isFalse();
    }

    @Test
    public void testCompactEncodeEvenCompact() {
        byte[] test = new byte[] {0, 1, 2, 3, 4, 5};
        byte[] expectedData = new byte[] {0x00, 0x01, 0x23, 0x45};
        byte[] actualData = CompactEncoder.packNibbles(test);
        assertArrayEquals("even compact encode fail", expectedData, actualData);
        assertThat(CompactEncoder.hasTerminator(actualData)).isFalse();
    }

    @Test
    public void testCompactEncode_wEmptyTerminatedArray() {
        byte[] test = new byte[] {T};
        byte[] expectedData = new byte[] {0x20};
        byte[] actualData = CompactEncoder.packNibbles(test);
        assertArrayEquals("odd terminated compact encode fail", expectedData, actualData);
        assertThat(CompactEncoder.hasTerminator(actualData)).isTrue();
    }

    @Test
    public void testCompactEncode_wSingleValueTerminatedArray() {
        byte[] test = new byte[] {2, T};
        byte[] expectedData = new byte[] {0x32};
        byte[] actualData = CompactEncoder.packNibbles(test);
        assertArrayEquals("odd terminated compact encode fail", expectedData, actualData);
        assertThat(CompactEncoder.hasTerminator(actualData)).isTrue();
    }

    @Test
    public void testCompactEncodeEvenTerminated() {
        byte[] test = new byte[] {0, 15, 1, 12, 11, 8, T};
        byte[] expectedData = new byte[] {0x20, 0x0f, 0x1c, (byte) 0xb8};
        byte[] actualData = CompactEncoder.packNibbles(test);
        assertArrayEquals("even terminated compact encode fail", expectedData, actualData);
        assertThat(CompactEncoder.hasTerminator(actualData)).isTrue();
    }

    @Test
    public void testCompactEncodeOddTerminated() {
        byte[] test = new byte[] {15, 1, 12, 11, 8, T};
        byte[] expectedData = new byte[] {0x3f, 0x1c, (byte) 0xb8};
        byte[] actualData = CompactEncoder.packNibbles(test);
        assertArrayEquals("odd terminated compact encode fail", expectedData, actualData);
        assertThat(CompactEncoder.hasTerminator(actualData)).isTrue();
    }

    @Test(expected = NullPointerException.class)
    public void testCompactEncode_wNullArray() {
        CompactEncoder.packNibbles(null);
    }

    @Test(expected = NullPointerException.class)
    public void testHasTerminator_wNullArray() {
        CompactEncoder.hasTerminator(null);
    }

    @Test
    public void testCompactDecodeOddCompact() {
        byte[] test = new byte[] {0x11, 0x23, 0x45};
        byte[] expected = new byte[] {1, 2, 3, 4, 5};
        assertArrayEquals(
                "odd compact decode fail", expected, CompactEncoder.unpackToNibbles(test));
    }

    @Test
    public void testCompactDecodeEvenCompact() {
        byte[] test = new byte[] {0x00, 0x01, 0x23, 0x45};
        byte[] expected = new byte[] {0, 1, 2, 3, 4, 5};
        assertArrayEquals(
                "even compact decode fail", expected, CompactEncoder.unpackToNibbles(test));
    }

    @Test
    public void testCompactDecodeEvenTerminated() {
        byte[] test = new byte[] {0x20, 0x0f, 0x1c, (byte) 0xb8};
        byte[] expected = new byte[] {0, 15, 1, 12, 11, 8, T};
        assertArrayEquals(
                "even terminated compact decode fail",
                expected,
                CompactEncoder.unpackToNibbles(test));
    }

    @Test
    public void testCompactDecodeOddTerminated() {
        byte[] test = new byte[] {0x3f, 0x1c, (byte) 0xb8};
        byte[] expected = new byte[] {15, 1, 12, 11, 8, T};
        assertArrayEquals(
                "odd terminated compact decode fail",
                expected,
                CompactEncoder.unpackToNibbles(test));
    }

    @Test
    public void testCompactHexEncode_1() {
        byte[] test = "stallion".getBytes();
        byte[] expected = new byte[] {7, 3, 7, 4, 6, 1, 6, 12, 6, 12, 6, 9, 6, 15, 6, 14, T};
        assertThat(CompactEncoder.binToNibbles(test)).isEqualTo(expected);
        assertThat(CompactEncoder.binToNibblesNoTerminator(test))
                .isEqualTo(Arrays.copyOf(expected, expected.length - 1));
    }

    @Test
    public void testCompactHexEncode_2() {
        byte[] test = "verb".getBytes();
        byte[] expected = new byte[] {7, 6, 6, 5, 7, 2, 6, 2, T};
        assertThat(CompactEncoder.binToNibbles(test)).isEqualTo(expected);
        assertThat(CompactEncoder.binToNibblesNoTerminator(test))
                .isEqualTo(Arrays.copyOf(expected, expected.length - 1));
    }

    @Test
    public void testCompactHexEncode_3() {
        byte[] test = "puppy".getBytes();
        byte[] expected = new byte[] {7, 0, 7, 5, 7, 0, 7, 0, 7, 9, T};
        assertThat(CompactEncoder.binToNibbles(test)).isEqualTo(expected);
        assertThat(CompactEncoder.binToNibblesNoTerminator(test))
                .isEqualTo(Arrays.copyOf(expected, expected.length - 1));
    }
}
