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
import java.util.List;
import java.util.Objects;

/**
 * @author chris
 */
// NOTE: only used by java api
public class FltrCt extends Fltr {

    private byte[] contractAddress;

    private String fromBlock;

    private String toBlock;

    private List<String> topics;

    private List<byte[]> addresses;

    private long expireTime;

    public FltrCt(byte[] contractAddress, String toBlock, String fromBlock, List<String> topics, List<byte[]> addrs, long time) {
        super(Type.EVENT);
        this.contractAddress = contractAddress;
        this.toBlock = toBlock;
        this.setFromBlock(fromBlock);
        this.topics = topics;
        this.setAddresses(addrs);
        this.setExpireTime(time);
    }

    public String getToBlock() {
        return this.toBlock;
    }

    public List<String> getTopics() {
        return this.topics;
    }

    public byte[] getContractAddr() {
        return this.contractAddress;
    }

    /**
     * verify if current log filter is for specific contract address
     */
    public boolean isFor(Address contractAddress, String topic) {
        // topic = "0x" + topic;
        if (!Arrays.equals(this.contractAddress, contractAddress.toBytes())) {
            return false;
        }
        return this.topics.stream().filter(s -> s.equals(topic)).findFirst().isPresent();
    }

    public String getFromBlock() {
        return fromBlock;
    }

    public void setFromBlock(String fromBlock) {
        this.fromBlock = fromBlock;
    }

    public List<byte[]> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<byte[]> addresses) {
        this.addresses = addresses;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

}