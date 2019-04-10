package org.aion.mcf.types;

import static org.aion.util.time.TimeUtils.secondsToMillis;

import java.math.BigInteger;
import java.util.Arrays;
import org.aion.interfaces.block.Block;
import org.aion.rlp.RLP;

/** AbstractBlockWrapper */
public abstract class AbstractBlockWrapper<BLK extends Block<?, ?>> {

    protected static final long SOLID_BLOCK_DURATION_THRESHOLD = secondsToMillis(60);

    protected BLK block;
    protected long importFailedAt = 0;
    protected long receivedAt = 0;
    protected boolean newBlock;
    protected byte[] nodeId;

    public AbstractBlockWrapper(BLK block, byte[] nodeId) {
        this(block, false, nodeId);
    }

    public AbstractBlockWrapper(BLK block, boolean newBlock, byte[] nodeId) {
        this.block = block;
        this.newBlock = newBlock;
        this.nodeId = nodeId;
    }

    public AbstractBlockWrapper(byte[] bytes) {
        parse(bytes);
    }

    protected abstract void parse(byte[] bytes);

    public BLK getBlock() {
        return block;
    }

    public boolean isNewBlock() {
        return newBlock;
    }

    public boolean isSolidBlock() {
        return !newBlock || timeSinceReceiving() > SOLID_BLOCK_DURATION_THRESHOLD;
    }

    public long getImportFailedAt() {
        return importFailedAt;
    }

    public void setImportFailedAt(long importFailedAt) {
        this.importFailedAt = importFailedAt;
    }

    public byte[] getHash() {
        return block.getHash();
    }

    public long getNumber() {
        return block.getNumber();
    }

    public byte[] getEncoded() {
        return block.getEncoded();
    }

    public String getShortHash() {
        return block.getShortHash();
    }

    public byte[] getParentHash() {
        return block.getParentHash();
    }

    public long getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(long receivedAt) {
        this.receivedAt = receivedAt;
    }

    public byte[] getNodeId() {
        return nodeId;
    }

    public boolean sentBy(byte[] nodeId) {
        return Arrays.equals(this.nodeId, nodeId);
    }

    public boolean isEqual(AbstractBlockWrapper wrapper) {
        return wrapper != null && block.isEqual(wrapper.getBlock());
    }

    public void importFailed() {
        if (importFailedAt == 0) {
            importFailedAt = System.currentTimeMillis();
        }
    }

    public void resetImportFail() {
        importFailedAt = 0;
    }

    public long timeSinceFail() {
        if (importFailedAt == 0) {
            return 0;
        } else {
            return System.currentTimeMillis() - importFailedAt;
        }
    }

    public long timeSinceReceiving() {
        return System.currentTimeMillis() - receivedAt;
    }

    public byte[] getBytes() {
        byte[] blockBytes = block.getEncoded();
        byte[] importFailedBytes = RLP.encodeBigInteger(BigInteger.valueOf(importFailedAt));
        byte[] receivedAtBytes = RLP.encodeBigInteger(BigInteger.valueOf(receivedAt));
        byte[] newBlockBytes = RLP.encodeByte((byte) (newBlock ? 1 : 0));
        byte[] nodeIdBytes = RLP.encodeElement(nodeId);
        return RLP.encodeList(
                blockBytes, importFailedBytes, receivedAtBytes, newBlockBytes, nodeIdBytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof AbstractBlockWrapper)) {
            return false;
        }

        return this.isEqual((AbstractBlockWrapper<?>) o);
    }
}
