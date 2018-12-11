package org.aion.zero.impl.sync;

import java.math.BigInteger;

/** @author chris used by sync mgr display logging */
final class NetworkStatus {

    private String targetDisplayId;

    private BigInteger targetTotalDiff;

    private long targetBestBlockNumber;

    private String targetBestBlockHash;

    NetworkStatus() {
        this.targetDisplayId = "";
        this.targetTotalDiff = BigInteger.ZERO;
        this.targetBestBlockNumber = 0;
        this.targetBestBlockHash = "";
    }

    synchronized void update(
            String _targetDisplayId,
            BigInteger _targetTotalDiff,
            long _targetBestBlockNumber,
            String _targetBestBlockHash) {
        this.targetDisplayId = _targetDisplayId;
        this.targetTotalDiff = _targetTotalDiff;
        this.targetBestBlockNumber = _targetBestBlockNumber;
        this.targetBestBlockHash = _targetBestBlockHash;
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
}
