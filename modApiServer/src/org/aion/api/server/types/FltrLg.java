/*******************************************************************************
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
 *     
 ******************************************************************************/

package org.aion.api.server.types;

import org.aion.base.type.Address;

import java.util.Arrays;

/**
 * @author chris Multiple clients usage might cause FltrLg run out queue max
 *         fast TODO: implement usage per client
 */

public final class FltrLg extends Fltr {

    private Address contractAddress;

    /**
     * set value when FltrLg initialed clear this filter at onBlock event if
     * initialToBlock != "latest" which means end user is not attend to watch
     * continue binded events TODO: due to its http post rpc callback, server
     * cannot detect user is stop calling which would cause this Fltr is left in
     * installedFilters forever. Clear this filtr if current incoming block
     * number is bigger than initialToBlock and initialBlock != "latest"
     */
    private String initialToBlock;

    private String[] topics;

    public FltrLg(final Address contractAddress, final String initialToBlock, final String topicsStr) {
        super(Fltr.Type.LOG);
        this.contractAddress = contractAddress;
        this.initialToBlock = initialToBlock;
        this.topics = (topicsStr == null || topicsStr.equals("")) ? new String[0] : topicsStr.split(",");
    }

    /**
     * 
     * used on event hook up to remove expired fltr
     */
    public String getInitialToBlock() {
        return this.initialToBlock;
    }

    public String[] getLogs() {
        return this.topics;
    }

    /**
     * verify if current log filter is for specific contract address
     */
    public boolean isFor(byte[] contractAddress, String topic) {

        // System.out.println(com.nuco.util.TypeConverter.toJsonHex(this.contractAddress));
        // System.out.println(com.nuco.util.TypeConverter.toJsonHex(contractAddress));
        // System.out.println(String.join(",", this.topics));
        // System.out.println(topic);
        // System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

        topic = "0x" + topic;
        if (!this.contractAddress.equals(contractAddress))
            return false;
        if (this.topics.length > 0) {
            boolean contains = false;
            check: for (int i = 0, m = this.topics.length; i < m; i++) {
                if (this.topics[i].equals(topic)) {
                    contains = true;
                    // System.out.println("DataWord matched");
                    break check;
                }

            }
            return contains;
        } else
            return true;
    }

}