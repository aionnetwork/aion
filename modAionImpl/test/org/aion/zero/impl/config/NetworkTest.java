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
    private Object stringToNetworkMappings() {
        List<Object> parameters = new ArrayList<>();

        for (Network net : Network.values()) {
            parameters.add(new Object[] {net.toString(), net});
            parameters.add(new Object[] {net.toString().toUpperCase(), net});
        }

        parameters.add(new Object[] {"testnet", Network.MASTERY});
        parameters.add(new Object[] {"TESTNET", Network.MASTERY});

        parameters.add(new Object[] {"custom", Network.CUSTOM});
        parameters.add(new Object[] {"CUSTOM", Network.CUSTOM});
        parameters.add(new Object[] {"custom", Network.getCustomNet(1000)});

        parameters.add(new Object[] {"undefined", null});

        return parameters.toArray();
    }

    @Test
    @Parameters(method = "stringToNetworkMappings")
    public void testDetermineNetwork(String input, Network expected) {
        assertThat(Network.determineNetwork(input)).isEqualTo(expected);
    }

    /** Parameters for testing {@link #testDetermineNetwork(int, Network)}. */
    @SuppressWarnings("unused")
    private Object intToNetworkMappings() {
        List<Object> parameters = new ArrayList<>();

        for (Network net : Network.values()) {
            parameters.add(new Object[] {net.getChainId(), net});
        }

        parameters.add(new Object[] {-1, null});
        parameters.add(new Object[] {1000, Network.getCustomNet(1000)});

        return parameters.toArray();
    }

    @Test
    @Parameters(method = "intToNetworkMappings")
    public void testDetermineNetwork(int input, Network expected) {
        assertThat(Network.determineNetwork(input)).isEqualTo(expected);
    }
}
