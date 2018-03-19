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
import org.aion.base.util.ByteUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author chris use at eth_newFilter & eth_getLogs
 */

public class ArgFltr {

    public String fromBlock = "latest";
    public String toBlock = "latest";
    public List<byte[]> address = new ArrayList<>();
    public List<byte[][]> topics = new ArrayList<>();

    public ArgFltr() {}

    public ArgFltr(String fromBlock, String toBlock, List<byte[]> address, List<byte[][]> topics) {
        this.fromBlock = fromBlock;
        this.toBlock = toBlock;
        this.address = address;
        this.topics = topics;
    }

    // implements validation of JSONObject received from user to create new filter (web3)
    // https://github.com/ethereum/wiki/wiki/JSON-RPC#eth_newfilter
    public static ArgFltr fromJSON(final JSONObject json){
        try {
            String fromBlock = json.optString("fromBlock", "latest");
            String toBlock = json.optString("toBlock", "latest");

            // user can choose to pass in either one address, or an array of addresses
            List<byte[]> address = new ArrayList<>();

            if (!json.isNull("address")) {
                JSONArray addressList = json.optJSONArray("address");
                if (addressList == null) {
                    // let the address constructor do the validation on the string
                    // it will throw if user passes invalid address
                    address.add((new Address(json.optString("address", null))).toBytes());
                } else {
                    for (int i = 0; i < addressList.length(); i++) {
                        address.add((new Address(addressList.optString(i, null))).toBytes());
                    }
                }
            }
            /*
                Topic Parsing Rules:
                -------------------
                Topics are order-dependent. A transaction with a log with topics [A, B] will be matched by
                the following topic filters:

                + [] "anything"
                + [A] "A in first position (and anything after)"
                + [null, B] "anything in first position AND B in second position (and anything after)"
                + [A, B] "A in first position AND B in second position (and anything after)"
                + [[A, B], [A, B]] "(A OR B) in first position AND (A OR B) in second position (and anything after)"
            */
            List<byte[][]> topics = new ArrayList<>();
            JSONArray topicList = json.optJSONArray("topics");
            if (topicList != null) {
                for (Object topic : topicList) {
                    if (topic == null || topic.equals(null)) {
                        topics.add((byte[][]) null);
                    } else if (topic instanceof String) {
                        byte[][] data = new byte[1][];
                        data[0] = ArgFltr.parseTopic((String) topic);
                        topics.add(data);
                    } else if (topic instanceof JSONArray) {
                        // at this point, we should have only strings in this array.
                        // invalid input if assumption not valid
                        JSONArray subTopicList = (JSONArray) topic;
                        List<byte[]> t = new ArrayList<>();
                        for (int i = 0; i < subTopicList.length(); i++) {
                            // ok to throw since, OR clause should not have any nulls
                            String topicString = subTopicList.getString(i);
                            t.add(parseTopic(topicString));
                        }
                        topics.add(t.toArray(new byte[0][]));
                    }
                    // else just ignore the json node.
                }
            }

            return new ArgFltr(fromBlock, toBlock, address, topics);
        } catch(Exception ex) {
            return null;
        }
    }

    // validates the input topic. pads with leading 0's, validates length
    // TODO: somehow, delegate this to the vm so we are topic-word-length-invariant
    private static byte[] parseTopic(String data) {
        byte[] input = ByteUtil.hexStringToBytes(data);
        byte[] topic = new byte[32];

        if (input.length == 32)
            topic = input;
        else if (input.length <= 32)
            System.arraycopy(input, 0, topic, 32 - input.length, input.length);
        else
            throw new RuntimeException("Data word can't be null or exceed 32 bytes: " + data);

        return topic;
    }

    // TODO: better toString()
    @Override
    public String toString() {
        return "FilterRequest{" +
                "fromBlock='" + fromBlock + '\'' +
                ", toBlock='" + toBlock + '\'' +
                ", address=" + address +
                ", topics=" + topics +
                '}';
    }
}