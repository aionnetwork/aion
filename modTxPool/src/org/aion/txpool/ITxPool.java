package org.aion.txpool;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.aion.interfaces.tx.Transaction;
import org.aion.vm.api.types.Address;

/**
 * Aion pending state should be the only user of transaction pool.
 *
 * @param <TX>
 */
public interface ITxPool<TX extends Transaction> {

    String PROP_TX_TIMEOUT = "tx-timeout";
    String PROP_BLOCK_SIZE_LIMIT = "blk-size-limit";
    String PROP_BLOCK_NRG_LIMIT = "blk-nrg-limit";
    String PROP_TX_SEQ_MAX = "tx-seq-max";

    List<TX> add(List<TX> tx);

    // return TX if the TX add success, if the pool already has the same nonce tx. return the old
    // tx.
    TX add(TX tx);

    List<TX> remove(List<TX> tx);

    List<TX> remove(Map<Address, BigInteger> accNonce);

    int size();

    List<TX> snapshot();

    List<TX> getOutdatedList();

    long getOutDateTime();

    BigInteger bestPoolNonce(Address addr);

    void updateBlkNrgLimit(long nrg);

    @SuppressWarnings("SameReturnValue")
    String getVersion();

    List<TX> snapshotAll();

    TX getPoolTx(Address from, BigInteger txNonce);
}
