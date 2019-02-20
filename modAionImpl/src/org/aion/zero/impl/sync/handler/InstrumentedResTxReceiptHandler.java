package org.aion.zero.impl.sync.handler;

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.util.ByteUtil;
import org.aion.db.impl.DatabaseFactory;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.db.TransactionStore;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.db.AionTransactionStoreSerializer;
import org.aion.zero.impl.sync.ReceiptsRetrievalVerifier;
import org.aion.zero.impl.types.AionTxInfo;
import org.slf4j.Logger;

import java.util.List;
import java.util.Properties;

import static org.aion.mcf.db.DatabaseUtils.connectAndOpen;

/**
 * Like {@link ResTxReceiptHandler} but instead of writing to actual Transaction Store,
 * write it to an alternate Transaction Store used only for verification/testing purposes,
 * and then perform verification on the result by {@link ReceiptsRetrievalVerifier}.
 *
 * Unlike the normal ResTxReceiptHandler,
 */
public class InstrumentedResTxReceiptHandler extends ResTxReceiptHandler {
    private final ReceiptsRetrievalVerifier rrv;

    private static String ALTERNATE_DB_NAME = "alt_transaction";
    private static final Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

    public void closeDb() {
        super.txStore.close();
    }

    /**
     * Constructor
     */
    public InstrumentedResTxReceiptHandler(
            AionBlockStore blockStore,
            ReceiptsRetrievalVerifier rrv,
            String altDbPath) {
        super(createAlternateTxStore(altDbPath), blockStore);
        this.rrv = rrv;
    }

    private static TransactionStore createAlternateTxStore(String altDbPath) {
        Properties sharedProps = new Properties();
        sharedProps.setProperty(DatabaseFactory.Props.ENABLE_LOCKING, "false");
        sharedProps.setProperty(DatabaseFactory.Props.DB_PATH, altDbPath);
        sharedProps.setProperty(DatabaseFactory.Props.DB_NAME, ALTERNATE_DB_NAME);
        sharedProps.setProperty(DatabaseFactory.Props.DB_TYPE, "leveldb");

        IByteArrayKeyValueDatabase transactionDatabase;
        transactionDatabase = connectAndOpen(sharedProps, LOGGER);
        if (transactionDatabase == null || transactionDatabase.isClosed()) {
            throw new IllegalStateException("Failed to bring up alternate transactions DB");
        }

        return new TransactionStore<>(
                transactionDatabase, AionTransactionStoreSerializer.serializer);
    }

    @Override
    protected void persist(List<AionTxInfo> txInfo) {
        super.persist(txInfo);
        for(AionTxInfo txi : txInfo) {
            rrv.receivedTxHash(txi.getReceipt().getTransaction().getTransactionHash());
            rrv.validateAgainstBlockchain(txi);
            rrv.validateDatabases(txi, txStore);
        }

        LOGGER.info(String.format(
                "InstrumentedResTxReceiptHandler persisted batch of AionTxInfo [block0=%s, blockN=%s].  Outstanding hashes after:",
                ByteUtil.toHexString(txInfo.get(0).getBlockHash()),
                ByteUtil.toHexString(txInfo.get(txInfo.size()-1).getBlockHash())
                ));
        rrv.displayOutstandingRequests();
    }
}
