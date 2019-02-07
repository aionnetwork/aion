package org.aion.zero.impl.sync;

import java.math.BigInteger;

/** @author chris used by sync mgr display logging */
final class NetworkStatus {

    private String targetDisplayId;

    private BigInteger targetTotalDiff;

    private long targetBestBlockNumber;

    private String targetBestBlockHash;

    private int targetApiVersion;

    private short targetPeerCount;

    private int targetPendingTxCount;

    private int targetLatency;

    NetworkStatus() {
        this.targetDisplayId = "";
        this.targetTotalDiff = BigInteger.ZERO;
        this.targetBestBlockNumber = 0;
        this.targetBestBlockHash = "";
        this.targetApiVersion = -1;
        this.targetPeerCount = 0;
        this.targetPeerCount = 0;
        this.targetLatency = 0;
    }

    synchronized void update(
            String _targetDisplayId,
            BigInteger _targetTotalDiff,
            long _targetBestBlockNumber,
            String _targetBestBlockHash,
            int _apiVersion,
            short _peerCount,
            int _pendingTxCount,
            int _latency) {
        this.targetDisplayId = _targetDisplayId;
        this.targetTotalDiff = _targetTotalDiff;
        this.targetBestBlockNumber = _targetBestBlockNumber;
        this.targetBestBlockHash = _targetBestBlockHash;
        this.targetApiVersion = _apiVersion;
        this.targetPeerCount = _peerCount;
        this.targetPendingTxCount = _pendingTxCount;
        this.targetLatency = _latency;
    }

    String getTargetDisplayId() {
        return this.targetDisplayId;
    }

    BigInteger getTargetTotalDiff() {
        return this.targetTotalDiff;
    }

    long getTargetBestBlockNumber() {
        return this.targetBestBlockNumber;
    }

    String getTargetBestBlockHash() {
        return this.targetBestBlockHash;
    }

    int getTargetApiVersion() {
        return this.targetApiVersion;
    }

    short getTargetPeerCount() {
        return this.targetPeerCount;
    }

    int getTargetPendingTxCount() {
        return this.targetPendingTxCount;
    }

    int getTargetLatency() {
        return this.targetLatency;
    }
}
