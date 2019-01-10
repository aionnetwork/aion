package org.aion.zero.impl.sync.msg;

import java.nio.ByteBuffer;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;

/** @author Chris */
public final class ResStatus extends Msg {

    private static final int minLen = 8 + 1 + 1 + 32 + 32;

    private final long bestBlockNumber; // 8

    private final byte totalDifficultyLen; // 1

    private final byte[] totalDifficulty; // >= 1

    private final byte[] bestHash; // 32

    private final byte[] genesisHash; // 32

    /**
     * @param bestBlockNumber long
     * @param _totalDifficulty byte[]
     * @param _bestHash byte[]
     * @param _genesisHash byte[]
     */
    public ResStatus(
            long bestBlockNumber,
            final byte[] _totalDifficulty,
            final byte[] _bestHash,
            final byte[] _genesisHash) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_STATUS);
        this.bestBlockNumber = bestBlockNumber;
        this.totalDifficultyLen =
                _totalDifficulty.length > Byte.MAX_VALUE ? 1 : (byte) _totalDifficulty.length;
        this.totalDifficulty = _totalDifficulty;
        this.bestHash = _bestHash;
        this.genesisHash = _genesisHash;
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
        return new ResStatus(_bestBlockNumber, _totalDifficulty, _bestHash, _genesisHash);
    }

    @Override
    public byte[] encode() {
        int _len = 8 + 1 + totalDifficultyLen + 32 + 32;
        ByteBuffer bb = ByteBuffer.allocate(_len);
        bb.putLong(this.bestBlockNumber);
        bb.put(this.totalDifficultyLen);
        bb.put(this.totalDifficulty);
        bb.put(this.bestHash);
        bb.put(this.genesisHash);
        return bb.array();
    }
}
