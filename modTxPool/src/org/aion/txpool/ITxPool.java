package org.aion.txpool;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.aion.base.AionTransaction;
import org.aion.base.PooledTransaction;
import org.aion.types.AionAddress;

/** Aion pending state should be the only user of transaction pool. */
public interface ITxPool {

    String PROP_TX_TIMEOUT = "tx-timeout";
    String PROP_BLOCK_SIZE_LIMIT = "blk-size-limit";
    String PROP_BLOCK_NRG_LIMIT = "blk-nrg-limit";
    String PROP_TX_SEQ_MAX = "tx-seq-max";

    List<PooledTransaction> add(List<PooledTransaction> tx);

    // return TX if the TX add success, if the pool already has the same nonce tx. return the old
    // tx.
    PooledTransaction add(PooledTransaction tx);

    List<PooledTransaction> remove(List<PooledTransaction> tx);

    PooledTransaction remove(PooledTransaction tx);

    List<PooledTransaction> remove(Map<AionAddress, BigInteger> accNonce);

    int size();

    List<AionTransaction> snapshot();

    List<PooledTransaction> getOutdatedList();

    long getOutDateTime();

    BigInteger bestPoolNonce(AionAddress addr);

    void updateBlkNrgLimit(long nrg);

    @SuppressWarnings("SameReturnValue")
    String getVersion();

    List<AionTransaction> snapshotAll();

    PooledTransaction getPoolTx(AionAddress from, BigInteger txNonce);
}
