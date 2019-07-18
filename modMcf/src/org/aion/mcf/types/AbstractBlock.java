package org.aion.mcf.types;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.aion.base.AionTransaction;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.rlp.RLP;
import org.slf4j.Logger;

/** Abstract Block class. */
public abstract class AbstractBlock implements Block {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.toString());

    /** use for cli tooling */
    protected Boolean mainChain;
    
    // set from BlockInfos in index database
    protected byte[] antiparentHash;

    protected BigInteger miningDifficulty = null;
    protected BigInteger stakingDifficulty = null;
    protected BigInteger totalDifficulty = null;

    protected List<AionTransaction> transactionsList = new CopyOnWriteArrayList<>();

    @Override
    public boolean isEqual(Block block) {
        return Arrays.equals(this.getHash(), block.getHash());
    }

    public abstract String getShortDescr();

    public abstract void parseRLP();

    /**
     * check if param block is son of this block
     *
     * @param block - possible a son of this
     * @return - true if this block is parent of param block
     */
    public boolean isParentOf(Block block) {
        return Arrays.equals(this.getHash(), block.getParentHash());
    }

    public byte[] getEncodedBody() {
        List<byte[]> body = getBodyElements();
        byte[][] elements = body.toArray(new byte[body.size()][]);
        return RLP.encodeList(elements);
    }

    public List<byte[]> getBodyElements() {
        parseRLP();
        byte[] transactions = getTransactionsEncoded();
        List<byte[]> body = new ArrayList<>();
        body.add(transactions);
        return body;
    }

    public byte[] getTransactionsEncoded() {

        byte[][] transactionsEncoded = new byte[transactionsList.size()][];
        int i = 0;
        for (AionTransaction tx : transactionsList) {
            transactionsEncoded[i] = tx.getEncoded();
            ++i;
        }
        return RLP.encodeList(transactionsEncoded);
    }

    public void setMainChain() {
        mainChain = true;
    }

    public byte[] getAntiparentHash() {
        return antiparentHash;
    }

    public void setAntiparentHash(byte[] antiparentHash) {
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
        this.miningDifficulty = miningDifficulty;
        if (this.miningDifficulty != null && this.stakingDifficulty != null) {
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
        this.stakingDifficulty = stakingDifficulty;
        if (this.miningDifficulty != null && this.stakingDifficulty != null) {
            this.totalDifficulty = stakingDifficulty.multiply(miningDifficulty);
        }
    }

    public BigInteger getCumulativeDifficulty() {
        if (totalDifficulty == null) {
            return BigInteger.ZERO;
        } else {
            return totalDifficulty;
        }
    }

    public void setCumulativeDifficulty(BigInteger totalDifficulty) {
        this.totalDifficulty = totalDifficulty;
    }
}
