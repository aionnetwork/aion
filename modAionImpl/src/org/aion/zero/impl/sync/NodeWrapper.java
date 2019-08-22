package org.aion.zero.impl.sync;

import java.math.BigInteger;
import org.aion.p2p.INode;

/** Facilitates passing peer information to the API without relying on the p2p module interfaces. */
public class NodeWrapper {
    INode peer;

    public NodeWrapper(INode peer) {
        this.peer = peer;
    }

    public String getIdShort() {
        return peer.getIdShort();
    }

    public byte[] getId() {
        return peer.getId();
    }

    public int getIdHash() {
        return peer.getIdHash();
    }

    public String getBinaryVersion() {
        return peer.getBinaryVersion();
    }

    public long getBestBlockNumber() {
        return peer.getBestBlockNumber();
    }

    public BigInteger getTotalDifficulty() {
        return peer.getTotalDifficulty();
    }

    public byte[] getIp() {
        return peer.getIp();
    }

    public String getIpStr() {
        return peer.getIpStr();
    }

    public int getPort() {
        return peer.getPort();
    }

    public long getTimestamp() {
        return peer.getTimestamp();
    }
}
