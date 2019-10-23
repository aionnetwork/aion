package org.aion.zero.impl.blockchain;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.aion.avm.stub.IAvmResourceFactory;
import org.aion.avm.stub.IContractFactory.AvmContract;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.types.AionAddress;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.A0BlockHeader;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.StakingBlock;
import org.aion.zero.impl.types.StakingBlockHeader;
import org.aion.zero.impl.vm.TestResourceProvider;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Methods used by several classes testing the blockchain functionality.
 *
 * @author Alexandra Roatis
 */
public class BlockchainTestUtils {
    private static final Random rand = new Random();
    private static final byte[] ZERO_BYTE = new byte[0];
    private static final long NRG = 21000L;
    private static final long NRG_PRICE = 10_123_456_789L;

    public static List<ECKey> generateAccounts(int size) {
        List<ECKey> accs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            accs.add(ECKeyFac.inst().create());
        }
        return accs;
    }

    public static List<AionTransaction> generateTransactions(
            int maxSize, List<ECKey> accounts, AionRepositoryImpl repo) {
        int size = rand.nextInt(maxSize);

        if (size == 0) {
            return Collections.emptyList();
        } else {
            // get the current nonce for each account
            Map<ECKey, BigInteger> nonces = new HashMap<>();
            for (ECKey key : accounts) {
                nonces.put(key, repo.getNonce(new AionAddress(key.getAddress())));
            }

            List<AionTransaction> transactions = new ArrayList<>();

            for (int i = 0; i < size; i++) {
                // get a random account
                ECKey key = accounts.get(rand.nextInt(accounts.size()));
                BigInteger accountNonce = nonces.get(key);

                // generate a random Aion account address
                byte[] aionBytes = HashUtil.h256(accountNonce.toByteArray());
                aionBytes[0] = (byte) 0xa0; // the Aion prefix
                AionAddress destAddr = new AionAddress(aionBytes);
                AionTransaction newTx =
                        AionTransaction.create(
                                key,
                                accountNonce.toByteArray(),
                                destAddr,
                                BigInteger.ONE.toByteArray(),
                                ZERO_BYTE,
                                NRG,
                                NRG_PRICE,
                                TransactionTypes.DEFAULT, null);
                transactions.add(newTx);
                accountNonce = accountNonce.add(BigInteger.ONE);
                nonces.put(key, accountNonce);
            }

            return transactions;
        }
    }

    /**
     * @param chain blockchain implementation to be populated
     * @param blocks number of blocks in the chain
     * @param frequency every multiple of frequency block will be on the main chain
     * @param accounts existing accounts
     * @param txCount maximum number of transactions per block
     * @apiNote Supports only chains that do not include the Unity fork.
     */
    public static void generateRandomChain(
            StandaloneBlockchain chain,
            long blocks,
            int frequency,
            List<ECKey> accounts,
            int txCount) {
        long seed = rand.nextLong();
        rand.setSeed(seed);
        System.out.println("Random seed used: " + seed);

        Block parent, block, mainChain;
        mainChain = chain.getGenesis();

        List<Block> knownBlocks = new ArrayList<>();
        knownBlocks.add(mainChain);

        List<AionTransaction> txs;
        AionRepositoryImpl repo = chain.getRepository();

        long time = System.currentTimeMillis();
        for (int i = 0; i < blocks; i++) {

            // ensuring that we add to the main chain at least every MAIN_CHAIN_FREQUENCY block
            if (i % frequency == 0) {
                // the parent will be the main chain
                parent = mainChain;
            } else {
                // decide if to keep the node among potential parents
                if (rand.nextBoolean()) {
                    // the parent is a random already imported block
                    parent = knownBlocks.get(rand.nextInt(knownBlocks.size()));
                } else {
                    // randomly remove some parent nodes
                    parent = knownBlocks.remove(rand.nextInt(knownBlocks.size()));
                }
            }

            // generate transactions for correct root
            byte[] originalRoot = repo.getRoot();
            repo.syncToRoot(parent.getStateRoot());
            txs = generateTransactions(txCount, accounts, repo);
            repo.syncToRoot(originalRoot);

            if (!chain.forkUtility.isUnityForkActive(parent.getNumber() + 1)) {
                // create and import pre-unity block
                Pair<Block, ImportResult> importPair = addMiningBlock(chain, parent, txs, time, String.valueOf(i).getBytes());
                time += 10;
                block = importPair.getLeft();
                knownBlocks.add(block);
                if (importPair.getRight() == ImportResult.IMPORTED_BEST) {
                    mainChain = block;
                }
            } else {
                throw new IllegalStateException("This method cannot create Unity chains.");
            }
        }
    }

    /**
     * @param chain blockchain implementation to be populated
     * @param blocks number of blocks in the chain
     * @param frequency every multiple of frequency block will be on the main chain
     * @apiNote Supports only chains that do not include the Unity fork.
     */
    public static void generateRandomChainWithoutTransactions(StandaloneBlockchain chain, long blocks, int frequency) {
        long seed = rand.nextLong();
        rand.setSeed(seed);
        System.out.println("Random seed used: " + seed);

        Block parent, block, mainChain;
        mainChain = chain.getGenesis();

        List<Block> knownBlocks = new ArrayList<>();
        knownBlocks.add(mainChain);

        List<AionTransaction> txs = Collections.emptyList();

        long time = System.currentTimeMillis();
        for (int i = 0; i < blocks; i++) {

            // ensuring that we add to the main chain at least every MAIN_CHAIN_FREQUENCY block
            if (i % frequency == 0) {
                // the parent will be the main chain
                parent = mainChain;
            } else {
                // decide if to keep the node among potential parents
                if (rand.nextBoolean()) {
                    // the parent is a random already imported block
                    parent = knownBlocks.get(rand.nextInt(knownBlocks.size()));
                } else {
                    // randomly remove some parent nodes
                    parent = knownBlocks.remove(rand.nextInt(knownBlocks.size()));
                }
            }

            if (!chain.forkUtility.isUnityForkActive(parent.getNumber() + 1)) {
                // create and import pre-unity block
                Pair<Block, ImportResult> importPair = addMiningBlock(chain, parent, txs, time, String.valueOf(i).getBytes());
                time += 10;
                block = importPair.getLeft();
                knownBlocks.add(block);
                if (importPair.getRight() == ImportResult.IMPORTED_BEST) {
                    mainChain = block;
                }
            } else {
                throw new IllegalStateException("This method cannot create Unity chains.");
            }
        }
    }

    private static Pair<Block, ImportResult> addMiningBlock(StandaloneBlockchain chain, Block parent, List<AionTransaction> txs, long time, byte[] extraData) {
        AionBlock block = chain.createNewMiningBlockInternal(parent, txs, true, time / 10000L).block;

        A0BlockHeader newBlockHeader = A0BlockHeader.Builder.newInstance().withHeader(block.getHeader()).withExtraData(extraData).build();
        block.updateHeader(newBlockHeader);

        ImportResult result = chain.tryToConnectInternal(block, (time + 10));

        System.out.format(
                "Created block with hash: %s, number: %6d, extra data: %6s, txs: %3d, import status: %20s %n",
                block.getShortHash(),
                block.getNumber(),
                new String(block.getExtraData()),
                block.getTransactionsList().size(),
                result.toString());

        return Pair.of(block, result);
    }

    /**
     * Generates a random chain with alternating mining and staking blocks after the unity fork is reached.
     * The staking registry contract is deployed in the block before the fork.
     * Stakers are registered in the Unity fork block.
     *
     * @param chain blockchain implementation to be populated
     * @param resourceProvider provides the implementations of the two AVMs; <b>must have both enabled</b>
     * @param blocks number of blocks to be added to the chain
     * @param frequency every multiple of frequency block will be on the main chain
     * @param accounts existing accounts with balance on the chain used to generate random transactions; <b>will be used as stakers after the Unity fork</b>
     * @param stakingRegistryOwner existing account with balance; <b>will be used to deploy the StakerRegistry contract</b>
     * @param txCount maximum number of transactions per block
     */
    public static void generateRandomUnityChain(StandaloneBlockchain chain, TestResourceProvider resourceProvider, long blocks, int frequency, List<ECKey> accounts, ECKey stakingRegistryOwner, int txCount) {
        long seed = rand.nextLong();
        rand.setSeed(seed);
        System.out.println("Random seed used: " + seed);

        // ensure the stakingRegistryOwner is not among the stakers
        List<ECKey> stakers = new ArrayList<>(accounts);
        stakers.remove(stakingRegistryOwner);

        if (stakingRegistryOwner == null || stakers == null || stakers.isEmpty()) {
            throw new IllegalStateException("Please provide the accounts required to build a Unity chain.");
        }

        Block parent, block, mainChain;
        mainChain = chain.getGenesis();

        List<Block> knownBlocks = new ArrayList<>();
        knownBlocks.add(mainChain);

        List<AionTransaction> txs;
        AionRepositoryImpl repo = chain.getRepository();

        long time = System.currentTimeMillis();
        for (int i = 0; i < blocks; i++) {
            // ensuring that we add to the main chain at least every MAIN_CHAIN_FREQUENCY block
            if (i % frequency == 0) {
                // the parent will be the main chain
                parent = mainChain;
            } else {
                // decide if to keep the node among potential parents
                if (rand.nextBoolean()) {
                    // the parent is a random already imported block
                    parent = knownBlocks.get(rand.nextInt(knownBlocks.size()));
                } else {
                    // randomly remove some parent nodes
                    parent = knownBlocks.remove(rand.nextInt(knownBlocks.size()));
                }
            }
            long nextBlockNumber = parent.getNumber() + 1;

            // generate transactions for correct root
            byte[] originalRoot = repo.getRoot();
            repo.syncToRoot(parent.getStateRoot());
            // ensure that staking is possible after the fork block
            if (chain.forkUtility.isUnityForkBlock(nextBlockNumber + 1)) {
                // deploy staking contract
                AionTransaction tx = deployStakingContract(resourceProvider.factoryForVersion1, stakingRegistryOwner, repo.getNonce(new AionAddress(stakingRegistryOwner.getAddress())));
                txs = List.of(tx);

                // set the staking contract address in the staking genesis
                AionAddress contract = TxUtil.calculateContractAddress(tx.getSenderAddress().toByteArray(), tx.getNonceBI());
                chain.getGenesis().setStakingContractAddress(contract);
            } else if (chain.forkUtility.isUnityForkBlock(nextBlockNumber)) {
                // set the staking contract address in the staking genesis
                AionAddress contract = chain.getGenesis().getStakingContractAddress();
                txs = new ArrayList<>();

                for (ECKey key : stakers) {
                    // register stakers
                    txs.add(registerStaker(resourceProvider.factoryForVersion1, key, repo.getNonce(new AionAddress(key.getAddress())), contract));
                }
            } else {
                txs = generateTransactions(txCount, accounts, repo);
            }
            // get back to current root
            repo.syncToRoot(originalRoot);

            if (chain.forkUtility.isUnityForkActive(nextBlockNumber)) {
                // create and import post-unity block
                Pair<Block, ImportResult> importPair;

                if (parent.getHeader().getSealType() == BlockSealType.SEAL_POS_BLOCK) {
                    importPair = addMiningBlock(chain, parent, txs, time, String.valueOf(i).getBytes());
                } else {
                    // get the parent seed
                    byte[] parentSeed = chain.forkUtility.isUnityForkBlock(parent.getNumber())
                            ? StakingBlockHeader.GENESIS_SEED
                            : ((StakingBlock) chain.getBlockByHash(parent.getParentHash())).getSeed();
                    importPair = addStakingBlock(chain, parentSeed, txs, time, stakers.get(rand.nextInt(stakers.size())));
                }

                if (importPair != null) {
                    time += 10;
                    block = importPair.getLeft();
                    knownBlocks.add(block);
                    if (importPair.getRight() == ImportResult.IMPORTED_BEST) {
                        mainChain = block;
                    }
                }
            } else {
                // create and import pre-unity block
                Pair<Block, ImportResult> importPair= addMiningBlock(chain, parent, txs, time, String.valueOf(i).getBytes());
                time += 10;
                block = importPair.getLeft();
                knownBlocks.add(block);
                if (importPair.getRight() == ImportResult.IMPORTED_BEST) {
                    mainChain = block;
                }
            }
        }
    }

    private static AionTransaction deployStakingContract(IAvmResourceFactory avmFactory, ECKey owner, BigInteger nonce) {
        byte[] jar = avmFactory.newContractFactory().getDeploymentBytes(AvmContract.UNITY_STAKER_REGISTRY);
        return AionTransaction.create(
                owner,
                nonce.toByteArray(),
                null,
                new byte[0],
                jar,
                5_000_000L,
                NRG_PRICE,
                TransactionTypes.AVM_CREATE_CODE,
                null);
    }

    private static AionTransaction registerStaker(
            IAvmResourceFactory avmFactory, ECKey caller, BigInteger nonce, AionAddress contract) {
        AionAddress callerAddress = new AionAddress(caller.getAddress());
        byte[] callBytes =
                avmFactory
                        .newStreamingEncoder()
                        .encodeOneString("registerStaker")
                        .encodeOneAddress(callerAddress) // staker address
                        .encodeOneAddress(callerAddress) // signing address
                        .encodeOneAddress(callerAddress) // coinbase address
                        .getEncoding();
        return AionTransaction.create(
                caller,
                nonce.toByteArray(),
                contract,
                MIN_SELF_STAKE.toByteArray(),
                callBytes,
                2_000_000L,
                NRG_PRICE,
                TransactionTypes.DEFAULT,
                null);
    }

    private static final BigInteger MIN_SELF_STAKE = new BigInteger("1000000000000000000000");

    private static Pair<Block, ImportResult> addStakingBlock(
            StandaloneBlockchain chain,
            byte[] parentSeed,
            List<AionTransaction> txs,
            long time,
            ECKey key) {
        byte[] newSeed = key.sign(parentSeed).getSignature();
        StakingBlock block =
                chain.createStakingBlockTemplate(
                        txs,
                        key.getPubKey(),
                        newSeed,
                        new AionAddress(key.getAddress()).toByteArray());

        if (block == null) {
            return null;
        }

        byte[] mineHashSig = key.sign(block.getHeader().getMineHash()).getSignature();
        block.seal(mineHashSig, key.getPubKey());

        ImportResult result = chain.tryToConnectInternal(block, (time + 10));

        System.out.format(
                "Created block with hash: %s, number: %6d, extra data: %6s, txs: %3d, import status: %20s %n",
                block.getShortHash(),
                block.getNumber(),
                new String(block.getExtraData()),
                block.getTransactionsList().size(),
                result.toString());

        return Pair.of(block, result);
    }

    /**
     * @param chain blockchain implementation to be populated
     * @param accounts existing accounts
     * @param txCount maximum number of transactions per block
     */
    public static Block generateNextBlock(
            StandaloneBlockchain chain, List<ECKey> accounts, int txCount) {

        Block block, parent = chain.getBestBlock();
        AionRepositoryImpl repo = chain.getRepository();
        List<AionTransaction> txs = generateTransactions(txCount, accounts, repo);

        long time = System.currentTimeMillis();
        block = chain.createNewMiningBlockInternal(parent, txs, true, time / 10000L).block;

        A0BlockHeader newBlockHeader =
            A0BlockHeader.Builder.newInstance()
                .withHeader((A0BlockHeader) block.getHeader())
                .withExtraData(String.valueOf(time).getBytes())
                .build();
        block.updateHeader(newBlockHeader);
        return block;
    }

    /**
     * @param chain blockchain implementation to be populated
     * @param parent the parent block for the newly generated block
     * @param accounts existing accounts
     * @param txCount maximum number of transactions per block
     * @implNote returns {@code null} if the parent block is not part of the chain
     */
    public static Block generateNewBlock(
            StandaloneBlockchain chain, Block parent, List<ECKey> accounts, int txCount) {
        if (!chain.getBlockStore().isBlockStored(parent.getHash(), parent.getNumber())) return null;

        Block block;
        AionRepositoryImpl repo = chain.getRepository();
        List<AionTransaction> txs;

        // generate transactions for correct root
        byte[] originalRoot = repo.getRoot();
        repo.syncToRoot(parent.getStateRoot());
        txs = generateTransactions(txCount, accounts, repo);
        repo.syncToRoot(originalRoot);

        long time = System.currentTimeMillis();
        block = chain.createNewMiningBlockInternal(parent, txs, true, time / 10000L).block;
        A0BlockHeader newBlockHeader =
            A0BlockHeader.Builder.newInstance()
                .withHeader((A0BlockHeader) block.getHeader())
                .withExtraData(String.valueOf(time).getBytes())
                .build();
        block.updateHeader(newBlockHeader);
        return block;
    }
}
