package org.aion.mcf.blockchain;

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
    
    void setCumulativeDifficulty(BigInteger totalDifficulty);
    
    void setMainChain();
    
    AionAddress getCoinbase();
    
    byte[] getDifficulty();

    byte[] getStateRoot();

    void setStateRoot(byte[] stateRoot);

    BigInteger getCumulativeDifficulty();
    
    byte[] getTxTrieRoot();

    byte[] getLogBloom();
    
    long getNrgConsumed();

    long getNrgLimit();

    byte[] getEncodedBody();

    boolean isParentOf(Block block);
    
    boolean isGenesis();

    ByteArrayWrapper getHashWrapper();

    ByteArrayWrapper getParentHashWrapper();

    void setExtraData(byte[] data);
    
    byte[] getExtraData();

    byte[] getAntiparentHash();

    void setAntiparentHash(byte[] antiparentHash);
}
