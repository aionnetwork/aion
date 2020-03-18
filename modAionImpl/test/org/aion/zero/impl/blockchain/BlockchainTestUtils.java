package org.aion.zero.impl.blockchain;

import java.io.IOException;
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
import org.aion.zero.impl.vm.contracts.ContractUtils;
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
    private static final long TEN_THOUSAND_MS = 10_000L;
    private static final long LIMIT_DEPLOY = 5_000_000L;
    private static final long LIMIT_CALL = 2_000_000L;

    public static final BigInteger MIN_SELF_STAKE = BigInteger.TEN.pow(21);

    public static List<ECKey> generateAccounts(int size) {
        List<ECKey> accs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            accs.add(ECKeyFac.inst().create());
        }
        return accs;
    }

    public static List<AionTransaction> generateTransactions(
            int maxSize, List<ECKey> accounts, AionRepositoryImpl repo) {
        return generateTransactions(maxSize, accounts, repo, repo.getBlockStore().getBestBlock());
    }

    public static List<AionTransaction> generateTransactions(int maxSize, List<ECKey> accounts, AionRepositoryImpl repository, Block block) {
        int size = rand.nextInt(maxSize);

        if (size == 0) {
            return Collections.emptyList();
        } else {
            AionRepositoryImpl repo = (AionRepositoryImpl) repository.getSnapshotTo(block.getStateRoot());
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

    public static Pair<Block, ImportResult> addMiningBlock(StandaloneBlockchain chain, Block parent, List<AionTransaction> txs) {
        return addMiningBlock(chain, parent, txs, System.currentTimeMillis(), new byte[0]);
    }

    private static Pair<Block, ImportResult> addMiningBlock(StandaloneBlockchain chain, Block parent, List<AionTransaction> txs, long time, byte[] extraData) {
        AionBlock block = chain.createNewMiningBlockInternal(parent, txs, true, time / TEN_THOUSAND_MS).block;

        A0BlockHeader newBlockHeader = A0BlockHeader.Builder.newInstance().withHeader(block.getHeader()).withExtraData(extraData).build();
        block.updateHeader(newBlockHeader);

        ImportResult result = chain.tryToConnect(block);

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
     * @param stakers existing accounts with balance on the chain used to generate random transactions; <b>will be used as stakers after the Unity fork</b>
     * @param stakingRegistryOwner existing account with balance; <b>will be used to deploy the StakerRegistry contract</b>
     * @param txCount maximum number of transactions per block
     */
    public static void generateRandomUnityChain(StandaloneBlockchain chain, TestResourceProvider resourceProvider, long blocks, int frequency, List<ECKey> stakers, ECKey stakingRegistryOwner, int txCount) {
        generateRandomUnityChain(chain, resourceProvider, blocks, frequency, stakers, Collections.emptyList(), stakingRegistryOwner, txCount);
    }

    /**
     * Generates a random chain with alternating mining and staking blocks after the unity fork is reached. The staking registry contract is deployed in the block before the fork. Stakers are registered in the Unity fork block.
     *
     * @param chain blockchain implementation to be populated
     * @param resourceProvider provides the implementations of the two AVMs; <b>must have both enabled</b>
     * @param blocks number of blocks to be added to the chain
     * @param frequency every multiple of frequency block will be on the main chain
     * @param stakerAccounts existing accounts with balance on the chain used to generate random transactions; <b>will be used as stakers after the Unity fork</b>
     * @param otherAccounts additional existing accounts with balance on the chain used to generate random transactions without staking;
     * @param stakingRegistryOwner existing account with balance; <b>will be used to deploy the StakerRegistry contract</b>
     * @param txCount maximum number of transactions per block
     */
    public static void generateRandomUnityChain(StandaloneBlockchain chain, TestResourceProvider resourceProvider, long blocks, int frequency, List<ECKey> stakerAccounts, List<ECKey> otherAccounts, ECKey stakingRegistryOwner, int txCount) {
        long seed = rand.nextLong();
        rand.setSeed(seed);
        System.out.println("Random seed used: " + seed);

        // ensure the stakingRegistryOwner is not among the stakers
        List<ECKey> stakers = new ArrayList<>(stakerAccounts);
        stakers.remove(stakingRegistryOwner);

        // Will produce random transactions on the chain.
        List<ECKey> users = new ArrayList<>(stakerAccounts);
        stakers.addAll(otherAccounts);

        if (stakingRegistryOwner == null || stakers == null || stakers.isEmpty()) {
            throw new IllegalStateException("Please provide the accounts required to build a Unity chain.");
        }

        Block parent, block, mainChain;
        mainChain = chain.getBestBlock();

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
                    txs.add(registerStaker(resourceProvider.factoryForVersion1, key, MIN_SELF_STAKE, repo.getNonce(new AionAddress(key.getAddress())), contract));
                }
            } else {
                txs = txCount > 0 ? generateTransactions(txCount, users, repo) : Collections.emptyList();
            }
            // get back to current root
            repo.syncToRoot(originalRoot);

            if (chain.forkUtility.isUnityForkActive(nextBlockNumber)) {
                // create and import post-unity block
                Pair<Block, ImportResult> importPair;

                if (parent.getHeader().getSealType() == BlockSealType.SEAL_POS_BLOCK) {
                    importPair = addMiningBlock(chain, parent, txs, time, String.valueOf(i).getBytes());
                } else {
                    importPair = addStakingBlock(chain, parent, txs, stakers.get(rand.nextInt(stakers.size())));
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

    /**
     * Generates a single mining block containing the staking registry contract deployment on top of the given chain.
     * Does not import the block.
     *
     * @param chain blockchain implementation to be extended with the new block
     * @param resourceProvider provides the implementations of the two AVMs; <b>must have both enabled</b>
     * @param stakingRegistryOwner existing account with balance; <b>will be used to deploy the StakerRegistry contract</b>
     */
    public static Block generateNextMiningBlockWithStakerRegistry(StandaloneBlockchain chain, Block parent, TestResourceProvider resourceProvider, ECKey stakingRegistryOwner) {
        if (stakingRegistryOwner == null) {
            throw new IllegalStateException("Please provide the required account to build the requested block.");
        }
        AionRepositoryImpl repo = chain.getRepository();

        // generate transactions for correct root
        byte[] originalRoot = repo.getRoot();
        repo.syncToRoot(parent.getStateRoot());
        // transaction to deploy staking contract
        AionTransaction tx = deployStakingContract(resourceProvider.factoryForVersion1, stakingRegistryOwner, repo.getNonce(new AionAddress(stakingRegistryOwner.getAddress())));
        // get back to current root
        repo.syncToRoot(originalRoot);

        return chain.createNewMiningBlockInternal(parent, List.of(tx), true, System.currentTimeMillis() / TEN_THOUSAND_MS).block;
    }

    /**
     * Generates a single mining block containing transactions to register stakers on top of the given chain.
     * Does not import the block.
     *
     * @param chain blockchain implementation to be extended with the new block
     * @param parent the parent for the block to be generated
     * @param resourceProvider provides the implementations of the two AVMs; <b>must have both enabled</b>
     * @param stakers existing accounts with balance on the chain used to generate random transactions; <b>will be used as stakers after the Unity fork</b>
     * @param amount the initial staking amount
     * @return the generated block
     */
    public static Block generateNextMiningBlockWithStakers(StandaloneBlockchain chain, Block parent, TestResourceProvider resourceProvider, List<ECKey> stakers, BigInteger amount) {
        List<AionTransaction> transactions = generateStakerRegistrationTransactions(chain, parent, resourceProvider, stakers, amount);
        return generateNextMiningBlock(chain, parent, transactions);
    }

    /**
     * Generates a single mining block (containing the given transactions) on top of the given chain.
     * Does not import the block.
     *
     * @param chain blockchain implementation to be extended with the new block
     * @param parent the parent for the block to be generated
     * @param transactions list of transactions to be included in the block
     * @return the generated block
     */
    public static Block generateNextMiningBlock(StandaloneBlockchain chain, Block parent, List<AionTransaction> transactions) {
        return chain.createNewMiningBlockInternal(parent, transactions, true, System.currentTimeMillis() / TEN_THOUSAND_MS).block;
    }

    /**
     * Generates a single mining block (containing the given transactions) on top of the given chain.
     * Does not import the block.
     *
     * @param chain blockchain implementation to be extended with the new block
     * @param parent the parent for the block to be generated
     * @param transactions list of transactions to be included in the block
     * @param timestamp the desired block timestamp
     * @return the generated block
     */
    public static Block generateNextMiningBlock(StandaloneBlockchain chain, Block parent, List<AionTransaction> transactions, long timestamp) {
        return chain.createNewMiningBlockInternal(parent, transactions, true, timestamp).block;
    }

    /**
     * Generates a single staking block containing transactions to register stakers on top of the given chain.
     * Does not import the block.
     *
     * @param chain blockchain implementation to be extended with the new block
     * @param parent the parent for the block to be generated
     * @param resourceProvider provides the implementations of the two AVMs; <b>must have both enabled</b>
     * @param stakers existing accounts with balance on the chain used to generate random transactions; <b>will be used as stakers after the Unity fork</b>
     * @param amount the initial staking amount
     * @param producer the staker key that will create and sign the block
     * @return the generated block
     */
    public static Block generateNextStakingBlockWithStakers(StandaloneBlockchain chain, Block parent, TestResourceProvider resourceProvider, List<ECKey> stakers, BigInteger amount, ECKey producer) {
        List<AionTransaction> transactions = generateStakerRegistrationTransactions(chain, parent, resourceProvider, stakers, amount);
        return generateNextStakingBlock(chain, parent, transactions, producer);
    }

    /**
     * Generates a single staking block (containing the given transactions) on top of the given chain.
     * Does not import the block.
     *
     * @param chain blockchain implementation to be extended with the new block
     * @param parent the parent for the block to be generated
     * @param transactions list of transactions to be included in the block
     * @param producer the staker key that will create and sign the block
     * @return the generated block
     */
    public static Block generateNextStakingBlock(StandaloneBlockchain chain, Block parent, List<AionTransaction> transactions, ECKey producer) {
        // get the parent seed
        byte[] parentSeed = chain.forkUtility.isUnityForkBlock(parent.getNumber())
                ? StakingBlockHeader.GENESIS_SEED
                : ((StakingBlock) chain.getBlockByHash(parent.getParentHash())).getSeed();

        // create staking block
        byte[] newSeed = producer.sign(parentSeed).getSignature();
        StakingBlock block = chain.createStakingBlockTemplate(parent, transactions, producer.getPubKey(), newSeed, new AionAddress(producer.getAddress()).toByteArray());

        if (block == null) {
            return null;
        }

        // seal staking block
        byte[] mineHashSig = producer.sign(block.getHeader().getMineHash()).getSignature();
        block.seal(mineHashSig, producer.getPubKey());

        return block;
    }

    private static AionTransaction deployStakingContract(IAvmResourceFactory avmFactory, ECKey owner, BigInteger nonce) {
        byte[] jar = avmFactory.newContractFactory().getDeploymentBytes(AvmContract.UNITY_STAKER_REGISTRY);
        return AionTransaction.create(
                owner,
                nonce.toByteArray(),
                null,
                new byte[0],
                jar,
                LIMIT_DEPLOY,
                NRG_PRICE,
                TransactionTypes.AVM_CREATE_CODE,
                null);
    }

    /**
     * Generates a list of transactions to register stakers.
     *
     * @param chain blockchain implementation to be used in generating the transactions
     * @param parent the block providing the state on top of which the transactions should be generated
     * @param resourceProvider provides the implementations of the two AVMs; <b>must have both enabled</b>
     * @param stakers existing accounts with balance on the chain used to generate random transactions; <b>will be used as stakers after the Unity fork</b>
     * @param amount the initial staking amount
     * @return a list of transactions for staker registrations containing one entry for each given staker
     */
    public static List<AionTransaction> generateStakerRegistrationTransactions(StandaloneBlockchain chain, Block parent, TestResourceProvider resourceProvider, List<ECKey> stakers, BigInteger amount) {
        if (stakers == null || stakers.isEmpty()) {
            throw new IllegalStateException("Please provide the accounts required to build a Unity chain.");
        }

        List<AionTransaction> txs = new ArrayList<>();
        AionRepositoryImpl repo = chain.getRepository();

        // generate transactions for correct root
        byte[] originalRoot = repo.getRoot();
        repo.syncToRoot(parent.getStateRoot());
        // get the staking contract address from the staking genesis
        AionAddress contract = chain.getGenesis().getStakingContractAddress();
        for (ECKey key : stakers) {
            // register stakers
            txs.add(registerStaker(chain.forkUtility.isUnityForkActive(parent.getNumber() + 1) ? resourceProvider.factoryForVersion2 : resourceProvider.factoryForVersion1, key, amount, repo.getNonce(new AionAddress(key.getAddress())), contract));
        }
        // get back to current root
        repo.syncToRoot(originalRoot);

        return txs;
    }

    private static AionTransaction registerStaker(IAvmResourceFactory avmFactory, ECKey caller, BigInteger amount, BigInteger nonce, AionAddress contract) {
        AionAddress callerAddress = new AionAddress(caller.getAddress());
        byte[] callBytes = avmFactory.newStreamingEncoder()
                                     .encodeOneString("registerStaker")
                                     .encodeOneAddress(callerAddress) // staker address
                                     .encodeOneAddress(callerAddress) // signing address
                                     .encodeOneAddress(callerAddress) // coinbase address
                                     .getEncoding();
        return AionTransaction.create(
                caller,
                nonce.toByteArray(),
                contract,
                amount.toByteArray(),
                callBytes,
                LIMIT_CALL,
                NRG_PRICE,
                TransactionTypes.DEFAULT,
                null);
    }

    /**
     * Generates a list of transactions to increase the stake of the given stakers.
     *
     * @param chain blockchain implementation to be used in generating the transactions
     * @param parent the block providing the state on top of which the transactions should be generated
     * @param resourceProvider provides the implementations of the two AVMs; <b>must have both enabled</b>
     * @param stakers existing accounts with balance on the chain used to generate random transactions; <b>will be used as stakers after the Unity fork</b>
     * @param amount the amount to bound
     * @return a list of transactions for increasing stake containing one entry for each given staker
     */
    public static List<AionTransaction> generateIncreaseStakeTransactions(StandaloneBlockchain chain, Block parent, TestResourceProvider resourceProvider, List<ECKey> stakers, BigInteger amount) {
        if (stakers == null || stakers.isEmpty()) {
            throw new IllegalStateException("Please provide the accounts required to build a Unity chain.");
        }

        List<AionTransaction> txs = new ArrayList<>();
        AionRepositoryImpl repo = chain.getRepository();

        // generate transactions for correct root
        byte[] originalRoot = repo.getRoot();
        repo.syncToRoot(parent.getStateRoot());
        // get the staking contract address from the staking genesis
        AionAddress contract = chain.getGenesis().getStakingContractAddress();
        for (ECKey key : stakers) {
            txs.add(increaseStake(chain.forkUtility.isUnityForkActive(parent.getNumber() + 1) ? resourceProvider.factoryForVersion2 : resourceProvider.factoryForVersion1, key, amount, repo.getNonce(new AionAddress(key.getAddress())), contract));
        }
        // get back to current root
        repo.syncToRoot(originalRoot);

        return txs;
    }

    private static AionTransaction increaseStake(IAvmResourceFactory avmFactory, ECKey caller, BigInteger amount, BigInteger nonce, AionAddress contract) {
        AionAddress callerAddress = new AionAddress(caller.getAddress());
        byte[] callBytes = avmFactory.newStreamingEncoder()
                                     .encodeOneString("bond")
                                     .encodeOneAddress(callerAddress) // staker address
                                     .getEncoding();
        return AionTransaction.create(
                caller,
                nonce.toByteArray(),
                contract,
                amount.toByteArray(),
                callBytes,
                LIMIT_CALL,
                NRG_PRICE,
                TransactionTypes.DEFAULT,
                null);
    }

    /**
     * Generates a list of transactions to decrease the stake of the given stakers.
     *
     * @param chain blockchain implementation to be used in generating the transactions
     * @param parent the block providing the state on top of which the transactions should be generated
     * @param resourceProvider provides the implementations of the two AVMs; <b>must have both enabled</b>
     * @param stakers existing accounts with balance on the chain used to generate random transactions; <b>will be used as stakers after the Unity fork</b>
     * @param amount the amount to unbound
     * @param fee the fee offered
     * @return a list of transactions for decreasing stake containing one entry for each given staker
     */
    public static List<AionTransaction> generateDecreaseStakeTransactions(StandaloneBlockchain chain, Block parent, TestResourceProvider resourceProvider, List<ECKey> stakers, BigInteger amount, BigInteger fee) {
        if (stakers == null || stakers.isEmpty()) {
            throw new IllegalStateException("Please provide the accounts required to build a Unity chain.");
        }

        List<AionTransaction> txs = new ArrayList<>();
        AionRepositoryImpl repo = chain.getRepository();

        // generate transactions for correct root
        byte[] originalRoot = repo.getRoot();
        repo.syncToRoot(parent.getStateRoot());
        // get the staking contract address from the staking genesis
        AionAddress contract = chain.getGenesis().getStakingContractAddress();
        for (ECKey key : stakers) {
            txs.add(decreaseStake(chain.forkUtility.isUnityForkActive(parent.getNumber() + 1) ? resourceProvider.factoryForVersion2 : resourceProvider.factoryForVersion1, key, amount, fee, repo.getNonce(new AionAddress(key.getAddress())), contract));
        }
        // get back to current root
        repo.syncToRoot(originalRoot);

        return txs;
    }

    private static AionTransaction decreaseStake(IAvmResourceFactory avmFactory, ECKey caller, BigInteger amount, BigInteger fee, BigInteger nonce, AionAddress contract) {
        AionAddress callerAddress = new AionAddress(caller.getAddress());
        byte[] callBytes = avmFactory.newStreamingEncoder()
                                     .encodeOneString("unbond")
                                     .encodeOneAddress(callerAddress) // staker address
                                     .encodeOneBigInteger(amount) // amount to un-stake
                                     .encodeOneBigInteger(fee) // fee
                                     .getEncoding();
        return AionTransaction.create(
                caller,
                nonce.toByteArray(),
                contract,
                BigInteger.ZERO.toByteArray(),
                callBytes,
                LIMIT_CALL,
                NRG_PRICE,
                TransactionTypes.DEFAULT,
                null);
    }

    /**
     * Generates a list of transactions to transfer the stake of the given stakers interpreted as pairs of (from, to) for the transfers.
     *
     * @param chain blockchain implementation to be used in generating the transactions
     * @param parent the block providing the state on top of which the transactions should be generated
     * @param resourceProvider provides the implementations of the two AVMs; <b>must have both enabled</b>
     * @param stakers existing accounts with balance on the chain used to generate random transactions; <b>will be used as stakers after the Unity fork</b>
     * @param amount the amount to transfer
     * @param fee the fee offered
     * @return a list of transactions for transfering stake containing one entry for each given pair of staker
     */
    public static List<AionTransaction> generateTransferStakeTransactions(StandaloneBlockchain chain, Block parent, TestResourceProvider resourceProvider, List<ECKey> stakers, BigInteger amount, BigInteger fee) {
        if (stakers == null || stakers.isEmpty() || stakers.size() % 2 != 0) {
            throw new IllegalStateException("Please provide the accounts required to build a Unity chain.");
        }

        List<AionTransaction> txs = new ArrayList<>();
        AionRepositoryImpl repo = chain.getRepository();

        // generate transactions for correct root
        byte[] originalRoot = repo.getRoot();
        repo.syncToRoot(parent.getStateRoot());
        // get the staking contract address from the staking genesis
        AionAddress contract = chain.getGenesis().getStakingContractAddress();
        for (int i = 0; i < stakers.size(); i += 2) {
            ECKey from = stakers.get(i);
            ECKey to = stakers.get(i + 1);
            txs.add(transferStake(chain.forkUtility.isUnityForkActive(parent.getNumber() + 1) ? resourceProvider.factoryForVersion2 : resourceProvider.factoryForVersion1,
                                  from, to, amount, fee, repo.getNonce(new AionAddress(from.getAddress())), contract));
        }
        // get back to current root
        repo.syncToRoot(originalRoot);

        return txs;
    }

    private static AionTransaction transferStake(IAvmResourceFactory avmFactory, ECKey from, ECKey to, BigInteger amount, BigInteger fee, BigInteger nonceOfFromAccount, AionAddress contract) {
        AionAddress fromAddress = new AionAddress(from.getAddress());
        AionAddress toAddress = new AionAddress(to.getAddress());
        byte[] callBytes = avmFactory.newStreamingEncoder()
                                     .encodeOneString("transferStake")
                                     .encodeOneAddress(fromAddress) // from address
                                     .encodeOneAddress(toAddress) // to address
                                     .encodeOneBigInteger(amount) // amount to transfer
                                     .encodeOneBigInteger(fee) // fee
                                     .getEncoding();
        return AionTransaction.create(
                from,
                nonceOfFromAccount.toByteArray(),
                contract,
                BigInteger.ZERO.toByteArray(),
                callBytes,
                LIMIT_CALL,
                NRG_PRICE,
                TransactionTypes.DEFAULT,
                null);
    }

    /**
     * Generates a transaction to finalize a stake transfer.
     *
     * @param chain blockchain implementation to be used in generating the transactions
     * @param parent the block providing the state on top of which the transactions should be generated
     * @param resourceProvider provides the implementations of the two AVMs; <b>must have both enabled</b>
     * @param staker existing account that made a stake transfer
     * @param transferId the identifier of the transfer to be finalized
     * @return the transaction to finalize the stake transfer
     */
    public static AionTransaction generateTransferFinalizeTransactions(StandaloneBlockchain chain, Block parent, TestResourceProvider resourceProvider, ECKey staker, long transferId) {
        if (staker == null) {
            throw new IllegalStateException("Please provide the accounts required to build a Unity chain.");
        }

        AionRepositoryImpl repo = chain.getRepository();

        // generate transactions for correct root
        byte[] originalRoot = repo.getRoot();
        repo.syncToRoot(parent.getStateRoot());
        // get the staking contract address from the staking genesis
        AionAddress contract = chain.getGenesis().getStakingContractAddress();
        AionTransaction tx = finalizeTransfer(chain.forkUtility.isUnityForkActive(parent.getNumber() + 1) ? resourceProvider.factoryForVersion2 : resourceProvider.factoryForVersion1,
                                              staker, transferId, repo.getNonce(new AionAddress(staker.getAddress())), contract);
        // get back to current root
        repo.syncToRoot(originalRoot);

        return tx;
    }

    private static AionTransaction finalizeTransfer(IAvmResourceFactory avmFactory, ECKey from, long transferId, BigInteger nonceOfFromAccount, AionAddress contract) {
        byte[] callBytes = avmFactory.newStreamingEncoder()
                                     .encodeOneString("finalizeTransfer")
                                     .encodeOneLong(transferId) // transfer identifier (sequential values starting at 0)
                                     .getEncoding();
        return AionTransaction.create(
                from,
                nonceOfFromAccount.toByteArray(),
                contract,
                BigInteger.ZERO.toByteArray(),
                callBytes,
                LIMIT_CALL,
                NRG_PRICE,
                TransactionTypes.DEFAULT,
                null);
    }

    public static Pair<Block, ImportResult> addStakingBlock(StandaloneBlockchain chain, Block parent, List<AionTransaction> txs, ECKey key) {
        byte[] parentSeed = chain.forkUtility.isUnityForkBlock(parent.getNumber()) ? StakingBlockHeader.GENESIS_SEED : ((StakingBlock) chain.getBlockByHash(parent.getParentHash())).getSeed();
        byte[] newSeed = key.sign(parentSeed).getSignature();
        StakingBlock block = chain.createStakingBlockTemplate(parent, txs, key.getPubKey(), newSeed, new AionAddress(key.getAddress()).toByteArray());

        if (block == null) {
            return null;
        }

        byte[] mineHashSig = key.sign(block.getHeader().getMineHash()).getSignature();
        block.seal(mineHashSig, key.getPubKey());

        ImportResult result = chain.tryToConnect(block);

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
        block = chain.createNewMiningBlockInternal(parent, txs, true, time / TEN_THOUSAND_MS).block;

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
        if (!chain.isBlockStored(parent.getHash(), parent.getNumber())) return null;

        Block block;
        AionRepositoryImpl repo = chain.getRepository();
        List<AionTransaction> txs;

        // generate transactions for correct root
        byte[] originalRoot = repo.getRoot();
        repo.syncToRoot(parent.getStateRoot());
        txs = generateTransactions(txCount, accounts, repo);
        repo.syncToRoot(originalRoot);

        long time = System.currentTimeMillis();
        block = chain.createNewMiningBlockInternal(parent, txs, true, time / TEN_THOUSAND_MS).block;
        A0BlockHeader newBlockHeader =
            A0BlockHeader.Builder.newInstance()
                .withHeader((A0BlockHeader) block.getHeader())
                .withExtraData(String.valueOf(time).getBytes())
                .build();
        block.updateHeader(newBlockHeader);
        return block;
    }

    public static AionTransaction deployAvmContractTransaction(AvmContract contract, IAvmResourceFactory avmFactory, ECKey owner, BigInteger nonce) {
        byte[] jar = avmFactory.newContractFactory().getDeploymentBytes(contract);
        return AionTransaction.create(
                owner,
                nonce.toByteArray(),
                null,
                new byte[0],
                jar,
                LIMIT_DEPLOY,
                NRG_PRICE,
                TransactionTypes.AVM_CREATE_CODE,
                null);
    }

    public static AionTransaction putToLargeStorageTransaction(IAvmResourceFactory avmFactory, ECKey caller, byte[] key, byte[] value, BigInteger nonce, AionAddress contract) {
        byte[] callBytes = avmFactory.newStreamingEncoder()
                .encodeOneString("putStorage")
                .encodeOneByteArray(key)
                .encodeOneByteArray(value)
                .getEncoding();
        return AionTransaction.create(
                caller,
                nonce.toByteArray(),
                contract,
                BigInteger.ZERO.toByteArray(),
                callBytes,
                LIMIT_CALL,
                NRG_PRICE,
                TransactionTypes.DEFAULT,
                null);
    }

    public static AionTransaction getFromLargeStorageTransaction(IAvmResourceFactory avmFactory, ECKey caller, byte[] key, BigInteger nonce, AionAddress contract) {
        byte[] callBytes = avmFactory.newStreamingEncoder()
                .encodeOneString("getStorage")
                .encodeOneByteArray(key)
                .getEncoding();
        return AionTransaction.create(
                caller,
                nonce.toByteArray(),
                contract,
                BigInteger.ZERO.toByteArray(),
                callBytes,
                LIMIT_CALL,
                NRG_PRICE,
                TransactionTypes.DEFAULT,
                null);
    }

    public static AionTransaction deployFvmTickerContractTransaction(ECKey owner, BigInteger nonce) throws IOException {
        byte[] contractBytes = ContractUtils.getContractDeployer("Ticker.sol", "Ticker");

        return AionTransaction.create(
                owner,
                nonce.toByteArray(),
                null,
                new byte[0],
                contractBytes,
                LIMIT_DEPLOY,
                NRG_PRICE,
                TransactionTypes.DEFAULT, null);
    }
}
