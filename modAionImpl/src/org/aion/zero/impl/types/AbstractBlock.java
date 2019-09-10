package org.aion.zero.impl.types;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.aion.base.AionTransaction;
import org.aion.mcf.blockchain.Block;
import org.aion.rlp.RLP;
import org.aion.util.types.ByteArrayWrapper;

/** Abstract Block class. */
public abstract class AbstractBlock implements Block {

    /** use for cli tooling */
    Boolean mainChain;
    
    // set from BlockInfos in index database
    byte[] antiparentHash;

    private BigInteger miningDifficulty = null;
    private BigInteger stakingDifficulty = null;
    BigInteger totalDifficulty = null;

    List<AionTransaction> transactionsList = new CopyOnWriteArrayList<>();

    // used to reduce the number of times we create equal wrapper objects
    private ByteArrayWrapper hashWrapper;
    private ByteArrayWrapper parentHashWrapper;

    @Override
    public boolean isEqual(Block block) {
        if (block == null) {
            return false;
        }
        return Arrays.equals(this.getHash(), block.getHash());
    }

    public abstract String getShortDescr();

    abstract void parseRLP();

    /**
     * check if param block is son of this block
     *
     * @param block - possible a son of this
     * @return - true if this block is parent of param block
     */
    @Override
    public boolean isParentOf(Block block) {
        if (block == null) {
            return false;
        }
        return Arrays.equals(this.getHash(), block.getParentHash());
    }

    @Override
    public byte[] getEncodedBody() {
        List<byte[]> body = getBodyElements();
        byte[][] elements = body.toArray(new byte[body.size()][]);
        return RLP.encodeList(elements);
    }

    List<byte[]> getBodyElements() {
        parseRLP();
        byte[] transactions = getTransactionsEncoded();
        List<byte[]> body = new ArrayList<>();
        body.add(transactions);
        return body;
    }

    private byte[] getTransactionsEncoded() {

        byte[][] transactionsEncoded = new byte[transactionsList.size()][];
        int i = 0;
        for (AionTransaction tx : transactionsList) {
            transactionsEncoded[i] = tx.getEncoded();
            ++i;
        }
        return RLP.encodeList(transactionsEncoded);
    }

    public byte[] getAntiparentHash() {
        return antiparentHash;
    }

    public void setAntiparentHash(byte[] antiparentHash) {
        if (antiparentHash == null) {
            throw new NullPointerException("antiparentHash is null");
        }

        this.antiparentHash = antiparentHash;
    }

    public BigInteger getMiningDifficulty() {
        if (miningDifficulty == null) {
            return BigInteger.ZERO;
        } else {
            return miningDifficulty;
        }
    }

    public void setMiningDifficulty(BigInteger miningDifficulty) {
        if (miningDifficulty == null) {
            throw new NullPointerException("miningDifficulty is null");
        }

        this.miningDifficulty = miningDifficulty;
        if (this.stakingDifficulty != null) {
            this.totalDifficulty = stakingDifficulty.multiply(miningDifficulty);
        }    
    }

    public BigInteger getStakingDifficulty() {
        if (stakingDifficulty == null) {
            return BigInteger.ZERO;
        } else {
            return stakingDifficulty;
        }
    }

    public void setStakingDifficulty(BigInteger stakingDifficulty) {
        if (stakingDifficulty == null) {
            throw new NullPointerException("stakingDifficulty is null");
        }

        this.stakingDifficulty = stakingDifficulty;
        if (this.miningDifficulty != null) {
            this.totalDifficulty = stakingDifficulty.multiply(miningDifficulty);
        }
    }

    @Override
    public BigInteger getCumulativeDifficulty() {
        if (totalDifficulty == null) {
            return BigInteger.ZERO;
        } else {
            return totalDifficulty;
        }
    }

    @Override
    public void setCumulativeDifficulty(BigInteger totalDifficulty) {
        if (totalDifficulty == null) {
            throw new NullPointerException("totalDifficulty is null");
        }
        this.totalDifficulty = totalDifficulty;
    }

    @Override
    public boolean isMainChain() {
        return mainChain;
    }

    @Override
    public void setMainChain() {
        mainChain = true;
    }

    /**
     * Returns a {@link ByteArrayWrapper} instance of the block's hash.
     *
     * @return a {@link ByteArrayWrapper} instance of the block's hash
     * @implNote Not safe when the block is mutable. Use this only for sync where the block's hash
     *     cannot change during execution.
     */
    @Override
    public ByteArrayWrapper getHashWrapper() {
        if (hashWrapper == null) {
            hashWrapper = ByteArrayWrapper.wrap(getHash());
        }
        return hashWrapper;
    }

    /**
     * Returns a {@link ByteArrayWrapper} instance of the block's parent hash.
     *
     * @return a {@link ByteArrayWrapper} instance of the block's parent hash
     * @implNote Not safe when the block is mutable. Use this only for sync where the block's parent
     *     hash cannot change during execution.
     */
    @Override
    public ByteArrayWrapper getParentHashWrapper() {
        if (parentHashWrapper == null) {
            parentHashWrapper = ByteArrayWrapper.wrap(getParentHash());
        }
        return parentHashWrapper;
    }
}
