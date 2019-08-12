package org.aion.zero.impl.types;

import java.math.BigInteger;
import org.aion.base.AionTransaction;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPItem;
import org.aion.rlp.RLPList;
import org.aion.util.conversions.Hex;
import org.aion.zero.types.AionTxReceipt;

public class AionTxInfo {
    private AionTxReceipt receipt;
    private byte[] blockHash;
    private int index;

    public AionTxInfo(AionTxReceipt receipt, byte[] blockHash, int index) {
        this.receipt = receipt;
        this.blockHash = blockHash;
        this.index = index;
    }

    /** Creates a pending tx info */
    public AionTxInfo(AionTxReceipt receipt) {
        this.receipt = receipt;
    }

    public AionTxInfo(byte[] rlp) {
        RLPList params = RLP.decode2(rlp);
        RLPList txInfo = (RLPList) params.get(0);
        RLPList receiptRLP = (RLPList) txInfo.get(0);
        RLPItem blockHashRLP = (RLPItem) txInfo.get(1);
        RLPItem indexRLP = (RLPItem) txInfo.get(2);

        receipt = new AionTxReceipt(receiptRLP.getRLPData());
        blockHash = blockHashRLP.getRLPData();
        if (indexRLP.getRLPData() == null) {
            index = 0;
        } else {
            index = new BigInteger(1, indexRLP.getRLPData()).intValue();
        }
    }

    public void setTransaction(AionTransaction tx) {
        receipt.setTransaction(tx);
    }

    /* [receipt, blockHash, index] */
    public byte[] getEncoded() {

        byte[] receiptRLP = this.receipt.toBytes();
        byte[] blockHashRLP = RLP.encodeElement(blockHash);
        byte[] indexRLP = RLP.encodeInt(index);

        byte[] rlpEncoded = RLP.encodeList(receiptRLP, blockHashRLP, indexRLP);

        return rlpEncoded;
    }

    public AionTxReceipt getReceipt() {
        return receipt;
    }

    public byte[] getBlockHash() {
        return blockHash;
    }

    public int getIndex() {
        return index;
    }

    public boolean isPending() {
        return blockHash == null;
    }

    @Override
    public String toString() {
        StringBuilder toStringBuff = new StringBuilder();
        toStringBuff.setLength(0);
        toStringBuff.append("  ").append("index=").append(index).append("\n");
        toStringBuff.append("  ").append(receipt.toString()).append("\n");
        toStringBuff.append("  ").append(Hex.toHexString(this.getEncoded()));

        return toStringBuff.toString();
    }
}
