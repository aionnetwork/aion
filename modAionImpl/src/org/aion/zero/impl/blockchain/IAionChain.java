package org.aion.zero.impl.blockchain;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import org.aion.base.AionTransaction;
import org.aion.equihash.EquihashMiner;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.db.Repository;
import org.aion.base.AionTxReceipt;
import org.aion.zero.impl.types.PendingTxDetails;

/** Aion chain interface. */
public interface IAionChain  {

    UnityChain getBlockchain();

    void close();

    void broadcastTransaction(AionTransaction transaction);

    AionTxReceipt callConstant(AionTransaction tx, Block block);

    Repository<?> getRepository();

    Repository<?> getPendingState();

    Repository<?> getSnapshotTo(byte[] root);

    List<AionTransaction> getWireTransactions();

    List<AionTransaction> getPendingStateTransactions();

    AionHub getAionHub();

    void exitOn(long number);

    long estimateTxNrg(AionTransaction tx, Block block);

    EquihashMiner getBlockMiner();

    Optional<Long> getInitialStartingBlockNumber();

    Optional<Long> getLocalBestBlockNumber();

    Optional<Long> getNetworkBestBlockNumber();

    void setApiServiceCallback(BlockchainCallbackInterface blockchainCallbackForApiServer);
}
