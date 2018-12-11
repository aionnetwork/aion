package org.aion.p2p;

import java.math.BigInteger;
import java.nio.channels.SocketChannel;

/** @author chris */
public interface INode {

    /** @return byte[] */
    byte[] getId();

    /** @return int */
    int getIdHash();

    /** @return String */
    String getIdShort();

    /** @return byte[] */
    byte[] getIp();

    /** @return String */
    String getIpStr();

    /** @return int */
    int getPort();

    /** @return long */
    long getBestBlockNumber();

    /** @return BigInteger */
    BigInteger getTotalDifficulty();

    int getPeerId();

    long getTimestamp();

    /**
     * @param _bestBlockNumber long
     * @param _bestBlockHash byte[]
     * @param _totalDifficulty long
     */
    void updateStatus(
            long _bestBlockNumber, final byte[] _bestBlockHash, BigInteger _totalDifficulty);

    String getBinaryVersion();

    boolean getIfFromBootList();

    byte[] getBestBlockHash();

    String getConnection();

    SocketChannel getChannel();

    void setFromBootList(boolean _ifBoot);

    void setConnection(String _connection);

    IPeerMetric getPeerMetric();

    void refreshTimestamp();

    void setChannel(SocketChannel _channel);

    void setId(byte[] _id);

    void setPort(int _port);

    void setBinaryVersion(String _revision);

    String toString();
}
