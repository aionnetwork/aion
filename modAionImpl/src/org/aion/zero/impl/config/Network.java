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

/**
 * Defines different Aion networks.
 *
 * @author Alexandra Roatis
 */
public enum Network {
    MAINNET("mainnet", 256),
    CONQUEST("conquest", 128),
    MASTERY("mastery", 32);

    private String name;
    private int identifier;

    /**
     * Constructor.
     *
     * @param _name network name
     * @param _identifier network identifier
     */
    Network(String _name, int _identifier) {
        this.name = _name;
        this.identifier = _identifier;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public int getIdentifier() {
        return identifier;
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
     * Utility method that determines the correct network based on the given identifier.
     *
     * @param identifier an integer value representing the network identifier
     * @return the network object corresponding to the int value or null when the value is not
     *     mapped to an object.
     */
    public static Network determineNetwork(int identifier) {
        for (Network net : Network.values()) {
            if (identifier == net.getIdentifier()) {
                return net;
            }
        }

        return null;
    }
}
