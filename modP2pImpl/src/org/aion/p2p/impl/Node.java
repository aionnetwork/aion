/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.p2p.impl;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.aion.p2p.INode;

/*
 *
 * @author Chris
 * p2p://{node-id}@{ip}:{port}
 * node-id could be any non-empty string update to 36 bytes
 *
 */
public final class Node implements INode {

    private boolean fromBootList;

    /**
     * id != "" && version != "" && node on pending nodes => move to active
     * nodes
     */
    private byte[] id; // 36 bytes

    private int idHash;

    private int fullHash = -1;

    /**
     * for display only
     */
    private String idShort;

    private int version;

    private byte[] ip;

    private String ipStr;

    private int port = -1;
    private int portConnected = -1;

    private long timestamp;

    private long bestBlockNumber;

    private byte[] bestBlockHash;

    private byte[] totalDifficulty;

    private SocketChannel channel;

    private String type = "";

    private static final String REGEX_PROTOCOL = "^p2p://";                                                               // Protocol eg. p2p://
    private static final String REGEX_NODE_ID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";           // Node-Id  eg. 3e2cab6a-09dd-4771-b28d-6aa674009796
    private static final String REGEX_IPV4 = "(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])";  // Ip       eg. 127.0.0.1
    private static final String REGEX_PORT = "[0-9]+$";                                                                   // Port     eg. 30303

    private static final Pattern PATTERN_P2P = Pattern.compile(REGEX_PROTOCOL + REGEX_NODE_ID + "@" + REGEX_IPV4 + ":" + REGEX_PORT);

    PeerMetric peerMetric = new PeerMetric();

    /**
     * constructor for initial stage of connections from network
     */
    Node(boolean fromBootList, String _ipStr) {
        this.fromBootList = fromBootList;

        // if id is not gathered, leave it empty
        // this.id = new byte[36];
        this.idHash = 0;
        this.version = 0;
        this.ip = ipStrToBytes(_ipStr);
        this.ipStr = _ipStr;
        this.port = 0;
        this.timestamp = System.currentTimeMillis();
        this.bestBlockNumber = 0L;
    }

    Node(String _ipStr, int port, int portConnected) {
        this.fromBootList = false;
        // if id is not gathered, leave it empty
        // this.id = new byte[36];
        this.idHash = 0;
        this.version = 0;
        this.ip = ipStrToBytes(_ipStr);
        this.ipStr = _ipStr;
        this.port = port;
        this.portConnected = portConnected;
        this.timestamp = System.currentTimeMillis();
        this.bestBlockNumber = 0L;
    }

    /**
     * constructor for initial stage of boot nodes from config
     */
    public Node(boolean fromBootList, final byte[] _id, final byte[] _ip, final int _port) {
        this.fromBootList = fromBootList;
        this.id = _id;
        if (_id != null && _id.length == 36) {
            this.idHash = Arrays.hashCode(_id);
            this.idShort = new String(Arrays.copyOfRange(_id, 0, 6));
        }
        this.version = -1;
        this.ip = _ip;
        this.ipStr = ipBytesToStr(_ip);
        this.port = _port;
        this.timestamp = System.currentTimeMillis();
        this.bestBlockNumber = 0L;
    }

    /**
     * @param _ip String
     * @return byte[]
     */
    public static byte[] ipStrToBytes(final String _ip) {
        ByteBuffer bb8 = ByteBuffer.allocate(8);
        String[] frags = _ip.split("\\.");
        for (String frag : frags) {
            short ipFrag;
            try {
                ipFrag = Short.parseShort(frag);
            } catch (NumberFormatException e) {
                return new byte[0];
            }
            bb8.putShort(ipFrag);
        }
        return bb8.array();
    }

    /**
     * @param _ip byte[]
     * @return String
     */
    static String ipBytesToStr(final byte[] _ip) {
        ByteBuffer bb2 = ByteBuffer.allocate(2);
        if (_ip == null || _ip.length != 8)
            return "";
        else {
            String ip = "";
            bb2.put(_ip[0]);
            bb2.put(_ip[1]);
            bb2.flip();
            ip += bb2.getShort() + ".";

            bb2.clear();
            bb2.put(_ip[2]);
            bb2.put(_ip[3]);
            bb2.flip();
            ip += bb2.getShort() + ".";

            bb2.clear();
            bb2.put(_ip[4]);
            bb2.put(_ip[5]);
            bb2.flip();
            ip += bb2.getShort() + ".";

            bb2.clear();
            bb2.put(_ip[6]);
            bb2.put(_ip[7]);
            bb2.flip();
            ip += bb2.getShort();
            return ip;
        }
    }

    /**
     * @param _p2p String
     * @return Node
     * TODO: ugly
     */
    public static Node parseP2p(String _p2p) {
        if (!PATTERN_P2P.matcher(_p2p).matches())
            return null;

        String[] arrs = _p2p.split("@");
        byte[] _tempBytes = arrs[0].getBytes();

        byte[] _id = Arrays.copyOfRange(_tempBytes, 6, 42);
        String[] subArrs = arrs[1].split(":");

        byte[] _ip = ipStrToBytes(subArrs[0]);
        int _port = Integer.parseInt(subArrs[1]);

        return new Node(true, _id, _ip, _port);
    }

    void setFromBootList(boolean _ifBoot) {
        this.fromBootList = _ifBoot;
    }

    /**
     * @param _id byte[]
     */
    void setId(final byte[] _id) {
        this.id = _id;
        if (_id != null && _id.length == 36) {
            this.idHash = Arrays.hashCode(_id);
            this.idShort = new String(Arrays.copyOfRange(_id, 0, 6));
        }
    }

    /**
     * @param _version int
     */
    void setVersion(final int _version) {
        this.version = _version;
    }

    /**
     * @param _port int
     */
    void setPort(final int _port) {
        this.port = _port;
    }

    void setPortConnected(final int _port) {
        this.portConnected = _port;
    }

    /**
     * this method used to keep current node stage on either pending list or
     * active list
     */
    void refreshTimestamp() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * @param _channel SocketChannel
     */
    void setChannel(final SocketChannel _channel) {
        this.channel = _channel;
    }

    /**
     * @param _type String
     */
    void setType(String _type) {
        this.type = _type;
    }

    /**
     * @return boolean
     */
    boolean getIfFromBootList() {
        return this.fromBootList;
    }

    /**
     * @return int
     */
    int getVersion() {
        return this.version;
    }

    @Override
    public byte[] getIp() {
        return this.ip;
    }

    @Override
    public String getIpStr() {
        return this.ipStr;
    }

    @Override
    public int getPort() {
        return this.port;
    }

    public int getConnectedPort() {
        return portConnected;
    }

    /**
     * @return long
     */
    long getTimestamp() {
        return this.timestamp;
    }

    /**
     * @return SocketChannel
     */
    SocketChannel getChannel() {
        return this.channel;
    }

    @Override
    public byte[] getId() {
        return this.id;
    }

    @Override
    public int getIdHash() {
        return this.idHash;
    }

    String getType() {
        return this.type;
    }

    boolean hasFullInfo() {
        return (id != null) && (ip != null) && (port > 0);
    }

    int getFullHash() {
        if (fullHash > 0)
            return fullHash;
        else {
            if (hasFullInfo()) {
                ByteBuffer bb = ByteBuffer.allocate(id.length + ip.length + 4);
                bb.putInt(port);
                bb.put(id);
                bb.put(ip);
                return Arrays.hashCode(bb.array());
            }
        }
        return -1;
    }

    @Override
    public String getIdShort() {
        return this.idShort == null ? "" : this.idShort;
    }

    @Override
    public long getBestBlockNumber() {
        return this.bestBlockNumber;
    }

    byte[] getBestBlockHash() {
        return this.bestBlockHash;
    }

    @Override
    public byte[] getTotalDifficulty() {
        return this.totalDifficulty;
    }

    @Override
    public void updateStatus(long _bestBlockNumber, final byte[] _bestBlockHash, final byte[] _totalDifficulty) {
        if (_bestBlockNumber > this.bestBlockNumber) {
            this.bestBlockNumber = _bestBlockNumber;
            this.bestBlockHash = _bestBlockHash;
            this.totalDifficulty = _totalDifficulty;
        }
    }

    void copyNodeStatus(Node _n) {
        if (_n.bestBlockNumber > this.bestBlockNumber) {
            this.bestBlockNumber = _n.getBestBlockNumber();
            this.bestBlockHash = _n.bestBlockHash;
            this.totalDifficulty = _n.getTotalDifficulty();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Node) {
            Node other = (Node) o;
            return this.getFullHash() == other.getFullHash();
        }
        return false;
    }
}
