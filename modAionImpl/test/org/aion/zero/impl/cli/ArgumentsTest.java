package org.aion.zero.impl.cli;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/** @author Alexandra Roatis */
public class ArgumentsTest {

    @Test
    public void testPreprocess_wSsl() {
        // first parameter
        String[] input =
                new String[] {
                    "-s", "create", "-n", "mainnet", "-d", "abc",
                };
        String[] expected =
                new String[] {
                    "-s create", "-n", "mainnet", "-d", "abc",
                };

        String[] actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // middle parameter
        input = new String[] {"-d", "abc", "-s", "create", "-n", "mainnet"};
        expected = new String[] {"-d", "abc", "-s create", "-n", "mainnet"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // last parameter
        input = new String[] {"-n", "mainnet", "-d", "abc", "-s", "create"};
        expected = new String[] {"-n", "mainnet", "-d", "abc", "-s create"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);
    }
}
