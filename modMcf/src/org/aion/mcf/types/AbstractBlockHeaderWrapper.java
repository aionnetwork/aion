package org.aion.mcf.types;

import java.util.Arrays;
import org.aion.base.type.IBlockHeader;
import org.aion.base.util.Hex;
import org.aion.rlp.RLP;

/** AbstractBlockHeaderWrapper */
public abstract class AbstractBlockHeaderWrapper<BH extends IBlockHeader> {

    protected BH header;

    protected byte[] nodeId;

    public AbstractBlockHeaderWrapper() {}

    public AbstractBlockHeaderWrapper(BH header, byte[] nodeId) {
        this.header = header;
        this.nodeId = nodeId;
    }

    public AbstractBlockHeaderWrapper(byte[] bytes) {
        parse(bytes);
    }

    public byte[] getBytes() {
        byte[] headerBytes = header.getEncoded();
        byte[] nodeIdBytes = RLP.encodeElement(nodeId);
        return RLP.encodeList(headerBytes, nodeIdBytes);
    }

    protected abstract void parse(byte[] bytes);

    public byte[] getNodeId() {
        return nodeId;
    }

    public byte[] getHash() {
        return header.getHash();
    }

    public long getNumber() {
        return header.getNumber();
    }

    public BH getHeader() {
        return header;
    }

    public String getHexStrShort() {
        return Hex.toHexString(header.getHash()).substring(0, 6);
    }

    public boolean sentBy(byte[] nodeId) {
        return Arrays.equals(this.nodeId, nodeId);
    }

    @Override
    public String toString() {
        return "BlockHeaderWrapper {"
                + "header="
                + header
                + ", nodeId="
                + Hex.toHexString(nodeId)
                + '}';
    }
}
