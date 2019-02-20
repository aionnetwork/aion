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
import org.aion.zero.impl.sync.msg.ResponseReceipts;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.slf4j.Logger;

import java.util.List;

/** Handle transaction receipts response */
public class ResponseReceiptsHandler extends Handler {

    protected final TransactionStore<AionTransaction, AionTxReceipt, AionTxInfo> txStore;
    private final AionBlockStore blockStore;

    private static final Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

    /** Constructor */
    public ResponseReceiptsHandler(
            TransactionStore<AionTransaction, AionTxReceipt, AionTxInfo> txStore,
            AionBlockStore blockStore) {
        super(Ver.V0, Ctrl.SYNC, Act.RESPONSE_RECEIPTS);
        this.txStore = txStore;
        this.blockStore = blockStore;
    }

    @Override
    public void receive(int id, String displayId, byte[] msg) {
        LOGGER.info("ResTxReceiptHandler received receipts results from " + displayId);
        final ResponseReceipts responseReceipts;
        try {
            responseReceipts = new ResponseReceipts(msg);
        } catch (NullPointerException | IllegalArgumentException ex) {
            LOGGER.error(
                    "ResTxReceiptHandler decode-error, unable to Msg body from {}, length: {}, reason: {}",
                    displayId,
                    msg.length,
                    ex.getMessage());
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("res-tx-receipts dump: {}", ByteUtil.toHexString(msg));
            }
            return;
        }

        for (AionTxInfo atr : responseReceipts.getTxInfo()) {
            List<AionTransaction> txs =
                    blockStore.getBlockByHash(atr.getBlockHash()).getTransactionsList();
            AionTransaction tx = txs.get(atr.getIndex());
            atr.setTransaction(tx);
        }

        LOGGER.debug(
                "ResTxReceiptHandler persisting {} receipts", responseReceipts.getTxInfo().size());
        if (!responseReceipts.getTxInfo().isEmpty()) {
            persist(responseReceipts.getTxInfo());
        }
    }

    protected void persist(List<AionTxInfo> txInfo) {
        for (AionTxInfo txi : txInfo) {
            txStore.putToBatch(txi);
        }
        txStore.flushBatch();
    }
}
