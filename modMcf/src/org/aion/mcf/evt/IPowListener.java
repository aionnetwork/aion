package org.aion.mcf.evt;

import java.util.List;
import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.base.type.ITxExecSummary;
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
                BLK extends IBlock<?, ?>,
                TX extends ITransaction,
                TXR extends AbstractTxReceipt<?>,
                BS extends AbstractBlockSummary<?, ?, ?, ?>>
        extends IListenerBase<BLK, TX, TXR, BS> {
    void onBlock(BS blockSummary);

    void onPeerDisconnect(String host, long port);

    void onPendingTransactionsReceived(List<TX> transactions);

    void onSyncDone();

    void onNoConnections();

    void onVMTraceCreated(String transactionHash, String trace);

    void onTransactionExecuted(ITxExecSummary summary);
}
