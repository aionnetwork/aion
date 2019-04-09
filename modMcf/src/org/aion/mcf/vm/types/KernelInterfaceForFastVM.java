package org.aion.mcf.vm.types;

import static org.aion.mcf.tx.TransactionTypes.FVM_CREATE_CODE;

import java.math.BigInteger;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.tx.TransactionTypes;
import org.aion.mcf.valid.TransactionTypeRule;
import org.aion.mcf.valid.TxNrgRule;
import org.aion.types.Address;
import org.aion.types.ByteArrayWrapper;
import org.aion.vm.api.interfaces.KernelInterface;

public class KernelInterfaceForFastVM implements KernelInterface {
    private RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repositoryCache;
    private boolean allowNonceIncrement, isLocalCall;

    public KernelInterfaceForFastVM(
            RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repositoryCache,
            boolean allowNonceIncrement,
            boolean isLocalCall) {

        if (repositoryCache == null) {
            throw new NullPointerException("Cannot set null repositoryCache!");
        }
        this.repositoryCache = repositoryCache;
        this.allowNonceIncrement = allowNonceIncrement;
        this.isLocalCall = isLocalCall;
    }

    @Override
    public KernelInterfaceForFastVM makeChildKernelInterface() {
        return new KernelInterfaceForFastVM(
                this.repositoryCache.startTracking(), this.allowNonceIncrement, this.isLocalCall);
    }

    @Override
    public void commit() {
        this.repositoryCache.flush();
    }

    @Override
    public void commitTo(KernelInterface target) {
        this.repositoryCache.flushTo(((KernelInterfaceForFastVM) target).repositoryCache, false);
    }

    // The below 2 methods will be removed during the next phase of refactoring. They are temporary.
    public void rollback() {
        this.repositoryCache.rollback();
    }

    public RepositoryCache<AccountState, IBlockStoreBase<?, ?>> getRepositoryCache() {
        return this.repositoryCache;
    }

    @Override
    public void createAccount(Address address) {
        this.repositoryCache.createAccount(address);
    }

    public void setVmType(Address address) {
        this.repositoryCache.saveVmType(address, FVM_CREATE_CODE);
    }

    @Override
    public boolean hasAccountState(Address address) {
        return this.repositoryCache.hasAccountState(address);
    }

    @Override
    public void putCode(Address address, byte[] code) {
        // ensure the vm type is set as soon as the account becomes a contract
        this.repositoryCache.saveVmType(address, FVM_CREATE_CODE);
        this.repositoryCache.saveCode(address, code);
    }

    @Override
    public byte[] getCode(Address address) {
        return this.repositoryCache.getCode(address);
    }

    @Override
    public void putObjectGraph(Address contract, byte[] graph) {
        throw new UnsupportedOperationException("The FVM does not use an object graph.");
    }

    @Override
    public byte[] getObjectGraph(Address contract) {
        throw new UnsupportedOperationException("The FVM does not use an object graph.");
    }

    @Override
    public void putStorage(Address address, byte[] key, byte[] value) {
        ByteArrayWrapper storageKey = alignDataToWordSize(key);
        ByteArrayWrapper storageValue = alignValueToWordSizeForPut(value);
        if (value == null || value.length == 0 || storageValue.isZero()) {
            // used to ensure FVM correctness
            throw new IllegalArgumentException(
                    "Put with null, empty or zero byte array values is not allowed for the FVM. For deletions, make explicit calls to the delete method.");
        }

        this.repositoryCache.addStorageRow(address, storageKey, storageValue);
        if (this.repositoryCache.getVmType(address) != FVM_CREATE_CODE) {
            this.repositoryCache.saveVmType(address, FVM_CREATE_CODE);
        }
    }

    @Override
    public void removeStorage(Address address, byte[] key) {
        ByteArrayWrapper storageKey = alignDataToWordSize(key);
        this.repositoryCache.removeStorageRow(address, storageKey);
        if (this.repositoryCache.getVmType(address) != FVM_CREATE_CODE) {
            this.repositoryCache.saveVmType(address, FVM_CREATE_CODE);
        }
    }

    @Override
    public byte[] getStorage(Address address, byte[] key) {
        ByteArrayWrapper storageKey = alignDataToWordSize(key);
        ByteArrayWrapper value = this.repositoryCache.getStorageValue(address, storageKey);
        if (value != null && (value.isZero() || value.isEmpty())) {
            // used to ensure FVM correctness
            throw new IllegalStateException(
                    "A zero or empty value was retrieved from storage. Storing zeros is not allowed by the FVM. An incorrect put was previously performed instead of an explicit call to the delete method.");
        }
        return (value == null) ? DataWordImpl.ZERO.getData() : alignValueToWordSizeForGet(value);
    }

    @Override
    public void deleteAccount(Address address) {
        if (!this.isLocalCall) {
            this.repositoryCache.deleteAccount(address);
        }
    }

    @Override
    public BigInteger getBalance(Address address) {
        return this.repositoryCache.getBalance(address);
    }

    @Override
    public void adjustBalance(Address address, BigInteger delta) {
        this.repositoryCache.addBalance(address, delta);
    }

    @Override
    public byte[] getBlockHashByNumber(long blockNumber) {
        return this.repositoryCache.getBlockStore().getBlockHashByNumber(blockNumber);
    }

    @Override
    public BigInteger getNonce(Address address) {
        return this.repositoryCache.getNonce(address);
    }

    @Override
    public void incrementNonce(Address address) {
        if (!this.isLocalCall && this.allowNonceIncrement) {
            this.repositoryCache.incrementNonce(address);
        }
    }

    @Override
    public void deductEnergyCost(Address address, BigInteger energyCost) {
        if (!this.isLocalCall) {
            this.repositoryCache.addBalance(address, energyCost.negate());
        }
    }

    @Override
    public void refundAccount(Address address, BigInteger amount) {
        if (!this.isLocalCall) {
            this.repositoryCache.addBalance(address, amount);
        }
    }

    @Override
    public void payMiningFee(Address miner, BigInteger fee) {
        if (!this.isLocalCall) {
            this.repositoryCache.addBalance(miner, fee);
        }
    }

    @Override
    public boolean accountNonceEquals(Address address, BigInteger nonce) {
        return (this.isLocalCall) ? true : getNonce(address).equals(nonce);
    }

    @Override
    public boolean accountBalanceIsAtLeast(Address address, BigInteger amount) {
        return (this.isLocalCall) ? true : getBalance(address).compareTo(amount) >= 0;
    }

    @Override
    public boolean isValidEnergyLimitForCreate(long energyLimit) {
        return (this.isLocalCall) ? true : TxNrgRule.isValidNrgContractCreate(energyLimit);
    }

    @Override
    public boolean isValidEnergyLimitForNonCreate(long energyLimit) {
        return (this.isLocalCall) ? true : TxNrgRule.isValidNrgTx(energyLimit);
    }

    @Override
    public boolean destinationAddressIsSafeForThisVM(Address address) {
        return TransactionTypeRule.isValidFVMContractDeployment(getVmType(address));
    }

    private byte getVmType(Address destination) {
        byte storedVmType = repositoryCache.getVMUsed(destination);

        // DEFAULT is returned when there was no contract information stored
        if (storedVmType == TransactionTypes.DEFAULT) {
            // will load contract into memory otherwise leading to consensus issues
            RepositoryCache track = repositoryCache.startTracking();
            return track.getVmType(destination);
        } else {
            return storedVmType;
        }
    }

    /**
     * If data.length > 16 then data is aligned to be 32 bytes.
     *
     * <p>Otherwise it is aligned to be 16 bytes with all of its leading zero bytes removed.
     *
     * <p>This method should only be used for putting data into storage.
     */
    private ByteArrayWrapper alignValueToWordSizeForPut(byte[] value) {
        if (value.length == DoubleDataWord.BYTES) {
            return new ByteArrayWrapper(new DoubleDataWord(value).getData());
        } else {
            DataWordImpl valueAsWord = new DataWordImpl(value);
            return (valueAsWord.isZero())
                    ? valueAsWord.toWrapper()
                    : new ByteArrayWrapper(valueAsWord.getNoLeadZeroesData());
        }
    }

    /**
     * If data.length > 16 then data is aligned to be 32 bytes.
     *
     * <p>Otherwise it is aligned to be 16 bytes.
     *
     * <p>This method should only be used for getting data from storage.
     */
    private byte[] alignValueToWordSizeForGet(ByteArrayWrapper wrappedValue) {
        byte[] value = wrappedValue.getData();

        if (value.length > DataWordImpl.BYTES) {
            return new DoubleDataWord(value).getData();
        } else {
            return new DataWordImpl(value).getData();
        }
    }

    /**
     * If data.length > 16 then data is aligned to be 32 bytes.
     *
     * <p>Otherwise it is aligned to be 16 bytes.
     *
     * <p>Takes a byte[] and outputs a {@link ByteArrayWrapper}.
     */
    private ByteArrayWrapper alignDataToWordSize(byte[] data) {
        if (data.length == DoubleDataWord.BYTES) {
            return new ByteArrayWrapper(new DoubleDataWord(data).getData());
        } else {
            return new ByteArrayWrapper(new DataWordImpl(data).getData());
        }
    }
}
