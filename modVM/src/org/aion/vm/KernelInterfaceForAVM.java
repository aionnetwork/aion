package org.aion.vm;

import java.math.BigInteger;
import org.aion.types.AionAddress;
import org.aion.interfaces.db.InternalVmType;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.interfaces.vm.DataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.valid.TxNrgRule;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.ContractFactory;
import org.aion.vm.api.types.ByteArrayWrapper;
import org.aion.vm.api.interfaces.KernelInterface;

public class KernelInterfaceForAVM implements KernelInterface {
    private RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repositoryCache;
    private boolean allowNonceIncrement, isLocalCall;

    private DataWord blockDifficulty;
    private long blockNumber;
    private long blockTimestamp;
    private long blockNrgLimit;
    private AionAddress blockCoinbase;

    public KernelInterfaceForAVM(
            RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repositoryCache,
            boolean allowNonceIncrement,
            boolean isLocalCall,
            DataWord blockDifficulty,
            long blockNumber,
            long blockTimestamp,
            long blockNrgLimit,
            AionAddress blockCoinbase) {

        if (repositoryCache == null) {
            throw new NullPointerException("Cannot set null repositoryCache!");
        }
        this.repositoryCache = repositoryCache;
        this.allowNonceIncrement = allowNonceIncrement;
        this.isLocalCall = isLocalCall;
        this.blockDifficulty = blockDifficulty;
        this.blockNumber = blockNumber;
        this.blockTimestamp = blockTimestamp;
        this.blockNrgLimit = blockNrgLimit;
        this.blockCoinbase = blockCoinbase;
    }

    @Override
    public KernelInterfaceForAVM makeChildKernelInterface() {
        return new KernelInterfaceForAVM(
                this.repositoryCache.startTracking(),
                this.allowNonceIncrement,
                this.isLocalCall,
                this.blockDifficulty,
                this.blockNumber,
                this.blockTimestamp,
                this.blockNrgLimit,
                this.blockCoinbase);
    }

    @Override
    public void commit() {
        this.repositoryCache.flush();
    }

    @Override
    public void commitTo(KernelInterface target) {
        this.repositoryCache.flushTo(((KernelInterfaceForAVM) target).repositoryCache, false);
    }

    // the below two methods are temporary and will be removed by the upcoming refactorings.
    public void rollback() {
        this.repositoryCache.rollback();
    }

    public RepositoryCache<AccountState, IBlockStoreBase<?, ?>> getRepositoryCache() {
        return this.repositoryCache;
    }

    @Override
    public void createAccount(AionAddress address) {
        this.repositoryCache.createAccount(address);
    }

    public void setVmType(AionAddress address) {
        this.repositoryCache.saveVmType(address, InternalVmType.AVM);
    }

    @Override
    public boolean hasAccountState(AionAddress address) {
        return this.repositoryCache.hasAccountState(address);
    }

    @Override
    public void putCode(AionAddress address, byte[] code) {
        this.repositoryCache.saveCode(address, code);
        setVmType(address);
    }

    @Override
    public byte[] getCode(AionAddress address) {
        return this.repositoryCache.getCode(address);
    }

    @Override
    public byte[] getTransformedCode(AionAddress address) {
        return this.repositoryCache.getTransformedCode(address);
    }

    @Override
    public void setTransformedCode(AionAddress address, byte[] transformedCode) {
        this.repositoryCache.setTransformedCode(address, transformedCode);
        setVmType(address);
    }

    @Override
    public void putObjectGraph(AionAddress contract, byte[] graph) {
        this.repositoryCache.saveObjectGraph(contract, graph);
        setVmType(contract);
    }

    @Override
    public byte[] getObjectGraph(AionAddress contract) {
        return this.repositoryCache.getObjectGraph(contract);
    }

    @Override
    public void putStorage(AionAddress address, byte[] key, byte[] value) {
        ByteArrayWrapper storageKey = new ByteArrayWrapper(key);
        ByteArrayWrapper storageValue = new ByteArrayWrapper(value);
        this.repositoryCache.addStorageRow(address, storageKey, storageValue);
        setVmType(address);
    }

    @Override
    public void removeStorage(AionAddress address, byte[] key) {
        ByteArrayWrapper storageKey = new ByteArrayWrapper(key);
        this.repositoryCache.removeStorageRow(address, storageKey);
        setVmType(address);
    }

    @Override
    public byte[] getStorage(AionAddress address, byte[] key) {
        ByteArrayWrapper storageKey = new ByteArrayWrapper(key);
        ByteArrayWrapper value = this.repositoryCache.getStorageValue(address, storageKey);
        return (value == null) ? null : value.getData();
    }

    @Override
    public void deleteAccount(AionAddress address) {
        if (!this.isLocalCall) {
            this.repositoryCache.deleteAccount(address);
        }
    }

    @Override
    public BigInteger getBalance(AionAddress address) {
        return this.repositoryCache.getBalance(address);
    }

    @Override
    public void adjustBalance(AionAddress address, BigInteger delta) {
        this.repositoryCache.addBalance(address, delta);
    }

    @Override
    public byte[] getBlockHashByNumber(long blockNumber) {
        return this.repositoryCache.getBlockStore().getBlockHashByNumber(blockNumber);
    }

    @Override
    public BigInteger getNonce(AionAddress address) {
        return this.repositoryCache.getNonce(address);
    }

    @Override
    public void incrementNonce(AionAddress address) {
        if (!this.isLocalCall && this.allowNonceIncrement) {
            this.repositoryCache.incrementNonce(address);
        }
    }

    @Override
    public void deductEnergyCost(AionAddress address, BigInteger energyCost) {
        if (!this.isLocalCall) {
            this.repositoryCache.addBalance(address, energyCost.negate());
        }
    }

    @Override
    public void refundAccount(AionAddress address, BigInteger amount) {
        if (!this.isLocalCall) {
            this.repositoryCache.addBalance(address, amount);
        }
    }

    @Override
    public void payMiningFee(AionAddress miner, BigInteger fee) {
        if (!this.isLocalCall) {
            this.repositoryCache.addBalance(miner, fee);
        }
    }

    @Override
    public boolean accountNonceEquals(AionAddress address, BigInteger nonce) {
        return (this.isLocalCall) || getNonce(address).equals(nonce);
    }

    @Override
    public boolean accountBalanceIsAtLeast(AionAddress address, BigInteger amount) {
        return (this.isLocalCall) || getBalance(address).compareTo(amount) >= 0;
    }

    @Override
    public boolean isValidEnergyLimitForCreate(long energyLimit) {
        return (this.isLocalCall) || TxNrgRule.isValidNrgContractCreate(energyLimit);
    }

    @Override
    public boolean isValidEnergyLimitForNonCreate(long energyLimit) {
        return (this.isLocalCall) || TxNrgRule.isValidNrgTx(energyLimit);
    }

    @Override
    public boolean destinationAddressIsSafeForThisVM(AionAddress address) {
        // Avm cannot run pre-compiled contracts.
        if (ContractFactory.isPrecompiledContract(address)) {
            return false;
        }

        // If address has no code then it is always safe.
        if (getCode(address).length == 0) {
            return true;
        }

        // Otherwise, it must be an Avm contract address.
        return getVmType(address) != InternalVmType.FVM;
    }

    private InternalVmType getVmType(AionAddress destination) {
        InternalVmType storedVmType = repositoryCache.getVMUsed(destination);

        // DEFAULT is returned when there was no contract information stored
        if (storedVmType == InternalVmType.UNKNOWN) {
            // will load contract into memory otherwise leading to consensus issues
            RepositoryCache track = repositoryCache.startTracking();
            return track.getVmType(destination);
        } else {
            return storedVmType;
        }
    }

    @Override
    public long getBlockNumber() {
        return blockNumber;
    }

    @Override
    public long getBlockTimestamp() {
        return blockTimestamp;
    }

    @Override
    public long getBlockEnergyLimit() {
        return blockNrgLimit;
    }

    @Override
    public long getBlockDifficulty() {
        if (blockDifficulty instanceof DataWordImpl) {
            return ((DataWordImpl) blockDifficulty).longValue();
        } else {
            return ((DoubleDataWord) blockDifficulty).longValue();
        }
    }

    @Override
    public AionAddress getMinerAddress() {
        return blockCoinbase;
    }
}
