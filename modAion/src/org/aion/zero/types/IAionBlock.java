package org.aion.zero.types;

import java.math.BigInteger;
import java.util.List;
import org.aion.base.type.Address;
import org.aion.base.type.IBlock;

/** aion block interface. */
public interface IAionBlock extends IBlock<AionTransaction, A0BlockHeader> {

    Address getCoinbase();

    long getTimestamp();

    byte[] getDifficulty();

    byte[] getStateRoot();

    void setStateRoot(byte[] stateRoot);

    BigInteger getCumulativeDifficulty();

    byte[] getReceiptsRoot();

    byte[] getTxTrieRoot();

    byte[] getLogBloom();

    void setNonce(byte[] nonce);

    List<AionTransaction> getTransactionsList();

    long getNrgConsumed();

    long getNrgLimit();
}
