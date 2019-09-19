package org.aion.zero.impl.types;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.aion.base.ConstantUtil;
import org.aion.base.AccountState;
import org.aion.zero.impl.trie.SecureTrie;
import org.aion.zero.impl.trie.Trie;
import org.aion.util.types.DataWord;
import org.aion.precompiled.ContractInfo;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.AddressUtils;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.db.AionContractDetailsImpl;

public class AionGenesis extends AionBlock {
    /**
     * Make this value constant for now, in the future we may move this to a more configuration
     * position, this indicates the address at which the storage rows are to be stored
     */
    private static final AionAddress NETWORK_BALANCE_ADDRESS =
            ContractInfo.TOTAL_CURRENCY.contractAddress;

    /**
     * Aion Genesis Constants
     *
     * <p>These are constants that will be used by {@link AionGenesis} to be overidden by the
     * default genesis.json file. If parameters are not overridden the defaults should fallback to
     * the values defined here
     */

    /**
     * Corresponds to {@link A0BlockHeader#getParentHash()}, for purposes of the genesis
     * blocks, this value could reference something arbitrary. We have chosen to arbitrarily set it
     * to a silly phrase.
     */
    static final byte[] GENESIS_PARENT_HASH =
            ByteUtil.hexStringToBytes(
                    "0000000000000000000000000000000000000000000000000000000000000000");

    /**
     * Corresponds to {@link A0BlockHeader#getCoinbase()} that mined the first block. For
     * fairness, the address is set to an address that is not ever to be used
     */
    static final AionAddress GENESIS_COINBASE = AddressUtils.ZERO_ADDRESS;

    /**
     * Corresponds to {@link A0BlockHeader#getLogsBloom()} indicates the logsBloom of the
     * genesis block, because no transactions are included in this block. It defaults to empty.
     */
    static final byte[] GENESIS_LOGSBLOOM = new byte[256];

    /**
     * Corresponds to {@link A0BlockHeader#getDifficulty()} and {@link
     * A0BlockHeader#getDifficultyBI()}. This value represents the initial difficulty of the
     * network, a very important value to set. It is not necessarily important to correctly set this
     * value as it will rise to based on the network hashing power.
     *
     * @implNote This value is set based on a number derived from a very optimistic (upper bound)
     *     estimate of the projected hashrate. The predicted difficulty number is: 189625700 (189M).
     *     It is unrealistic to expect this amount during the test-net phase, therefore divide this
     *     number by 100, this roughly accounts for ~440 GPUs, something attainable by the founation
     *     on test-net launch.
     * @implNote Updated April 7th, 2018. In light of changes to the original plan this value has
     *     been reduced to 1024 based on the heuristic data from the QA1 TestNet
     */
    static final byte[] GENESIS_DIFFICULTY =
            ByteUtil.bigIntegerToBytes(BigInteger.valueOf(1024));

    /**
     * Corresponds to {@link A0BlockHeader#getNumber()} the number. This is pretty self
     * explanatory.
     */
    private static final long GENESIS_NUMBER = 0;

    /**
     * Corresponds to {@link A0BlockHeader#getTimestamp()} the timestamp that the block was
     * forged. In terms of the genesis, we arbitrarily set it to 0.
     */
    static final long GENESIS_TIMESTAMP = 0;

    /**
     * Corresponds to {@link A0BlockHeader#getNonce()} nonce of the block, we arbitrarily set
     * this to 0 for now
     */
    private static final byte[] GENESIS_NONCE = new byte[32];

    /**
     * Corresponds to {@link A0BlockHeader#getEnergyLimit()} sets the initial energy limit. We will
     * set this to an accept (but low) value to ensure the network is not strained from the start.
     *
     * <p>Previously, this was set to a low value, but for the test net we would like to stress the
     * network, thus the default value is being changed from {@code 1000000 -> 10000000}. This gives
     * us the ability to deploy more complicated contracts.
     *
     * @see <a href=
     *     "https://blog.ethereum.org/2016/10/31/uncle-rate-transaction-fee-analysis">fee-analysis</a>
     */
    static final long GENESIS_ENERGY_LIMIT = 10000000;

    /** Corresponds to {@link AionGenesis#premine} default premined accounts */
    static final Map<AionAddress, AccountState> GENESIS_PREMINE;

    private static final Map<Integer, BigInteger> GENESIS_NETWORK_BALANCE;

    static {
        // leaving because we may want to add accounts later
        GENESIS_PREMINE = new HashMap<>();

        GENESIS_NETWORK_BALANCE = new HashMap<>();
        GENESIS_NETWORK_BALANCE.put(0, new BigInteger("465934586660000000000000000"));
    }

    // END DEFAULT VALUES

    private Map<Integer, BigInteger> networkBalances = new HashMap<>();

    /**
     * Premined accounts, these are accounts that have initial state and value. Because of the
     * bridging mechanism, we arbitrarily store all ERC20 token values as coins on an account at
     * address 0x100 (256).
     */
    private Map<AionAddress, AccountState> premine = new HashMap<>();

    /**
     * The virtual staking block, which is the sealParent of the first PoS block is built, and the
     * sealAntiparent of all PoW blocks until the first PoS block.
     */
    private GenesisStakingBlock genesisStakingBlock;

    private AionAddress stakingContractAddress;

    // TODO: verify whether setting the solution to null is okay
    // TODO: set energyLimit to a correct value (after genesis loader is
    // completed)
    public AionGenesis(
            byte[] parentHash,
            AionAddress coinbase,
            byte[] logsBloom,
            byte[] difficulty,
            long number,
            long timestamp,
            byte[] extraData,
            byte[] nonce,
            long energyLimit,
            byte[] stateRoot,
            byte[] receiptRoot,
            byte[] txTrieRoot) {
        super(
                parentHash,
                coinbase,
                logsBloom,
                difficulty,
                number,
                timestamp,
                extraData,
                nonce,
                energyLimit);

        updateTransactionAndState(
                new ArrayList<>(), txTrieRoot, stateRoot, logsBloom, receiptRoot, 0L);
    }

    public Map<AionAddress, AccountState> getPremine() {
        return premine;
    }

    private void setPremine(Map<AionAddress, AccountState> premine) {
        this.premine = premine;
    }

    private void setNetworkBalance(Map<Integer, BigInteger> networkBalances) {
        this.networkBalances = networkBalances;
    }

    private void setStakingContractAddress(AionAddress address) {
        stakingContractAddress = address;
    }

    public Map<Integer, BigInteger> getNetworkBalances() {
        return this.networkBalances;
    }

    public GenesisStakingBlock getGenesisStakingBlock() {
        return this.genesisStakingBlock;
    }

    private void setGenesisStakingBlock(GenesisStakingBlock genesisStakingBlock) {
        this.genesisStakingBlock = genesisStakingBlock;
    }

    /**
     * ChainID is defined to be the last two bytes of the extra data field in the header of a
     * genesis block.
     *
     * <p>Note that this distinction is <b>ONLY</b> made for the genesis block, in any other block
     * extraData field bits are not interpreted in any way.
     */
    public int getChainId() {
        return ByteBuffer.wrap(this.getExtraData()).position(30).getShort() & 0xFFFF;
    }

    /**
     * Genesis will fallback to a set of default values given that the loader does not override
     * them, note that because a block by default is not immutable we are required here to create a
     * new instance of the genesis each time the builder produces a new block.
     *
     * <p>Does not assume anything about the input data, will do the necessary checks to assert the
     * specs. But will not do null checks, therefore it is up to the caller to ensure input values
     * are non null
     *
     * <p>This class makes no assumptions about thread-safety.
     */
    public static class Builder {
        protected byte[] parentHash;
        protected AionAddress coinbase;
        protected byte[] logsBloom;
        protected byte[] difficulty;
        protected Long number;
        protected Long timestamp;
        protected byte[] nonce;
        protected Long energyLimit;

        /**
         * With proposed changes to chainId, the extraData field is now segmented into the following
         * [30-byte FREE | 2-byte chainId (uint16)]
         */
        int chainId;

        Map<Integer, BigInteger> networkBalance;
        Map<AionAddress, AccountState> premined;

        protected AionAddress stakingContractAddress;
        protected BigInteger genesisStakingDifficulty;

        public Builder withParentHash(final byte[] parentHash) {
            if (parentHash == null) {
                throw new NullPointerException("parentHash is null");
            }

            if (parentHash.length != 32) {
                throw new IllegalArgumentException("Invalid parentHash length");
            }
            this.parentHash = parentHash;
            return this;
        }

        public Builder withCoinbase(final AionAddress coinbase) {
            if (coinbase == null) {
                throw new NullPointerException("coinbase is null");
            }
            this.coinbase = coinbase;
            return this;
        }

        public Builder withDifficulty(final byte[] difficulty) {
            if (difficulty == null) {
                throw new NullPointerException();
            }

            if (difficulty.length > 16) {
                throw new IllegalArgumentException("Invalid difficulty length");
            }

            this.difficulty = difficulty;
            return this;
        }

        public Builder withNumber(final long number) {
            if (number < 0) throw new IllegalArgumentException("number cannot be negative");

            this.number = number;
            return this;
        }

        public Builder withTimestamp(final long timestamp) {
            if (timestamp < 0) throw new IllegalArgumentException("timestamp cannot be negative");

            this.timestamp = timestamp;
            return this;
        }

        public Builder withNonce(final byte[] nonce) {
            if (nonce == null) {
                throw new NullPointerException("nonce is null");
            }

            if (nonce.length != 32) {
                throw new IllegalArgumentException("Invalid nonce length");
            }

            this.nonce = nonce;
            return this;
        }

        public Builder withEnergyLimit(final long energyLimit) {
            if (energyLimit < 0)
                throw new IllegalArgumentException("energyLimit cannot be negative");

            this.energyLimit = energyLimit;
            return this;
        }

        public Builder withChainId(final int chainId) {
            if (chainId < 0) throw new IllegalArgumentException("chainId cannot be negative");

            if (chainId > 0xFFFF)
                throw new IllegalArgumentException("chainId cannot be larger than 0xFFFF");

            this.chainId = chainId;
            return this;
        }

        public Builder addPreminedAccount(final AionAddress address, final AccountState state) {
            if (address == null) {
                throw new NullPointerException("address is null");
            }

            if (state == null) {
                throw new NullPointerException("state is null");
            }

            if (this.premined == null) {
                this.premined = new HashMap<>();
            }

            if (this.premined.get(address) != null)
                throw new IllegalArgumentException("duplicate premined address");

            this.premined.put(address, state);
            return this;
        }

        public Builder addNetworkBalance(Integer chainId, BigInteger balance) {
            if (chainId == null) {
                throw new NullPointerException("chain ID is null");
            }

            if (balance == null) {
                throw new NullPointerException("balance is null");
            }

            if (chainId < 0)
                throw new IllegalArgumentException(
                        "networkBalance chainId cannot be null or negative");

            if (balance.signum() < 0)
                throw new IllegalArgumentException(
                        "networkBalance balance cannot be null or negative");

            if (this.networkBalance == null) this.networkBalance = new HashMap<>();

            if (chainId > 255) throw new IllegalArgumentException("chainId cannot exceed 255");

            if (this.networkBalance.get(chainId) != null)
                throw new IllegalArgumentException("duplicate chainId");

            this.networkBalance.put(chainId, balance);
            return this;
        }

        public Builder setStakingContractAddress(AionAddress address) {
            if (address == null) {
                throw new NullPointerException("staking contract address is null");
            }
            this.stakingContractAddress = address;
            return this;
        }

        public Builder setGenesisStakingDifficulty(BigInteger difficulty) {
            if (difficulty == null) {
                throw new NullPointerException("genesisStakingDifficulty is null");
            }
            this.genesisStakingDifficulty = difficulty;
            return this;
        }

        /**
         * Build the genesis block, after parameters have been set. Defaults back to default genesis
         * values if parameters are not specified.
         */
        public AionGenesis build() {
            return build(false);
        }

        public AionGenesis buildForTest() {
            return build(true);
        }

        private AionGenesis build(boolean buildForTest) {
            if (this.parentHash == null) this.parentHash = GENESIS_PARENT_HASH;

            if (this.coinbase == null) this.coinbase = GENESIS_COINBASE;

            if (this.difficulty == null) this.difficulty = GENESIS_DIFFICULTY;

            if (this.number == null) this.number = GENESIS_NUMBER;

            if (this.timestamp == null) this.timestamp = GENESIS_TIMESTAMP;

            if (this.nonce == null) this.nonce = GENESIS_NONCE;

            if (this.energyLimit == null) this.energyLimit = GENESIS_ENERGY_LIMIT;

            if (this.premined == null) this.premined = GENESIS_PREMINE;

            if (this.networkBalance == null) this.networkBalance = GENESIS_NETWORK_BALANCE;

            if (logsBloom == null) logsBloom = GENESIS_LOGSBLOOM;

            byte[] extraData = generateExtraData(this.chainId);

            byte[] rootHash = generateRootHash();

            AionGenesis genesis =
                    new AionGenesis(
                            this.parentHash,
                            this.coinbase,
                            this.logsBloom,
                            this.difficulty,
                            this.number,
                            this.timestamp,
                            extraData,
                            this.nonce,
                            this.energyLimit,
                            rootHash,
                            ConstantUtil.EMPTY_TRIE_HASH,
                            ConstantUtil.EMPTY_TRIE_HASH);

            // temporary solution, so as not to disrupt the constructors
            genesis.setPremine(this.premined);
            genesis.setNetworkBalance(this.networkBalance);

            BigInteger genesisStakingDifficulty =
                    buildForTest
                            ? BigInteger.valueOf(2_000_000_000L)
                            : this.genesisStakingDifficulty;

            if (genesisStakingDifficulty == null) {
                throw new NullPointerException(
                        "The genesisStakingDifficulty setting is null or incorrect, please check your genesis.json in the executing network folder");
            }

            GenesisStakingBlock genesisStakingBlock =
                new GenesisStakingBlock(extraData, genesisStakingDifficulty);
            genesis.setGenesisStakingBlock(genesisStakingBlock);
            genesis.setAntiparentHash(genesisStakingBlock.getHash());

            BigInteger miningDifficulty = genesis.getDifficultyBI();
            genesis.setMiningDifficulty(miningDifficulty);
            genesis.setStakingDifficulty(BigInteger.ONE);
            genesis.setCumulativeDifficulty(miningDifficulty);
            genesis.setStakingContractAddress(this.stakingContractAddress);
            return genesis;
        }

        byte[] generateRootHash() {
            Trie worldTrie = new SecureTrie(null);
            AionContractDetailsImpl networkBalanceStorage = new AionContractDetailsImpl();

            for (Map.Entry<Integer, BigInteger> entry : this.networkBalance.entrySet()) {
                // we assume there are no deletions in the genesis
                networkBalanceStorage.put(
                        new DataWord(entry.getKey()).toWrapper(),
                        wrapValueForPut(new DataWord(entry.getValue())));
            }
            byte[] networkBalanceStorageHash = networkBalanceStorage.getStorageHash();

            AccountState networkBalanceAccount = new AccountState();
            networkBalanceAccount.setStateRoot(networkBalanceStorageHash);

            worldTrie.update(
                    NETWORK_BALANCE_ADDRESS.toByteArray(), networkBalanceAccount.getEncoded());

            // update predefined accounts
            for (Map.Entry<AionAddress, AccountState> preminedEntry : this.premined.entrySet()) {
                worldTrie.update(
                        preminedEntry.getKey().toByteArray(),
                        preminedEntry.getValue().getEncoded());
            }

            return worldTrie.getRootHash();
        }

        private static ByteArrayWrapper wrapValueForPut(DataWord value) {
            return (value.isZero())
                    ? DataWord.ZERO.toWrapper()
                    : new ByteArrayWrapper(value.getNoLeadZeroesData());
        }

        private static byte[] generateExtraData(int chainId) {
            byte[] extraData = new byte[32];
            byte[] idBytes = new byte[] {(byte) ((chainId >> 8) & 0xFF), (byte) (chainId & 0xFF)};
            System.arraycopy(idBytes, 0, extraData, 30, 2);
            return extraData;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("GenesisBlockData [\n");

        // header (encoded with 2 tabs)
        builder.append(this.getHeader().toString());

        // accounts
        builder.append("  Premined Accounts: \n");
        for (AccountState premined : this.premine.values()) {
            builder.append(premined.toString());
        }

        // chainId

        // footer
        builder.append("]");
        return builder.toString();
    }
}
