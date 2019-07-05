package org.aion.txpool;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.aion.base.AionTransaction;
import org.aion.base.Transaction;
import org.aion.types.AionAddress;

/**
 * Aion pending state should be the only user of transaction pool.
 *
 * @param <TX>
 */
public interface ITxPool {

    String PROP_TX_TIMEOUT = "tx-timeout";
    String PROP_BLOCK_SIZE_LIMIT = "blk-size-limit";
    String PROP_BLOCK_NRG_LIMIT = "blk-nrg-limit";
    String PROP_TX_SEQ_MAX = "tx-seq-max";

    List<AionTransaction> add(List<AionTransaction> tx);

    // return TX if the TX add success, if the pool already has the same nonce tx. return the old
    // tx.
    AionTransaction add(AionTransaction tx);

    List<AionTransaction> remove(List<AionTransaction> tx);

    List<AionTransaction> remove(Map<AionAddress, BigInteger> accNonce);

    int size();

    List<AionTransaction> snapshot();

    List<AionTransaction> getOutdatedList();

    long getOutDateTime();

    BigInteger bestPoolNonce(AionAddress addr);

    void updateBlkNrgLimit(long nrg);

    @SuppressWarnings("SameReturnValue")
    String getVersion();

    List<AionTransaction> snapshotAll();

    AionTransaction getPoolTx(AionAddress from, BigInteger txNonce);
}
