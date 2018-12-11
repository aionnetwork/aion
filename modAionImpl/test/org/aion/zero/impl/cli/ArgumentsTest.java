package org.aion.zero.impl.cli;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/** @author Alexandra Roatis */
public class ArgumentsTest {

    @Test
    public void testPreprocess_wCreate() {
        // first parameter
        String[] input =
                new String[] {
                    "-a", "create", "-n", "mainnet", "-d", "abc",
                };
        String[] expected =
                new String[] {
                    "-a create", "-n", "mainnet", "-d", "abc",
                };

        String[] actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // middle parameter
        input = new String[] {"-d", "abc", "-a", "create", "-n", "mainnet"};
        expected = new String[] {"-d", "abc", "-a create", "-n", "mainnet"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // last parameter
        input = new String[] {"-n", "mainnet", "-d", "abc", "-a", "create"};
        expected = new String[] {"-n", "mainnet", "-d", "abc", "-a create"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testPreprocess_wImport() {
        // first parameter
        String[] input =
                new String[] {
                    "-a", "import", "0x123", "-n", "mainnet", "-d", "abc",
                };
        String[] expected =
                new String[] {
                    "-a import", "0x123", "-n", "mainnet", "-d", "abc",
                };

        String[] actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // middle parameter
        input = new String[] {"-d", "abc", "-a", "import", "0x123", "-n", "mainnet"};
        expected = new String[] {"-d", "abc", "-a import", "0x123", "-n", "mainnet"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // last parameter
        input = new String[] {"-n", "mainnet", "-d", "abc", "-a", "import", "0x123"};
        expected = new String[] {"-n", "mainnet", "-d", "abc", "-a import", "0x123"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testPreprocess_wExport() {
        // first parameter
        String[] input =
                new String[] {
                    "-a", "export", "0x123", "-n", "mainnet", "-d", "abc",
                };
        String[] expected =
                new String[] {
                    "-a export", "0x123", "-n", "mainnet", "-d", "abc",
                };

        String[] actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // middle parameter
        input = new String[] {"-d", "abc", "-a", "export", "0x123", "-n", "mainnet"};
        expected = new String[] {"-d", "abc", "-a export", "0x123", "-n", "mainnet"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // last parameter
        input = new String[] {"-n", "mainnet", "-d", "abc", "-a", "export", "0x123"};
        expected = new String[] {"-n", "mainnet", "-d", "abc", "-a export", "0x123"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testPreprocess_wList() {
        // first parameter
        String[] input =
                new String[] {
                    "-a", "list", "-n", "mainnet", "-d", "abc",
                };
        String[] expected =
                new String[] {
                    "-a list", "-n", "mainnet", "-d", "abc",
                };

        String[] actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // middle parameter
        input = new String[] {"-d", "abc", "-a", "list", "-n", "mainnet"};
        expected = new String[] {"-d", "abc", "-a list", "-n", "mainnet"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // last parameter
        input = new String[] {"-n", "mainnet", "-d", "abc", "-a", "list"};
        expected = new String[] {"-n", "mainnet", "-d", "abc", "-a list"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);
    }

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
