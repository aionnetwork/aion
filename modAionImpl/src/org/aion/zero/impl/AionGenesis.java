/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.zero.impl;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.core.AccountState;
import org.aion.crypto.HashUtil;
import org.aion.vm.PrecompiledContracts;
import org.aion.zero.db.AionContractDetailsImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.trie.SecureTrie;
import org.aion.mcf.trie.Trie;
import org.aion.mcf.types.AbstractBlockHeader;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class AionGenesis extends AionBlock {
    /**
     * Make this value constant for now, in the future we may move this to a
     * more configuration position, this indicates the address at which the
     * storage rows are to be stored
     */
    protected static final Address NETWORK_BALANCE_ADDRESS = PrecompiledContracts.totalCurrencyAddress;

    /**
     * Aion Genesis Constants
     * 
     * These are constants that will be used by {@link AionGenesis} to be
     * overidden by the default genesis.json file. If parameters are not
     * overridden the defaults should fallback to the values defined here
     */

    /**
     * Corresponds to {@link AbstractBlockHeader#getParentHash()}, for purposes
     * of the genesis blocks, this value could reference something arbitrary. We
     * have chosen to arbitrarily set it to a silly phrase.
     */
    protected static final byte[] GENESIS_PARENT_HASH = ByteUtil
            .hexStringToBytes("0000000000000000000000000000000000000000000000000000000000000000");

    /**
     * Corresponds to {@link AbstractBlockHeader#getCoinbase()} that mined the
     * first block. For fairness, the address is set to an address that is not
     * ever to be used
     */
    protected static final Address GENESIS_COINBASE = Address.ZERO_ADDRESS();

    /**
     * Corresponds to {@link AbstractBlockHeader#getLogsBloom()} indicates the
     * logsBloom of the genesis block, because no transactions are included in
     * this block. It defaults to empty.
     */
    protected static final byte[] GENESIS_LOGSBLOOM = new byte[256];

    /**
     * Corresponds to {@link AbstractBlockHeader#getDifficulty()} and
     * {@link AbstractBlockHeader#getDifficultyBI()}. This value represents the
     * initial difficulty of the network, a very important value to set. It is
     * not necessarily important to correctly set this value as it will rise to
     * based on the network hashing power.
     * 
     * This value is set based on a number derived from a very optimistic (upper
     * bound) estimate of the projected hashrate. The predicted difficulty
     * number is: 189625700 (189M). It is unrealistic to expect this amount
     * during the test-net phase, therefore divide this number by 100, this
     * roughly accounts for ~440 GPUs, something attainable by the founation on
     * test-net launch.
     */
    protected static final byte[] GENESIS_DIFFICULTY = ByteUtil.bigIntegerToBytes(BigInteger.valueOf(189625));

    /**
     * Corresponds to {@link AbstractBlockHeader#getNumber()} the number. This
     * is pretty self explanatory.
     */
    protected static final long GENESIS_NUMBER = 0;

    /**
     * Corresponds to {@link AbstractBlockHeader#getTimestamp()} the timestamp
     * that the block was forged. In terms of the genesis, we arbitrarily set it
     * to 0.
     */
    protected static final long GENESIS_TIMESTAMP = 0;

    /**
     * Corresponds to {@link AbstractBlockHeader#getExtraData()} extra data
     * (limited to 32 bytes), we arbitrarily set this to 0.
     * 
     * TODO: we may want to put something meaningful here
     */
    protected static final byte[] GENESIS_EXTRA_DATA = new byte[32];

    /**
     * Corresponds to {@link AbstractBlockHeader#getNonce()} nonce of the block,
     * we arbitrarily set this to 0 for now
     */
    protected static final byte[] GENESIS_NONCE = new byte[32];

    /**
     * Corresponds to {@link A0BlockHeader#getEnergyLimit()} sets the initial
     * energy limit. We will set this to an accept (but low) value to ensure the
     * network is not strained from the start.
     *
     * Previously, this was set to a low value, but for the test net we would
     * like to stress the network, thus the default value is being changed from
     * {@code 1000000 -> 10000000}. This gives us the ability to deploy more
     * complicated contracts.
     * 
     * @see <a href=
     *      "https://blog.ethereum.org/2016/10/31/uncle-rate-transaction-fee-analysis">fee-analysis</a>
     */
    protected static final long GENESIS_ENERGY_LIMIT = 10000000;

    /**
     * Corresponds to {@link AionGenesis#premine} default premined accounts
     */
    protected static final Map<Address, AccountState> GENESIS_PREMINE;

    protected static final Map<Integer, BigInteger> GENESIS_NETWORK_BALANCE;

    static {
        // leaving because we may want to add accounts later
        GENESIS_PREMINE = new HashMap<>();

        GENESIS_NETWORK_BALANCE = new HashMap<>();
        GENESIS_NETWORK_BALANCE.put(0, new BigInteger("465934586660000000000000000"));
    }

    private Map<Integer, BigInteger> networkBalances = new HashMap<>();

    /**
     * Premined accounts, these are accounts that have initial state and value.
     * Because of the bridging mechanism, we arbitrarily store all ERC20 token
     * values as coins on an account at address 0x100 (256).
     */
    private Map<Address, AccountState> premine = new HashMap<>();

    // TODO: verify whether setting the solution to null is okay
    // TODO: set energyLimit to a correct value (after genesis loader is
    // completed)
    public AionGenesis(byte[] parentHash, Address coinbase, byte[] logsBloom, byte[] difficulty, long number,
            long timestamp, byte[] extraData, byte[] nonce, long energyLimit) {
        super(parentHash, coinbase, logsBloom, difficulty, number, timestamp, extraData, nonce, energyLimit);
    }

    public Map<Address, AccountState> getPremine() {
        return premine;
    }

    public void setPremine(Map<Address, AccountState> premine) {
        this.premine = premine;
    }

    public void setNetworkBalance(Map<Integer, BigInteger> networkBalances) {
        this.networkBalances = networkBalances;
    }

    public Map<Integer, BigInteger> getNetworkBalances() {
        return this.networkBalances;
    }

    /**
     * Genesis will fallback to a set of default values given that the loader
     * does not override them, note that because a block by default is not
     * immutable we are required here to create a new instance of the genesis
     * each time the builder produces a new block.
     * 
     * Does not assume anything about the input data, will do the necessary
     * checks to assert the specs. But will not do null checks, therefore it is
     * up to the caller to ensure input values are non null
     * 
     * This class makes no assumptions about thread-safety.
     */
    public static class Builder {
        protected byte[] parentHash;
        protected Address coinbase;
        protected byte[] logsBloom;
        protected byte[] difficulty;
        protected Long number;
        protected Long timestamp;
        protected byte[] extraData;
        protected byte[] nonce;
        protected Long energyLimit;

        protected Map<Integer, BigInteger> networkBalance;
        protected Map<Address, AccountState> premined;

        public Builder withParentHash(final byte[] parentHash) {
            this.parentHash = parentHash;
            return this;
        }

        public Builder withCoinbase(final Address coinbase) {
            this.coinbase = coinbase;
            return this;
        }

        public Builder withDifficulty(final byte[] difficulty) {
            this.difficulty = difficulty;
            return this;
        }

        public Builder withNumber(final long number) {
            if (number < 0)
                throw new IllegalArgumentException("number cannot be negative");

            this.number = number;
            return this;
        }

        public Builder withTimestamp(final long timestamp) {
            if (timestamp < 0)
                throw new IllegalArgumentException("timestamp cannot be negative");

            this.timestamp = timestamp;
            return this;
        }

        public Builder withExtraData(final byte[] extraData) {
            this.extraData = extraData;
            return this;
        }

        public Builder withNonce(final byte[] nonce) {
            this.nonce = nonce;
            return this;
        }

        public Builder withEnergyLimit(final long energyLimit) {
            if (energyLimit < 0)
                throw new IllegalArgumentException("energyLimit cannot be negative");

            this.energyLimit = energyLimit;
            return this;
        }

        public Builder addPreminedAccount(final Address address, final AccountState state) {
            if (this.premined == null)
                this.premined = new HashMap<>();

            if (this.premined.get(address) != null)
                throw new IllegalArgumentException("duplicate premined address");

            this.premined.put(address, state);
            return this;
        }

        public Builder addNetworkBalance(Integer chainId, BigInteger balance) {
            if (chainId < 0)
                throw new IllegalArgumentException("networkBalance chainId cannot be null or negative");

            if (balance.signum() < 0)
                throw new IllegalArgumentException("networkBalance balance cannot be null or negative");

            if (this.networkBalance == null)
                this.networkBalance = new HashMap<>();

            if (chainId > 256)
                throw new IllegalArgumentException("chainId cannot exceed 255");

            if (this.networkBalance.get(chainId) != null)
                throw new IllegalArgumentException("duplicate chainId");

            this.networkBalance.put(chainId, balance);
            return this;
        }

        /**
         * Build the genesis block, after parameters have been set. Defaults
         * back to default genesis values if parameters are not specified.
         */
        public AionGenesis build() {
            if (this.parentHash == null)
                this.parentHash = GENESIS_PARENT_HASH;

            if (this.coinbase == null)
                this.coinbase = GENESIS_COINBASE;

            if (this.difficulty == null)
                this.difficulty = GENESIS_DIFFICULTY;

            if (this.number == null)
                this.number = GENESIS_NUMBER;

            if (this.timestamp == null)
                this.timestamp = GENESIS_TIMESTAMP;

            if (this.extraData == null)
                this.extraData = GENESIS_EXTRA_DATA;

            if (this.nonce == null)
                this.nonce = GENESIS_NONCE;

            if (this.energyLimit == null)
                this.energyLimit = GENESIS_ENERGY_LIMIT;

            if (this.premined == null)
                this.premined = GENESIS_PREMINE;

            if (this.networkBalance == null)
                this.networkBalance = GENESIS_NETWORK_BALANCE;

            AionGenesis genesis = new AionGenesis(this.parentHash, this.coinbase, this.logsBloom, this.difficulty,
                    this.number, this.timestamp, this.extraData, this.nonce, this.energyLimit);

            // temporary solution, so as not to disrupt the constructors
            genesis.setPremine(this.premined);
            genesis.setNetworkBalance(this.networkBalance);

            byte[] rootHash = generateRootHash();

            genesis.getHeader().setStateRoot(rootHash);
            genesis.getHeader().setReceiptsRoot(HashUtil.EMPTY_TRIE_HASH);
            genesis.getHeader().setTxTrieRoot(HashUtil.EMPTY_TRIE_HASH);

            return genesis;
        }

        protected byte[] generateRootHash() {
            Trie worldTrie = new SecureTrie(null);
            AionContractDetailsImpl networkBalanceStorage = new AionContractDetailsImpl();

            for (Map.Entry<Integer, BigInteger> entry : this.networkBalance.entrySet()) {
                networkBalanceStorage.put(new DataWord(entry.getKey()), new DataWord(entry.getValue()));
            }
            byte[] networkBalanceStorageHash = networkBalanceStorage.getStorageHash();

            AccountState networkBalanceAccount = new AccountState();
            networkBalanceAccount.setStateRoot(networkBalanceStorageHash);

            worldTrie.update(NETWORK_BALANCE_ADDRESS.toBytes(), networkBalanceAccount.getEncoded());

            // update predefined accounts
            for (Map.Entry<Address, AccountState> preminedEntry : this.premined.entrySet()) {
                worldTrie.update(preminedEntry.getKey().toBytes(), preminedEntry.getValue().getEncoded());
            }

            return worldTrie.getRootHash();
        }
    }
}
