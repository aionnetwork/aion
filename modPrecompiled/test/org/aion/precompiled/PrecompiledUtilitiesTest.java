package org.aion.precompiled;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class PrecompiledUtilitiesTest {

    @Test
    public void testPad_wLargerInputLength() {
        byte[] input = new byte[] {1, 2, 3};
        int newLength = input.length - 1;
        // returns null
        assertThat(PrecompiledUtilities.pad(input, newLength)).isNull();
    }

    @Test
    public void testPad_wEqualLengths() {
        byte[] input = new byte[] {1, 2, 3};
        int newLength = input.length;
        // unchanged
        assertThat(PrecompiledUtilities.pad(input, newLength)).isEqualTo(input);
    }

    @Test
    public void testPad_wSmallerInputLength() {
        byte[] input = new byte[] {1, 2, 3};
        int newLength = input.length + 1;
        byte[] expected = new byte[] {0, 1, 2, 3};
        assertThat(PrecompiledUtilities.pad(input, newLength)).isEqualTo(expected);
    }
}
