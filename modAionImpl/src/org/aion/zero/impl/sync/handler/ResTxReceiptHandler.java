package org.aion.zero.impl.sync.handler;

import org.aion.base.util.ByteUtil;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.db.TransactionStore;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.Ver;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.ResTxReceipts;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.slf4j.Logger;

import java.util.List;

/** Handle transaction receipts response */
public class ResTxReceiptHandler extends Handler {
    protected final TransactionStore<
            AionTransaction, AionTxReceipt, AionTxInfo> txStore;
    private final AionBlockStore blockStore;

    private static final Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

    /**
     * Constructor
     */
    public ResTxReceiptHandler(
            TransactionStore<AionTransaction, AionTxReceipt, AionTxInfo> txStore,
            AionBlockStore blockStore) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_TX_RECEIPT_HEADERS);
        this.txStore = txStore;
        this.blockStore = blockStore;
    }

    @Override
    public void receive(int id, String displayId, byte[] msg) {
        LOGGER.info("<<< ResTxReceiptHandler >>> receive start");
        final ResTxReceipts resTxReceipts;
        try {
            resTxReceipts = new ResTxReceipts(msg);
        } catch (NullPointerException | IllegalArgumentException ex) {
            LOGGER.error(
                    "<res-tx-receipts decode-error, unable to Msg body from {}, len: {}, reason: {}>",
                    displayId, msg.length, ex.getMessage());
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("res-tx-receipts dump: {}", ByteUtil.toHexString(msg));
            }
            return;
        }

        for(AionTxInfo atr : resTxReceipts.getTxInfo()) {
            List<AionTransaction> txs = blockStore.getBlockByHash(atr.getBlockHash()).getTransactionsList();
            AionTransaction tx = txs.get(atr.getIndex());
            atr.setTransaction(tx);
        }
        persist(resTxReceipts.getTxInfo());
    }

    protected void persist(List<AionTxInfo> txInfo) {
        for(AionTxInfo txi : txInfo) {
            txStore.putToBatch(txi);
        }
        txStore.flushBatch();
    }
}
