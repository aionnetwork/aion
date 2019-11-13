package org.aion.api.server.rpc3;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.types.AionAddress;
import org.aion.zero.impl.blockchain.AionBlockchainImpl;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.keystore.Keystore;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.impl.types.BlockContext;
import org.aion.zero.impl.types.StakingBlock;

public class AionChainHolder implements ChainHolder {

    private final IAionChain chain;//An implementation of AionChain
    private final AtomicReference<BlockContext> currentTemplate;

    public AionChainHolder(IAionChain chain) {
        if (chain == null) {
            throw new NullPointerException("AionChain is null.");// This class should not
            // be instantiated without an instance of IAionChain
        }
        this.chain = chain;
        currentTemplate = new AtomicReference<>(null);
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

    @Override
    public boolean submitSignature(byte[] signature, byte[] sealHash) {
        if (!isUnityForkEnabled()) throw new UnsupportedOperationException();
        else {
            StakingBlock stakingBlock = chain.getBlockchain().getCachingStakingBlockTemplate(sealHash);
            stakingBlock.seal(signature, stakingBlock.getHeader().getSigningPublicKey());
            ImportResult result = chain.getBlockchain().tryToConnect(stakingBlock);
            final boolean sealed =
                result == ImportResult.IMPORTED_BEST || result == ImportResult.IMPORTED_NOT_BEST;

            if (sealed) {
                logSealedBlock(stakingBlock);
            }else {
                logFailedSealedBlock(stakingBlock);
            }
            return sealed;
        }
    }

    @Override
    public byte[] submitSeed(byte[] newSeed, byte[] signingPublicKey, byte[] coinBase) {
        if (!isUnityForkEnabled()) throw new UnsupportedOperationException();
        else {
            StakingBlock blockTemplate =
                    this.chain
                            .getAionHub()
                            .getStakingBlockTemplate(newSeed, signingPublicKey, coinBase);
            if (blockTemplate == null) {
                return null;
            } else {
                return blockTemplate.getHeader().getMineHash();
            }
        }
    }

    @Override
    public byte[] getSeed() {
        if (!isUnityForkEnabled()) throw new UnsupportedOperationException();
        else return this.chain.getBlockchain().getSeed();
    }

    @Override
    public synchronized BlockContext getBlockTemplate() {
        return currentTemplate.updateAndGet(bc -> this.chain
            .getAionHub()
            .getNewMiningBlockTemplate(bc,
                TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())));
    }

    @Override
    public boolean submitBlock(byte[] nonce, byte[] solution, byte[] headerHash) {
        AionBlock bestPowBlock = this.chain.getBlockchain().getCachingMiningBlockTemplate(headerHash);
        if (bestPowBlock == null) {
            return false; // cannot seal a block that does not exist
        } else {
            bestPowBlock.seal(nonce, solution);
            ImportResult result = ((AionImpl) chain).addNewBlock(bestPowBlock);
            final boolean sealedSuccessfully =
                result == ImportResult.IMPORTED_BEST || result == ImportResult.IMPORTED_NOT_BEST;

            if (sealedSuccessfully) {
                logSealedBlock(bestPowBlock);
            } else {
               logFailedSealedBlock(bestPowBlock);
            }
            return sealedSuccessfully;
        }
    }
    
    @Override
    public boolean canSeal(byte[] headerHash) {
        return this.chain.getBlockchain().getCachingMiningBlockTemplate(headerHash) != null ||
            this.chain.getBlockchain().getCachingStakingBlockTemplate(headerHash) != null;
    }

    @Override
    public AionBlock getBestPOWBlock() {
        return this.chain.getBlockchain().getBestMiningBlock();
    }

    @Override
    public StakingBlock getBestPOSBlock() {
        return this.chain.getBlockchain().getBestStakingBlock();
    }

    @Override
    public boolean addressExists(AionAddress address) {
        return Keystore.exist(address.toString());
    }

    private void logSealedBlock(Block block){
        //log that the block was sealed
        AionLoggerFactory.getLogger(LogEnum.CONS.toString()).info(
            "{} block submitted via api <num={}, hash={}, diff={}, tx={}>",
            block.getHeader().getSealType().equals(BlockSealType.SEAL_POW_BLOCK) ? "Mining": "Staking",
            block.getNumber(),
            block.getShortHash(), // LogUtil.toHexF8(newBlock.getHash()),
            block.getHeader().getDifficultyBI().toString(),
            block.getTransactionsList().size());
    }

    private void logFailedSealedBlock(Block block){
        //log that the block could not be sealed
        AionLoggerFactory.getLogger(LogEnum.CONS.toString()).info(
            "Unable to submit {} block via api <num={}, hash={}, diff={}, tx={}>",
            block.getHeader().getSealType().equals(BlockSealType.SEAL_POW_BLOCK) ? "mining": "staking",
            block.getNumber(),
            block.getShortHash(), // LogUtil.toHexF8(newBlock.getHash()),
            block.getHeader().getDifficultyBI().toString(),
            block.getTransactionsList().size());
    }
}
