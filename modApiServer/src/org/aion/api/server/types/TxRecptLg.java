package org.aion.api.server.types;

import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.base.util.TypeConverter;
import org.aion.mcf.vm.types.Log;
import org.aion.vm.api.interfaces.IExecutionLog;

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

    public <TX extends ITransaction> TxRecptLg(
            IExecutionLog logInfo, IBlock b, Integer txIndex, TX tx, int logIdx, boolean isMainchain) {
        this.logIndex = TypeConverter.toJsonHex(logIdx);
        this.blockNumber = b == null ? null : TypeConverter.toJsonHex(b.getNumber());
        this.blockHash = b == null ? null : TypeConverter.toJsonHex(b.getHash());
        this.transactionIndex =
                (b == null || txIndex == null) ? null : TypeConverter.toJsonHex(txIndex);
        this.transactionHash = TypeConverter.toJsonHex(tx.getTransactionHash());
        this.address = TypeConverter.toJsonHex(logInfo.getSourceAddress().toString());
        this.data = TypeConverter.toJsonHex(logInfo.getData());
        this.removed = !isMainchain;

        this.topics = new String[logInfo.getTopics().size()];
        for (int i = 0, m = this.topics.length; i < m; i++) {
            this.topics[i] = TypeConverter.toJsonHex(logInfo.getTopics().get(i));
        }
    }
}
