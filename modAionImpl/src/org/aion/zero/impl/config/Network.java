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

import java.util.Arrays;

/**
 * Defines different Aion networks.
 *
 * @author Alexandra Roatis
 */
public enum Network {
    MAINNET("mainnet", 256),
    CONQUEST("conquest", 128),
    MASTERY("mastery", 32),
    AVMTESTNET("avmtestnet", 31), // temporary chainid
    CUSTOM("custom", 0);

    private final String name;
    private int chainId;

    /**
     * Constructor.
     *
     * @param _name network name
     * @param _chainId chain identifier
     */
    Network(String _name, int _chainId) {
        this.name = _name;
        this.chainId = _chainId;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public int getChainId() {
        return chainId;
    }

    /**
     * Utility method that determines the correct network based on the given name.
     *
     * @param network a string value representing the network name
     * @return the network object corresponding to the string value or null when the value is not
     *     mapped to an object.
     * @implNote There may be several test networks active. The term {@code testnet} is defined to
     *     correspond to the current default test network.
     */
    public static Network determineNetwork(String network) {
        String netStr = network.toLowerCase();

        if (netStr.equals("testnet")) {
            return Network.MASTERY;
        }

        for (Network net : Network.values()) {
            if (netStr.equals(net.toString())) {
                return net;
            }
        }

        return null;
    }

    /**
     * Utility method that determines the correct network based on the given chain identifier.
     *
     * @param chainId a positive integer value representing the network chain identifier
     * @return the network object corresponding to the int value or null when the value is not
     *     mapped to an object.
     */
    public static Network determineNetwork(int chainId) {
        if (chainId < 0) {
            return null;
        }

        for (Network net : Network.values()) {
            if (chainId == net.getChainId()) {
                return net;
            }
        }

        // custom networks may have any positive chainId not already taken by other defined networks
        Network net = Network.CUSTOM;
        net.chainId = chainId;
        return net;
    }

    public static String valuesString() {
        String output = Arrays.toString(Network.values());
        return output.substring(1, output.length() - 1);
    }

    /**
     * Generates a custom network with the given chain identifier.
     *
     * @param chainId a positive integer value representing the network chain identifier
     * @return a custom network object with the given chain identifier.
     */
    public static Network getCustomNet(int chainId) {
        if (chainId < 0) {
            return null;
        }
        Network net = Network.CUSTOM;
        net.chainId = chainId;
        return net;
    }
}
