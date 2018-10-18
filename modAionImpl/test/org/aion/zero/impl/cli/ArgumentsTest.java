/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.zero.impl.cli;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/**
 * @author Alexandra Roatis
 */
public class ArgumentsTest {

    @Test
    public void testPreprocess_wCreate() {
        // first parameter
        String[] input =
            new String[]{
                "-a", "create", "-n", "mainnet", "-d", "abc",
            };
        String[] expected =
            new String[]{
                "-a create", "-n", "mainnet", "-d", "abc",
            };

        String[] actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // middle parameter
        input = new String[]{"-d", "abc", "-a", "create", "-n", "mainnet"};
        expected = new String[]{"-d", "abc", "-a create", "-n", "mainnet"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // last parameter
        input = new String[]{"-n", "mainnet", "-d", "abc", "-a", "create"};
        expected = new String[]{"-n", "mainnet", "-d", "abc", "-a create"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testPreprocess_wImport() {
        // first parameter
        String[] input =
            new String[]{
                "-a", "import", "0x123", "-n", "mainnet", "-d", "abc",
            };
        String[] expected =
            new String[]{
                "-a import", "0x123", "-n", "mainnet", "-d", "abc",
            };

        String[] actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // middle parameter
        input = new String[]{"-d", "abc", "-a", "import", "0x123", "-n", "mainnet"};
        expected = new String[]{"-d", "abc", "-a import", "0x123", "-n", "mainnet"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // last parameter
        input = new String[]{"-n", "mainnet", "-d", "abc", "-a", "import", "0x123"};
        expected = new String[]{"-n", "mainnet", "-d", "abc", "-a import", "0x123"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testPreprocess_wExport() {
        // first parameter
        String[] input =
            new String[]{
                "-a", "export", "0x123", "-n", "mainnet", "-d", "abc",
            };
        String[] expected =
            new String[]{
                "-a export", "0x123", "-n", "mainnet", "-d", "abc",
            };

        String[] actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // middle parameter
        input = new String[]{"-d", "abc", "-a", "export", "0x123", "-n", "mainnet"};
        expected = new String[]{"-d", "abc", "-a export", "0x123", "-n", "mainnet"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // last parameter
        input = new String[]{"-n", "mainnet", "-d", "abc", "-a", "export", "0x123"};
        expected = new String[]{"-n", "mainnet", "-d", "abc", "-a export", "0x123"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testPreprocess_wList() {
        // first parameter
        String[] input =
            new String[]{
                "-a", "list", "-n", "mainnet", "-d", "abc",
            };
        String[] expected =
            new String[]{
                "-a list", "-n", "mainnet", "-d", "abc",
            };

        String[] actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // middle parameter
        input = new String[]{"-d", "abc", "-a", "list", "-n", "mainnet"};
        expected = new String[]{"-d", "abc", "-a list", "-n", "mainnet"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // last parameter
        input = new String[]{"-n", "mainnet", "-d", "abc", "-a", "list"};
        expected = new String[]{"-n", "mainnet", "-d", "abc", "-a list"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testPreprocess_wSsl() {
        // first parameter
        String[] input =
            new String[]{
                "-s", "create", "-n", "mainnet", "-d", "abc",
            };
        String[] expected =
            new String[]{
                "-s create", "-n", "mainnet", "-d", "abc",
            };

        String[] actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // middle parameter
        input = new String[]{"-d", "abc", "-s", "create", "-n", "mainnet"};
        expected = new String[]{"-d", "abc", "-s create", "-n", "mainnet"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);

        // last parameter
        input = new String[]{"-n", "mainnet", "-d", "abc", "-s", "create"};
        expected = new String[]{"-n", "mainnet", "-d", "abc", "-s create"};

        actual = Arguments.preProcess(input);
        assertThat(actual).isEqualTo(expected);
    }
}
