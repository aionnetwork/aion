package org.aion.api.server.types;

import org.aion.interfaces.block.Block;
import org.aion.interfaces.tx.Transaction;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.util.string.StringUtils;

public class TxRecptLg {

    public String address;

    public String blockHash;

    public String blockNumber;

    public String data;

    public String logIndex;

    public String[] topics;

    public String transactionHash;

    public String transactionIndex;

    // true when the log was removed, due to a chain reorganization. false if its a valid log.
    public boolean removed;

    public <TX extends Transaction> TxRecptLg(
            Log logInfo, Block b, Integer txIndex, TX tx, int logIdx, boolean isMainchain) {
        this.logIndex = StringUtils.toJsonHex(logIdx);
        this.blockNumber = b == null ? null : StringUtils.toJsonHex(b.getNumber());
        this.blockHash = b == null ? null : StringUtils.toJsonHex(b.getHash());
        this.transactionIndex =
                (b == null || txIndex == null) ? null : StringUtils.toJsonHex(txIndex);
        this.transactionHash = StringUtils.toJsonHex(tx.getTransactionHash());
        AionAddress aionAddress = new AionAddress(logInfo.copyOfAddress());
        this.address = StringUtils.toJsonHex(aionAddress.toString());
        this.data = StringUtils.toJsonHex(logInfo.copyOfData());
        this.removed = !isMainchain;

        this.topics = new String[logInfo.copyOfTopics().size()];
        for (int i = 0, m = this.topics.length; i < m; i++) {
            this.topics[i] = StringUtils.toJsonHex(logInfo.copyOfTopics().get(i));
        }
    }
}
