package org.aion.api.server.external;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.aion.api.server.external.account.Account;
import org.aion.api.server.external.account.AccountManagerInterface;
import org.aion.api.server.external.types.SyncInfo;
import org.aion.api.server.nrgprice.NrgOracle;
import org.aion.base.AccountState;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.crypto.ECKey;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.mcf.db.Repository;
import org.aion.types.AionAddress;
import org.aion.zero.impl.blockchain.AionBlockchainImpl;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.blockchain.UnityChain;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.keystore.Keystore;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.impl.types.BlockContext;
import org.aion.zero.impl.types.StakingBlock;
import org.aion.zero.impl.types.TxResponse;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

public class AionChainHolder implements ChainHolder {

    private NrgOracle nrgOracle;
    private final IAionChain chain;//An implementation of AionChain
    private final AtomicReference<BlockContext> currentTemplate;
    private final AccountManagerInterface accountManager;
    private final long recommendedNrg;
    private final long syncTolerance;

    public AionChainHolder(IAionChain chain,
        AccountManagerInterface accountManager) {
        this(chain, accountManager, CfgAion.inst().getApi().getNrg().getNrgPriceDefault(), 1);
    }

    @SuppressWarnings("WeakerAccess")
    public AionChainHolder(IAionChain chain,
        AccountManagerInterface accountManager, long recommendedNrg, long syncTolerance) {
        if (chain == null) {
            throw new NullPointerException("AionChain is null.");// This class should not
            // be instantiated without an instance of IAionChain
        }
        if (accountManager == null) {
            throw new NullPointerException("AccountManager is null.");// This class should not
            // be instantiated without an instance of AccountManager
        }
        this.chain = chain;
        currentTemplate = new AtomicReference<>(null);
        this.accountManager = accountManager;
        initNrgOracle(chain);
        this.syncTolerance = syncTolerance;
        this.recommendedNrg = recommendedNrg;
    }

    private void initNrgOracle(IAionChain ac) {
        if (nrgOracle != null) return;

        UnityChain bc = ac.getBlockchain();
        long nrgPriceDefault = CfgAion.inst().getApi().getNrg().getNrgPriceDefault();
        long nrgPriceMax = CfgAion.inst().getApi().getNrg().getNrgPriceMax();

        NrgOracle.Strategy oracleStrategy = NrgOracle.Strategy.SIMPLE;
        if (CfgAion.inst().getApi().getNrg().isOracleEnabled()) {
            oracleStrategy = NrgOracle.Strategy.BLK_PRICE;
        }

        nrgOracle = new NrgOracle(bc, nrgPriceDefault, nrgPriceMax, oracleStrategy);
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
            final boolean sealed = addNewBlock(stakingBlock);

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
            final boolean sealedSuccessfully = addNewBlock(bestPowBlock);

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
    public boolean addNewBlock(Block block) {
        return ((AionImpl) chain).addNewBlock(block).isSuccessful();
    }

    @Override
    public BigInteger getAccountBalance(AionAddress aionAddress, long blockNumber) {
        return getRepoByBlockNumber(blockNumber).getBalance(aionAddress);
    }

    @Override
    public BigInteger getAccountNonce(AionAddress aionAddress, long blockNumber) {
        return getRepoByBlockNumber(blockNumber).getNonce(aionAddress);
    }

    @Override
    public BigInteger getAccountBalance(AionAddress aionAddress) {
        return this.chain.getRepository().getBalance(aionAddress);
    }

    @Override
    public BigInteger getAccountNonce(AionAddress aionAddress) {
        return this.chain.getRepository().getNonce(aionAddress);
    }

    @Override
    public AccountState getAccountState(AionAddress aionAddress) {
        final AccountState accountState = ((AionRepositoryImpl) this.chain.getRepository())
            .getAccountState(aionAddress);
        if (accountState == null) {
            return new AccountState();//
        }else {
            return accountState;
        }
    }

    @Override
    public long blockNumber() {
        return this.getBestBlock().getNumber();
    }

    @Override
    public boolean unlockAccount(AionAddress aionAddress, String password, int timeout) {
        return accountManager.unlockAccount(aionAddress, password, timeout);
    }

    @Override
    public boolean lockAccount(AionAddress aionAddress, String password) {
        return accountManager.lockAccount(aionAddress, password);
    }

    @Override
    public AionAddress newAccount(String password) {
        return accountManager.createAccount(password);
    }

    @Override
    public List<AionAddress> listAccounts() {
        return accountManager.getAccounts().stream().map(Account::getKey).map(ECKey::getAddress)
            .map(AionAddress::new).collect(Collectors.toUnmodifiableList());
    }

    @Override
    public long getRecommendedNrg() {
        if (nrgOracle != null) {
            return nrgOracle.getNrgPrice();
        } else {
            return recommendedNrg;
        }
    }

    @Override
    public AionTxReceipt call(AionTransaction transaction, Block block) {
        return this.chain.callConstant(transaction, block);
    }

    @Override
    public SyncInfo getSyncInfo() {
        Optional<Long> bestLocalBlock = this.chain.getLocalBestBlockNumber();
        Optional<Long> networkBestBlock = this.chain.getNetworkBestBlockNumber();
        if (bestLocalBlock.isPresent() && networkBestBlock.isPresent()){
            boolean done = bestLocalBlock.get() + syncTolerance >= networkBestBlock.get();
            long chainStartingBlock = this.chain.getInitialStartingBlockNumber().orElse(0L);
            return new SyncInfo(done, chainStartingBlock, bestLocalBlock.get(), networkBestBlock.get());
        }else {
            return null;
        }
    }

    @Override
    public Pair<byte[], TxResponse> sendTransaction(AionTransaction tx) {
        final TxResponse response;
        if (tx==null) response = TxResponse.INVALID_TX;
        else response = this.chain.getAionHub().getPendingState().addPendingTransaction(tx);
        if (response.isFail()){
            AionLoggerFactory.getLogger(LogEnum.API.name()).debug("<send-transaction failed response={}>", response.name());
        } else {
            AionLoggerFactory.getLogger(LogEnum.API.name()).debug("<send-transaction succeeded response={}>", response.name());
        }
        return new ImmutablePair<>(tx.getTransactionHash(), response);
    }

    @Override
    public ECKey getKey(AionAddress aionAddress) {
        return accountManager.getKey(aionAddress);
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

    /*
    Returns the blockchain state at the specified block number
    */
    private Repository getRepoByBlockNumber(long blockNumber){
        return this.chain.getRepository()
            .getSnapshotTo(getBlockByNumber(blockNumber).getStateRoot());
    }
}
