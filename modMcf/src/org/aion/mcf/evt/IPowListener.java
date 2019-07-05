package org.aion.mcf.evt;

import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.base.Transaction;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.tx.TxExecSummary;
import org.aion.mcf.types.AbstractBlockSummary;
import org.aion.mcf.types.AbstractTxReceipt;

/**
 * POW listener interface.
 *
 * @param <BLK>
 * @param <TX>
 * @param <TXR>
 * @param <BS>
 */
public interface IPowListener<
                BLK extends Block<?>,
                TXR extends AbstractTxReceipt,
                BS extends AbstractBlockSummary<?, ?, ?>>
        extends IListenerBase<BLK, TXR, BS> {
    void onBlock(BS blockSummary);

    void onPeerDisconnect(String host, long port);

    void onPendingTransactionsReceived(List<AionTransaction> transactions);

    void onSyncDone();

    void onNoConnections();

    void onVMTraceCreated(String transactionHash, String trace);

    void onTransactionExecuted(TxExecSummary summary);
}
