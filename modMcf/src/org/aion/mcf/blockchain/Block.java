package org.aion.mcf.blockchain;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;

/** @author jin */
public interface Block {

    long getNumber();

    byte[] getParentHash();

    byte[] getHash();

    byte[] getEncoded();

    String getShortHash();

    boolean isEqual(Block block);

    String getShortDescr();

    List<AionTransaction> getTransactionsList();

    BlockHeader getHeader();

    /**
     * Newly added with the refactory of API for libNc, both chains should have implemented this
     *
     * @return the ReceiptsRoot represent as a byte array
     */
    byte[] getReceiptsRoot();

    long getTimestamp();

    BigInteger getDifficultyBI();

    AionAddress getCoinbase();
    
    byte[] getDifficulty();

    byte[] getStateRoot();

    byte[] getTxTrieRoot();

    byte[] getLogBloom();
    
    long getNrgConsumed();

    long getNrgLimit();

    byte[] getEncodedBody();

    boolean isParentOf(Block block);
    
    boolean isGenesis();

    ByteArrayWrapper getHashWrapper();

    ByteArrayWrapper getParentHashWrapper();

    byte[] getExtraData();

    /* this setter is only relate with the consensus itself. Not been include inside the block data*/
    void setUnityDifficulty(Difficulty ud);

    void setMainChain();

    BigInteger getMiningDifficulty();

    BigInteger getStakingDifficulty();

    BigInteger getCumulativeDifficulty();

    int size();

    byte[] getAntiparentHash();

    void setAntiparentHash(byte[] antiparentHash);

    boolean isMainChain();

    @VisibleForTesting
    void updateHeader(BlockHeader header);

    void updateHeaderDifficulty(byte[] diff);

    void updateTransactionAndState(
            List<AionTransaction> transactions,
            byte[] txTrieRoot,
            byte[] stateRoot,
            byte[] bloom,
            byte[] receiptRoot,
            long energyUsed);
}
