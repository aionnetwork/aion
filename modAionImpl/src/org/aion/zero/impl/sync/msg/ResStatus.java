package org.aion.zero.impl.sync.msg;

import java.nio.ByteBuffer;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;

/** @author Chris */
public final class ResStatus extends Msg {

    private static final int minLen = 8 + 1 + 1 + 32 + 32;

    private static final int minLenNew = minLen + 1 + 2 + 1 + 1 + 4;

    private final long bestBlockNumber; // 8

    private final byte totalDifficultyLen; // 1

    private final byte[] totalDifficulty; // >= 1

    private final byte[] bestHash; // 32

    private final byte[] genesisHash; // 32

    private final byte apiVersion; // 1

    private final short peerCount; // 2

    private final byte pendingTxCountLen; // 1

    private final byte[] pendingTxCount; // 1+n

    private final int latency; // 4

    private int msgLen;

    /**
     * @param bestBlockNumber long
     * @param _totalDifficulty byte[]
     * @param _bestHash byte[]
     * @param _genesisHash byte[]
     * @param _apiVersion byte
     * @param _peerCount short
     * @param _pendingTxCount byte[]
     * @param _latency int
     */
    public ResStatus(
            long bestBlockNumber,
            final byte[] _totalDifficulty,
            final byte[] _bestHash,
            final byte[] _genesisHash,
            final byte _apiVersion,
            final short _peerCount,
            final byte[] _pendingTxCount,
            final int _latency) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_STATUS);
        this.bestBlockNumber = bestBlockNumber;
        this.totalDifficultyLen =
                _totalDifficulty.length > Byte.MAX_VALUE ? 1 : (byte) _totalDifficulty.length;
        this.totalDifficulty = _totalDifficulty;
        this.bestHash = _bestHash;
        this.genesisHash = _genesisHash;
        this.apiVersion = _apiVersion;
        this.peerCount = _peerCount;
        this.pendingTxCountLen =
                _pendingTxCount.length > Byte.MAX_VALUE ? 1 : (byte) _pendingTxCount.length;
        this.pendingTxCount = _pendingTxCount;
        this.latency = _latency;
        this.msgLen = 8 + 1 + totalDifficultyLen + 32 + 32 + 1 + 2 + 1 + pendingTxCountLen + 4;
    }

    /** @return long */
    public long getBestBlockNumber() {
        return this.bestBlockNumber;
    }

    /** @return byte[] */
    public byte[] getBestHash() {
        return this.bestHash;
    }

    /** @return byte[] */
    public byte[] getGenesisHash() {
        return this.genesisHash;
    }

    /** @return byte[] */
    public byte[] getTotalDifficulty() {
        return this.totalDifficulty;
    }

    /** @return byte */
    public byte getApiVersion() {
        return this.apiVersion;
    }

    /** @return short */
    public short getPeerCount() {
        return this.peerCount;
    }

    /** @return byte[] */
    public byte[] getPendingTxCount() {
        return this.pendingTxCount;
    }

    /** @return short */
    public int getLatency() {
        return this.latency;
    }

    public static ResStatus decode(final byte[] _bytes) {
        if (_bytes == null || _bytes.length < minLen) return null;
        ByteBuffer bb = ByteBuffer.wrap(_bytes);
        bb.clear();
        long _bestBlockNumber = bb.getLong();
        int _totalDifficultyLen = bb.get();
        byte[] _totalDifficulty = new byte[_totalDifficultyLen];
        byte[] _bestHash = new byte[32];
        byte[] _genesisHash = new byte[32];
        bb.get(_totalDifficulty);
        bb.get(_bestHash);
        bb.get(_genesisHash);

        int _len = 8 + 1 + _totalDifficultyLen + 32 + 32;

        byte _apiVersion = 0;
        short _peerCount = 0;
        byte[] _pendingTxCount = new byte[0];
        int _latency = 0;
        if (_bytes.length > Math.max(_len, minLenNew)) {
            _apiVersion = bb.get();
            _peerCount = bb.getShort();
            int _pendingTxCountLen = bb.get();
            _pendingTxCount = new byte[_pendingTxCountLen];
            bb.get(_pendingTxCount);
            _latency = bb.getInt();
        }
        return new ResStatus(
                _bestBlockNumber,
                _totalDifficulty,
                _bestHash,
                _genesisHash,
                _apiVersion,
                _peerCount,
                _pendingTxCount,
                _latency);
    }

    @Override
    public byte[] encode() {
        ByteBuffer bb = ByteBuffer.allocate(msgLen);
        bb.putLong(this.bestBlockNumber);
        bb.put(this.totalDifficultyLen);
        bb.put(this.totalDifficulty);
        bb.put(this.bestHash);
        bb.put(this.genesisHash);
        bb.put(this.apiVersion);
        bb.putShort(this.peerCount);
        bb.put(this.pendingTxCountLen);
        bb.put(this.pendingTxCount);
        bb.putInt(this.latency);
        return bb.array();
    }
}
