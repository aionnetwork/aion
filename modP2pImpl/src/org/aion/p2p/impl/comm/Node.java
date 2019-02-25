package org.aion.p2p.impl.comm;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.aion.p2p.INode;
import org.aion.p2p.IPeerMetric;
import org.aion.util.bytes.ByteUtil;

/**
 * @author Chris p2p://{node-id}@{ip}:{port} node-id could be any non-empty string update to 36
 *     bytes
 */
public final class Node implements INode {

    private static final String REGEX_PROTOCOL = "^p2p://"; // Protocol eg. p2p://
    private static final String REGEX_NODE_ID =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"; // Node-Id
    // eg.
    // 3e2cab6a-09dd-4771-b28d-6aa674009796
    private static final String REGEX_IPV4 =
            "(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])"; // Ip
    // eg.
    // 127.0.0.1
    private static final String REGEX_PORT = "[0-9]{1,5}$"; // Port eg. 30303
    private static final Pattern PATTERN_P2P =
            Pattern.compile(REGEX_PROTOCOL + REGEX_NODE_ID + "@" + REGEX_IPV4 + ":" + REGEX_PORT);
    private static final int SIZE_BYTES_IPV4 = 8;
    private IPeerMetric peerMetric = new PeerMetric();
    private boolean fromBootList;
    private byte[] id; // 36 bytes
    private int idHash;
    private int peerhash;
    /** for display only */
    private String idShort;

    private byte[] ip;
    private String ipStr;
    private int port;
    private volatile long timestamp;
    private long bestBlockNumber;
    private byte[] bestBlockHash;
    private BigInteger totalDifficulty = BigInteger.ZERO;
    private byte apiVersion;
    private short peerCount;
    private int pendingTxCount;
    private int latency;

    private String binaryVersion = "";
    private SocketChannel channel;
    /**
     * for log display indicates current node connection is constructed by inbound connection or
     * outbound connection
     */
    private String connection = "";

    /** constructor for initial stage of connections from network */
    Node(String _ipStr, int port) {
        if (port > 65535 || port < 1) {
            throw new IllegalArgumentException();
        }

        this.fromBootList = false;
        this.idHash = 0;
        this.ip = ipStrToBytes(_ipStr);
        this.ipStr = _ipStr;
        this.port = port;
        this.timestamp = System.currentTimeMillis();
        this.bestBlockNumber = 0L;
        this.peerhash = 0;
    }

    /** constructor for initial stage of boot nodes from config */
    public Node(boolean fromBootList, final byte[] _id, final byte[] _ip, final int _port) {
        this.fromBootList = fromBootList;
        this.id = _id;
        if (_id != null && _id.length == 36) {
            this.idHash = Arrays.hashCode(_id);
            this.idShort = new String(Arrays.copyOfRange(_id, 0, 6));

            ByteBuffer buffer =
                    ByteBuffer.allocate(_id.length + _ip.length + Integer.BYTES)
                            .put(_id)
                            .put(_ip)
                            .putInt(_port);

            this.peerhash = Arrays.hashCode(buffer.array());
        }
        this.ip = _ip;
        this.ipStr = ipBytesToStr(_ip);
        this.port = ((_port > 65535 || _port < 1) ? 0 : _port);
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
    public static String ipBytesToStr(final byte[] _ip) {
        if (_ip == null || _ip.length != SIZE_BYTES_IPV4) {
            return "";
        } else {
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
     * @return Node
     */
    public static Node parseP2p(String _p2p) {
        if (!PATTERN_P2P.matcher(_p2p).matches()) {
            return null;
        }

        String[] arrs = _p2p.split("@");
        byte[] _tempBytes = arrs[0].getBytes();

        byte[] _id = Arrays.copyOfRange(_tempBytes, 6, 42);
        String[] subArrs = arrs[1].split(":");

        byte[] _ip = ipStrToBytes(subArrs[0]);
        int _port = Integer.parseInt(subArrs[1]);

        return new Node(true, _id, _ip, _port);
    }

    @Override
    public IPeerMetric getPeerMetric() {
        return this.peerMetric;
    }

    @Override
    public void setFromBootList(boolean _ifBoot) {
        this.fromBootList = _ifBoot;
    }

    /** this method used to keep current node stage on either pending list or active list */
    @Override
    public void refreshTimestamp() {
        this.timestamp = System.currentTimeMillis();
    }

    /** @return boolean */
    @Override
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

    /** @param _port int */
    @Override
    public void setPort(final int _port) {
        this.port = _port;
    }

    @Override
    public int getPeerId() {
        return peerhash;
    }

    /** @return long */
    public long getTimestamp() {
        return this.timestamp;
    }

    public String getBinaryVersion() {
        return this.binaryVersion;
    }

    @Override
    public void setBinaryVersion(String _revision) {
        this.binaryVersion = _revision;
    }

    /** @return SocketChannel */
    @Override
    public SocketChannel getChannel() {
        return this.channel;
    }

    /** @param _channel SocketChannel */
    @Override
    public void setChannel(final SocketChannel _channel) {
        this.channel = _channel;
    }

    @Override
    public byte[] getId() {
        return this.id;
    }

    /** @param _id byte[] */
    @Override
    public void setId(final byte[] _id) {
        this.id = _id;
        if (_id != null && _id.length == 36) {
            this.idHash = Arrays.hashCode(_id);
            this.idShort = new String(Arrays.copyOfRange(_id, 0, 6));
        }
    }

    @Override
    public int getIdHash() {
        return this.idHash;
    }

    /** @return String */
    @Override
    public String getConnection() {
        return this.connection;
    }

    /** @param _connection String */
    @Override
    public void setConnection(String _connection) {
        this.connection = _connection;
    }

    @Override
    public String getIdShort() {
        return this.idShort == null ? "" : this.idShort;
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
    public byte getApiVersion() {
        return this.apiVersion;
    }

    @Override
    public short getPeerCount() {
        return this.peerCount;
    }

    @Override
    public int getPendingTxCount() {
        return this.pendingTxCount;
    }

    @Override
    public int getLatency() {
        return this.latency;
    }

    @Override
    public void updateStatus(
            long _bestBlockNumber,
            final byte[] _bestBlockHash,
            BigInteger _totalDifficulty,
            byte _apiVersion,
            short _peerCount,
            int _pendingTxCount,
            int _latency) {
        this.bestBlockNumber = _bestBlockNumber;
        this.bestBlockHash = _bestBlockHash;
        this.totalDifficulty = _totalDifficulty == null ? BigInteger.ZERO : _totalDifficulty;
        this.apiVersion = _apiVersion;
        this.peerCount = _peerCount;
        this.pendingTxCount = _pendingTxCount;
        this.latency = _latency;
    }

    @Override
    public String toString() {
        return "bootList:"
                + fromBootList
                + "\n"
                + "idHash:"
                + idHash
                + "\n"
                + "id:"
                + (id == null ? "null" : new String(id))
                + "\n"
                + "idShort:"
                + idShort
                + "\n"
                + "peerhash:"
                + peerhash
                + "\n"
                + "ipStr:"
                + ipStr
                + "\n"
                + "port:"
                + port
                + "\n"
                + "timestamp:"
                + timestamp
                + "\n"
                + "bestBlockNumber:"
                + bestBlockNumber
                + "\n"
                + "totalDifficulty:"
                + totalDifficulty.toString()
                + "\n"
                + "bestBlockHash:"
                + (bestBlockHash == null ? "null" : ByteUtil.toHexString(bestBlockHash))
                + "\n"
                + "binaryVersion:"
                + binaryVersion
                + "\n\n";
    }
}
