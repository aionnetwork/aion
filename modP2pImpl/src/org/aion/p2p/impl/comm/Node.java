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

package org.aion.p2p.impl.comm;

import java.math.BigInteger;
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

    private static final Pattern PATTERN_P2P = Pattern.compile("^p2p://[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}@(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5]):[0-9]+$");

    private static final int SIZE_BYTES_IPV4 = 8;

    private SocketChannel channel;

    private boolean fromBootList;

    private byte[] id; // 36 bytes

    private int idHash;

    private String idShort;

    private byte[] ip;

    private String ipStr;

    private int port;

    private long timestamp;

    private long bestBlockNumber;

    private byte[] bestBlockHash;

    private BigInteger totalDifficulty = BigInteger.ZERO;

    private String binaryVersion = "";

    /**
     * for log display indicates current node inboundOrOutbound is constructed by inbound
     * inboundOrOutbound or outbound inboundOrOutbound
     */
    private String inboundOrOutbound = "";



    public NodeStats nodeStats = new NodeStats();

    /**
     *
     * @param _channel SocketChannel
     * @param _ipStr String
     * @param _port int
     * constructor for initial stage of incoming connections from network
     */
    public Node(SocketChannel _channel, String _ipStr, int _port) {
        this.channel = _channel;
        this.fromBootList = false;
        this.idHash = 0;
        this.ip = ipStrToBytes(_ipStr);
        this.ipStr = _ipStr;
        this.port = _port;
        this.timestamp = System.currentTimeMillis();
        this.bestBlockNumber = 0L;
        this.inboundOrOutbound = "inbound";
    }

    /**
     * constructor for initial stage of boot nodes from config or from list of active nodes response
     */
    public Node(boolean fromBootList, final byte[] _id, final byte[] _ip, int _port) {
        this.fromBootList = fromBootList;
        this.id = _id;
        this.idHash = Arrays.hashCode(_id);
        this.idShort = new String(Arrays.copyOfRange(_id, 0, 6));
        this.ip = _ip;
        this.ipStr = ipBytesToStr(_ip);
        this.port = _port;
        this.timestamp = System.currentTimeMillis();
        this.bestBlockNumber = 0L;
        this.inboundOrOutbound = "outbound";
    }

    /**
     * @param _ip String
     * @return byte[]
     */
    public static byte[] ipStrToBytes(String _ip) {
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
    public static String ipBytesToStr(final byte[] _ip) {
        if (_ip == null || _ip.length != SIZE_BYTES_IPV4)
            return "";
        else {
            short[] shorts = new short[_ip.length / 2];
            ByteBuffer.wrap(_ip).asShortBuffer().get(shorts);

            StringBuilder ip = new StringBuilder();
            for (int i = 0; i < shorts.length; i++) {
                ip.append(shorts[i]).append(i < shorts.length - 1 ? "." : "");
            }

            return ip.toString();
        }
    }

    /**
     * @param _p2p String
     * @return Node TODO: ugly
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

    /**
     * @param _id byte[]
     */
    public void setId(final byte[] _id) {
        this.id = _id;
        if (_id != null && _id.length == 36) {
            this.idHash = Arrays.hashCode(_id);
            this.idShort = new String(Arrays.copyOfRange(_id, 0, 6));
        }
    }

    /**
     * @param _port int
     */
    public void setPort(final int _port) {
        this.port = _port;
    }

    public void setBinaryVersion(String _revision) {
        this.binaryVersion = _revision;
    }

    /**
     * this method used to keep current node stage on either pending list or active
     * list
     */
    public void refreshTimestamp() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * @return boolean
     */
    public boolean getIfFromBootList() {
        return this.fromBootList;
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

    @Override
    public long getTimestamp() {
        return this.timestamp;
    }

    String getBinaryVersion() {
        return this.binaryVersion;
    }

    public void setChannel(final SocketChannel _channel){
        this.channel = _channel;
    }

    /**
     * @return SocketChannel
     */
    public SocketChannel getChannel() {
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

    @Override
    public String getIdShort() {
        return this.idShort == null ? "" : this.idShort;
    }

    /**
     * @return String
     */
    String getInboundOrOutbound() {
        return this.inboundOrOutbound;
    }

    @Override
    public long getBestBlockNumber() {
        return this.bestBlockNumber;
    }

    @Override
    public byte[] getBestBlockHash() {
        return this.bestBlockHash;
    }

    @Override
    public BigInteger getTotalDifficulty() {
        return this.totalDifficulty;
    }

    @Override
    public void updateStatus(long _bestBlockNumber, final byte[] _bestBlockHash, BigInteger _totalDifficulty) {
        this.bestBlockNumber = _bestBlockNumber;
        this.bestBlockHash = _bestBlockHash;
        this.totalDifficulty = _totalDifficulty == null ? BigInteger.ZERO : _totalDifficulty;
    }

}
