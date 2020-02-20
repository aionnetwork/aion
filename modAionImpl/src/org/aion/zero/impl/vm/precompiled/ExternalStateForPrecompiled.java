package org.aion.zero.impl.vm.precompiled;

import java.math.BigInteger;
import org.aion.base.AccountState;
import org.aion.mcf.db.Repository;
import org.aion.mcf.db.RepositoryCache;
import org.aion.precompiled.type.IPrecompiledDataWord;
import org.aion.precompiled.type.IExternalStateForPrecompiled;
import org.aion.precompiled.type.PrecompiledDataWord;
import org.aion.precompiled.type.PrecompiledDoubleDataWord;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.vm.common.TxNrgRule;

/**
 * An implementation of the {@link IExternalStateForPrecompiled} interface defined in the precompiled
 * contract module.
 */
public final class ExternalStateForPrecompiled implements IExternalStateForPrecompiled {
    private final Repository<AccountState> sourceRepository;
    private final RepositoryCache<AccountState> repository;
    private final long blockNumber;
    private final boolean isLocalCall;
    private final boolean allowNonceIncrement;
    private final boolean fork032Enabled;

    /**
     * Constructs a new external state whose state is backed by the provided repository.
     *
     * <p>A call to this class's {@code commit()} method is equivalent to calling {@code flush()} on
     * the given repository.
     *
     * @param repository The backing repository.
     * @param blockNumber The current block number.
     * @param isLocalCall Whether this is a local call or not (eth_call).
     * @param allowNonceIncrement Whether or not to increment account nonces.
     */
    public ExternalStateForPrecompiled(Repository<AccountState> repository, long blockNumber, boolean isLocalCall, boolean fork032Enabled, boolean allowNonceIncrement) {
        if (repository == null) {
            throw new NullPointerException("Cannot create precompiled external state with null repository!");
        }
        this.sourceRepository = repository;
        this.repository = sourceRepository.startTracking();
        this.blockNumber = blockNumber;
        this.isLocalCall = isLocalCall;
        this.fork032Enabled = fork032Enabled;
        this.allowNonceIncrement = allowNonceIncrement;
    }

    /** Commits the changes in this external state to its parent external state. */
    @Override
    public void commit() {
        this.repository.flushTo(sourceRepository, true);
    }

    /**
     * Returns a new external state that is a direct child of this external state.
     *
     * <p>Any changes made in the child external state will not alter this external state unless the
     * {@code commit()} method is called in the child, in which case all state changes will be
     * pushed to this external state.
     *
     * <p>The child external state will be passed the same block number, isLocalCall and
     * allowNonceIncrement values as this external state.
     *
     * @return a child external state.
     */
    @Override
    public IExternalStateForPrecompiled newChildExternalState() {
        return new ExternalStateForPrecompiled(this.repository, this.blockNumber, this.isLocalCall, this.fork032Enabled, this.allowNonceIncrement);
    }

    /**
     * Adds the provided key-value pairing to the external state, associating it only with the given
     * address.
     *
     * <p>If the given address already has a key-value pairing whose key is the same as the given
     * key, then the given value will overwrite whatever value is currently paired with that key.
     *
     * @param address The account address.
     * @param key The key.
     * @param value The value.
     */
    @Override
    public void addStorageValue(AionAddress address, IPrecompiledDataWord key, IPrecompiledDataWord value) {
        // We drop all of the leading zero bytes when we have a 16-byte data word as the value.
        // This has always been done, it's a repository storage implementation detail as a kind of
        // compaction optimization.
        byte[] valueBytes = (value instanceof PrecompiledDataWord) ? dropLeadingZeroes(value.copyOfData()) : value.copyOfData();
        this.repository.addStorageRow(address, ByteArrayWrapper.wrap(key.copyOfData()), ByteArrayWrapper.wrap(valueBytes));
    }

    /**
     * Removes the provided key from the storage associated with the given address, if that key
     * exists.
     *
     * @param address The account address.
     * @param key The key.
     */
    @Override
    public void removeStorage(AionAddress address, IPrecompiledDataWord key) {
        this.repository.removeStorageRow(address, ByteArrayWrapper.wrap(key.copyOfData()));
    }

    /**
     * Returns the value in the key-value pairing associated with the given address and key, or
     * {@code null} if no such pairing exists.
     *
     * @param address The account address.
     * @param key The key.
     * @return the value.
     */
    @Override
    public IPrecompiledDataWord getStorageValue(AionAddress address, IPrecompiledDataWord key) {
        ByteArrayWrapper byteArray = this.repository.getStorageValue(address, ByteArrayWrapper.wrap(key.copyOfData()));
        return (byteArray == null) ? null : toDataWord(byteArray.toBytes());
    }

    /**
     * Returns the balance of the specified account.
     *
     * @param address The account address.
     * @return the balance.
     */
    @Override
    public BigInteger getBalance(AionAddress address) {
        return this.repository.getBalance(address);
    }

    /**
     * Adjusts the balance of the specified account so that it is equal to its current value plus
     * the specified amount.
     *
     * @param address The account address.
     * @param amount The amount to add.
     */
    @Override
    public void addBalance(AionAddress address, BigInteger amount) {
        if (!this.isLocalCall && getBalance(address).add(amount).signum() < 0) {
            throw new IllegalArgumentException("This balance adjustment leads to a negative balance!");
        }
        this.repository.addBalance(address, amount);
    }

    /**
     * Returns the nonce of the specified account.
     *
     * @param address The account address.
     * @return the account nonce.
     */
    @Override
    public BigInteger getNonce(AionAddress address) {
        return this.repository.getNonce(address);
    }

    /**
     * Increments the nonce of the specified address by one if this is not a local call and nonce
     * incrementation is allowed. Otherwise does nothing.
     *
     * @param address The account address.
     */
    @Override
    public void incrementNonce(AionAddress address) {
        if (!this.isLocalCall && this.allowNonceIncrement) {
            this.repository.incrementNonce(address);
        }
    }

    /**
     * Returns the current block number.
     *
     * @return the block number.
     */
    @Override
    public long getBlockNumber() {
        return this.blockNumber;
    }

    /**
     * Returns the current block number.
     *
     * @return the block number.
     */
    @Override
    public boolean isFork032Enabled() {
        return this.fork032Enabled;
    }

    /**
     * Returns {@code true} only if the specified energyLimit is a valid energy limit for contract
     * creation transactions. Otherwise {@code false}.
     *
     * <p>If this is a local call then this method always returns {@code true} since we do not want
     * to perform energy limit checks during local calls.
     *
     * @param energyLimit The energy limit to test.
     * @return whether the limit is valid or not.
     */
    @Override
    public boolean isValidEnergyLimitForCreate(long energyLimit) {
        return (this.isLocalCall) ? true : TxNrgRule.isValidNrgContractCreate(energyLimit);
    }

    /**
     * Returns {@code true} only if the specified energyLimit is a valid energy limit for contract
     * call transactions. Otherwise {@code false}.
     *
     * <p>If this is a local call then this method always returns {@code true} since we do not want
     * to perform energy limit checks during local calls.
     *
     * @param energyLimit The energy limit to test.
     * @return whether the limit is valid or not.
     */
    @Override
    public boolean isValidEnergyLimitForNonCreate(long energyLimit) {
        return (this.isLocalCall) ? true : TxNrgRule.isValidNrgTx(energyLimit);
    }

    /**
     * Returns {@code true} only if the specified account address has a nonce equal to the specified
     * nonce value. Otherwise {@code false}.
     *
     * <p>If this is a local call then this method always returns {@code true} since we do not want
     * to perform nonce checks during local calls.
     *
     * @param address The account address.
     * @param nonce The nonce to test.
     * @return whether the nonce is equal or not.
     */
    @Override
    public boolean accountNonceEquals(AionAddress address, BigInteger nonce) {
        return (this.isLocalCall) ? true : getNonce(address).equals(nonce);
    }

    /**
     * Returns {@code true} only if the specified account address has a balance that is greater than
     * or equal to the specified amount. Otherwise {@code false}.
     *
     * <p>If this is a local call then this method always returns {@code true} since we do not want
     * to perform balance checks during local calls.
     *
     * @param address The account address.
     * @param amount The amount to test.
     * @return whether the balance is at least the amount.
     */
    @Override
    public boolean accountBalanceIsAtLeast(AionAddress address, BigInteger amount) {
        return (this.isLocalCall) ? true : getBalance(address).compareTo(amount) >= 0;
    }

    /**
     * Deducts the specified energyCost amount from the given account address if this is not a local
     * call. Otherwise, if this is a local call, this method does nothing.
     *
     * <p>This method can make an account balance become negative. It is the responsibility of the
     * caller to ensure that this is always a valid action.
     *
     * @param address The account address.
     * @param energyCost The amount to deduct.
     */
    @Override
    public void deductEnergyCost(AionAddress address, BigInteger energyCost) {
        if (!this.isLocalCall) {
            if (getBalance(address).subtract(energyCost).signum() < 0) {
                throw new IllegalArgumentException("This balance adjustment leads to a negative balance!");
            }
            this.repository.addBalance(address, energyCost.negate());
        }
    }

    private static int findIndexOfFirstNonZeroByte(byte[] bytes) {
        int indexOfFirstNonZeroByte = 0;
        for (byte singleByte : bytes) {
            if (singleByte != 0x0) {
                return indexOfFirstNonZeroByte;
            }
            indexOfFirstNonZeroByte++;
        }
        return indexOfFirstNonZeroByte;
    }

    /**
     * Returns the input bytes but with all leading zero bytes removed.
     *
     * <p>If the input bytes consists of all zero bytes then an array of length 1 whose only byte is
     * a zero byte is returned.
     *
     * @param bytes The bytes to chop.
     * @return the chopped bytes.
     */
    private static byte[] dropLeadingZeroes(byte[] bytes) {
        int indexOfFirstNonZeroByte = findIndexOfFirstNonZeroByte(bytes);

        if (indexOfFirstNonZeroByte == bytes.length) {
            return new byte[1];
        }

        byte[] nonZeroBytes = new byte[bytes.length - indexOfFirstNonZeroByte];
        System.arraycopy(bytes, indexOfFirstNonZeroByte, nonZeroBytes, 0, bytes.length - indexOfFirstNonZeroByte);
        return nonZeroBytes;
    }

    /**
     * Converts bytes to the appropriately sized data word implementation and returns it.
     *
     * @param bytes The bytes to convert.
     * @return the data word.
     */
    private static IPrecompiledDataWord toDataWord(byte[] bytes) {
        // A PrecompiledDataWord can be placed into storage as a byte array whose length is between
        // 1 and 16 inclusive. This is how we can tell whether we have a data word or a double.
        return (bytes.length > PrecompiledDataWord.SIZE) ? PrecompiledDoubleDataWord.fromBytes(bytes) : PrecompiledDataWord.fromBytes(bytes);
    }
}
