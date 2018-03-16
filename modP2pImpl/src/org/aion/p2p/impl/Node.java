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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.aion.p2p.INode;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/*
 *
 * @author Chris
 * p2p://{node-id}@{ip}:{port}
 * node-id could be any non-empty string update to 36 bytes
 *
 */
public final class Node implements INode {

    private static final String REGEX_PROTOCOL = "^p2p://";                                                               // Protocol eg. p2p://
    private static final String REGEX_NODE_ID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";           // Node-Id  eg. 3e2cab6a-09dd-4771-b28d-6aa674009796
    private static final String REGEX_IPV4 = "(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])";  // Ip       eg. 127.0.0.1
    private static final String REGEX_PORT = "[0-9]+$";                                                                   // Port     eg. 30303
    private static final Pattern PATTERN_P2P = Pattern.compile(REGEX_PROTOCOL + REGEX_NODE_ID + "@" + REGEX_IPV4 + ":" + REGEX_PORT);
    private static final int SIZE_BYTES_IPV4 = 8;

    private boolean fromBootList;

    private byte[] id; // 36 bytes

    private int idHash;

    private int fullHash = -1;

    /**
     * for display only
     */
    private String idShort;

    private byte[] ip;

    private String ipStr;

    private int port;

    private int portConnected;

    private long timestamp;

    private long bestBlockNumber;

    private byte[] bestBlockHash;

    private BigInteger totalDifficulty = BigInteger.ZERO;

    private String binaryVersion = "";

    private SocketChannel channel;

    /**
     * for log display indicates current node connection is
     * constructed by inbound connection or outbound connection
     */
    private String connection = "";

    PeerMetric peerMetric = new PeerMetric();

    /**
     * constructor for initial stage of persisted peer info from file system
     */
    private Node(boolean fromBootList, String _ipStr) {
        this.fromBootList = fromBootList;
        this.idHash = 0;
        this.ip = ipStrToBytes(_ipStr);
        this.ipStr = _ipStr;
        this.port = -1;
        this.portConnected = -1;
        this.timestamp = System.currentTimeMillis();
        this.bestBlockNumber = 0L;
    }

    /**
     * constructor for initial stage of connections from network
     */
    Node(String _ipStr, int port, int portConnected) {
        this.fromBootList = false;
        this.idHash = 0;
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
        this.ip = _ip;
        this.ipStr = ipBytesToStr(_ip);
        this.port = _port;
        this.portConnected = -1;
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
        if(_ip == null || _ip.length != SIZE_BYTES_IPV4)
            return "";
        else {
            short[] shorts = new short[_ip.length/2];
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
     * @param _port int
     */
    void setPort(final int _port) {
        this.port = _port;
    }

    void setPortConnected(final int _port) {
        this.portConnected = _port;
    }

    void setBinaryVersion(String _revision) { this.binaryVersion = _revision; }

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
     * @param _connection String
     */
    void setConnection(String _connection){
        this.connection = _connection;
    }

    /**
     * @return boolean
     */
    boolean getIfFromBootList() {
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

    public int getConnectedPort() {
        return portConnected;
    }

    /**
     * @return long
     */
    long getTimestamp() {
        return this.timestamp;
    }

    String getBinaryVersion() { return this.binaryVersion; }

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

    /**
     * @return String
     */
    public String getConnection() {
        return this.connection;
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
    public BigInteger getTotalDifficulty() {
        return this.totalDifficulty;
    }

    @Override
    public void updateStatus(long _bestBlockNumber, final byte[] _bestBlockHash, BigInteger _totalDifficulty) {
        this.bestBlockNumber = _bestBlockNumber;
        this.bestBlockHash = _bestBlockHash;
        this.totalDifficulty = _totalDifficulty == null ? BigInteger.ZERO : _totalDifficulty;
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

    String toXML(){
        final XMLOutputFactory output = XMLOutputFactory.newInstance();
        XMLStreamWriter sw;
        String xml;
        try {
            Writer strWriter = new StringWriter();
            sw = output.createXMLStreamWriter(strWriter);

            sw.writeCharacters("\r\n\t");
            sw.writeStartElement("node");
            sw.writeStartElement("ip");
            sw.writeCharacters(getIpStr());
            sw.writeEndElement();

            sw.writeStartElement("port");
            sw.writeCharacters(String.valueOf(getPort()));
            sw.writeEndElement();

            sw.writeStartElement("id");
            sw.writeCharacters(new String(getId()));
            sw.writeEndElement();

            sw.writeStartElement("failedConn");
            sw.writeCharacters(String.valueOf(peerMetric.metricFailedConn));
            sw.writeEndElement();
            sw.writeEndElement();

            xml = strWriter.toString();
            strWriter.flush();
            strWriter.close();
            sw.flush();
            sw.close();
            return xml;
        } catch (IOException | XMLStreamException e) {
            return "";
        }
    }

    public static Node fromXML(final XMLStreamReader sr) throws XMLStreamException {
        String id = null;
        String ip = null;
        int port = 0;
        int failedConn = 0;

        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "ip":
                            ip = readValue(sr);
                            break;
                        case "port":
                            port = Integer.parseInt(readValue(sr));
                            break;
                        case "id":
                            id =  readValue(sr);
                            break;
                        case "failedconn":
                            failedConn = Integer.parseInt(readValue(sr));
                            break;
                        default:
                            break;
                    }
                    break;
                case XMLStreamReader.END_ELEMENT:
                    Node node = new Node(false, ip);
                    if(id == null)
                        return null;
                    node.setId(id.getBytes());
                    node.setPort(port);
                    node.peerMetric.metricFailedConn = failedConn;
                    return node;
            }
        }
        return null;
    }

    private static String readValue(final XMLStreamReader sr) throws XMLStreamException {
        StringBuilder str = new StringBuilder();
        readLoop:
        while (sr.hasNext()) {
            switch (sr.next()) {
                case XMLStreamReader.CHARACTERS:
                    str.append(sr.getText());
                    break;
                case XMLStreamReader.END_ELEMENT:
                    break readLoop;
            }
        }
        return str.toString();
    }
}
