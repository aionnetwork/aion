package org.aion.api.server.rpc3;

import java.math.BigInteger;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.blockchain.AionBlockchainImpl;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.types.AionTxInfo;

public final class AionChainHolder implements ChainHolder {

    private final IAionChain chain;//An implementation of AionChain

    public AionChainHolder(IAionChain chain) {
        if (chain == null) {
            throw new NullPointerException("AionChain is null.");// This class should not
            // be instantiated without an instance of IAionChain
        }
        this.chain = chain;
    }

    @Override
    public Block getBlockByNumber(long block) {
        return this.chain.getAionHub().getBlockchain().getBlockByNumber(block);
    }

    @Override
    public Block getBestBlock() {
        return this.chain.getAionHub().getBlockchain().getBestBlock();
    }

    @Override
    public Block getBlockByHash(byte[] hash) {
        return this.chain.getAionHub().getBlockchain().getBlockByHash(hash);
    }

    @Override
    public BigInteger getTotalDifficultyByHash(byte[] hash) {
        return this.chain.getAionHub().getTotalDifficultyForHash(hash);
    }

    @Override
    public AionTxInfo getTransactionInfo(byte[] transactionHash) {
        return this.chain.getAionHub().getBlockchain().getTransactionInfo(transactionHash);
    }

    /**
     *
     * @param number
     * @return the block reward at the specified block number
     */
    @Override
    public BigInteger calculateReward(Long number) {
        return ((AionBlockchainImpl) this.chain.getAionHub().getBlockchain())
                .getChainConfiguration()
                .getRewardsCalculator(isUnityForkEnabled())
                .calculateReward(number);
    }

    @Override
    public boolean isUnityForkEnabled() {
        return this.chain.getAionHub().getBlockchain().isUnityForkEnabledAtNextBlock();
    }
}
