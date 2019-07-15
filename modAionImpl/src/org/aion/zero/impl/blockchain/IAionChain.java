package org.aion.zero.impl.blockchain;

import java.math.BigInteger;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.IChainInstancePOW;
import org.aion.mcf.blockchain.IPowChain;
import org.aion.mcf.db.Repository;
import org.aion.types.AionAddress;
import org.aion.zero.impl.AionHub;
import org.aion.zero.impl.query.QueryInterface;
import org.aion.zero.types.AionTxReceipt;

/** Aion chain interface. */
public interface IAionChain extends IChainInstancePOW, QueryInterface {

    IPowChain getBlockchain();

    void close();

    AionTransaction createTransaction(
            BigInteger nonce, AionAddress to, BigInteger value, byte[] data);

    void broadcastTransaction(AionTransaction transaction);

    AionTxReceipt callConstant(AionTransaction tx, Block block);

    Repository<?, ?> getRepository();

    Repository<?, ?> getPendingState();

    Repository<?, ?> getSnapshotTo(byte[] root);

    List<AionTransaction> getWireTransactions();

    List<AionTransaction> getPendingStateTransactions();

    AionHub getAionHub();

    void exitOn(long number);

    long estimateTxNrg(AionTransaction tx, Block block);
}
