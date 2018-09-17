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
package org.aion.zero.impl.config;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test functionality from {@link Network}.
 *
 * @author Alexandra Roatis
 */
@RunWith(JUnitParamsRunner.class)
public class NetworkTest {

    /** Parameters for testing {@link #testDetermineNetwork(String, Network)}. */
    @SuppressWarnings("unused")
    private Object networkMappings() {
        List<Object> parameters = new ArrayList<>();

        for (Network net : Network.values()) {
            parameters.add(new Object[] {net.toString(), net});
            parameters.add(new Object[] {net.toString().toUpperCase(), net});
        }

        parameters.add(new Object[] {"testnet", Network.MASTERY});
        parameters.add(new Object[] {"TESTNET", Network.MASTERY});

        parameters.add(new Object[] {"undefined", null});

        return parameters.toArray();
    }

    @Test
    @Parameters(method = "networkMappings")
    public void testDetermineNetwork(String input, Network expected) {
        assertThat(Network.determineNetwork(input)).isEqualTo(expected);
    }
}
