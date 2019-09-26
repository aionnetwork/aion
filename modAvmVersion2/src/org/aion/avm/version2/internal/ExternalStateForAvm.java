package org.aion.avm.version2.internal;

import java.math.BigInteger;
import org.aion.avm.core.IExternalState;
import org.aion.avm.stub.IAvmExternalState;
import org.aion.avm.stub.IEnergyRules;
import org.aion.avm.stub.IEnergyRules.TransactionType;
import org.aion.base.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.db.InternalVmType;
import org.aion.mcf.db.RepositoryCache;
import org.aion.precompiled.ContractInfo;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;

/**
 * @implNote This class implements both {@link IExternalState} and {@link IAvmExternalState} because
 * {@link IExternalState} is really the interface we need to be able to speak in terms of for the
 * real AVM. This is what is required of this class for all of the internal details of this module.
 * However, the outside world cannot have any AVM dependencies and so cannot talk about this interface.
 * The 'stub interface' {@link IAvmExternalState} is what allows us to communicate this idea to the
 * outside world. This allows us to cast to the correct type when we need to, rather than instantiate
 * a new equivalent object for whatever world we are talking to.
 */
public final class ExternalStateForAvm implements IExternalState, IAvmExternalState {
    private RepositoryCache<AccountState, IBlockStoreBase> repositoryCache;
    private BigInteger blockDifficulty;
    private AionAddress blockCoinbase;
    private IEnergyRules energyRules;
    private boolean allowNonceIncrement, isLocalCall;
    private long blockNumber;
    private long blockTimestamp;
    private long blockNrgLimit;

    public ExternalStateForAvm(RepositoryCache<AccountState, IBlockStoreBase> repositoryCache, boolean allowNonceIncrement, boolean isLocalCall, BigInteger blockDifficulty, long blockNumber, long blockTimestamp, long blockNrgLimit, AionAddress blockCoinbase, IEnergyRules energyRules) {
        this.repositoryCache = repositoryCache;
        this.allowNonceIncrement = allowNonceIncrement;
        this.isLocalCall = isLocalCall;
        this.blockDifficulty = blockDifficulty;
        this.blockNumber = blockNumber;
        this.blockTimestamp = blockTimestamp;
        this.blockNrgLimit = blockNrgLimit;
        this.blockCoinbase = blockCoinbase;
        this.energyRules = energyRules;
    }

    @Override
    public ExternalStateForAvm newChildExternalState() {
        return new ExternalStateForAvm(this.repositoryCache.startTracking(), this.allowNonceIncrement, this.isLocalCall, this.blockDifficulty, this.blockNumber, this.blockTimestamp, this.blockNrgLimit, this.blockCoinbase, this.energyRules);
    }

    @Override
    public void commit() {
        this.repositoryCache.flush();
    }

    @Override
    public void commitTo(IExternalState target) {
        this.repositoryCache.flushTo(((ExternalStateForAvm) target).repositoryCache, false);
    }

    // the below two methods are temporary and will be removed by the upcoming refactorings.
    public void rollback() {
        this.repositoryCache.rollback();
    }

    public RepositoryCache<AccountState, IBlockStoreBase> getRepositoryCache() {
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
        if (code.length == 0) {
            throw new IllegalArgumentException("The AVM does not allow the concept of empty code.");
        }
        this.repositoryCache.saveCode(address, code);
        setVmType(address);
    }

    @Override
    public byte[] getCode(AionAddress address) {
        byte[] code = this.repositoryCache.getCode(address);
        // the notion of empty code is not a valid concept for the AVM
        return code.length == 0 ? null : code;
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
    public void refundAccount(AionAddress address, BigInteger amount) {
        if (!this.isLocalCall) {
            this.repositoryCache.addBalance(address, amount);
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
        return (this.isLocalCall) || this.energyRules.isValidEnergyLimit(TransactionType.CREATE, energyLimit);
    }

    @Override
    public boolean isValidEnergyLimitForNonCreate(long energyLimit) {
        return (this.isLocalCall) || this.energyRules.isValidEnergyLimit(TransactionType.NON_CREATE, energyLimit);
    }

    @Override
    public boolean destinationAddressIsSafeForThisVM(AionAddress address) {
        // Avm cannot run pre-compiled contracts.
        if (ContractInfo.isPrecompiledContract(address)) {
            return false;
        }

        // If address has no code then it is always safe.
        if (getCode(address) == null) {
            return true;
        }

        // Otherwise, it must be an Avm contract address.
        return getVmType(address) != InternalVmType.FVM;
    }

    private InternalVmType getVmType(AionAddress destination) {
        // will load contract into memory otherwise leading to consensus issues
        RepositoryCache<AccountState, IBlockStoreBase> track = repositoryCache.startTracking();
        AccountState accountState = track.getAccountState(destination);

        InternalVmType vm;
        if (accountState == null) {
            // the address doesn't exist yet, so it can be used by either vm
            vm = InternalVmType.EITHER;
        } else {
            vm = repositoryCache.getVMUsed(destination, accountState.getCodeHash());

            // UNKNOWN is returned when there was no contract information stored
            if (vm == InternalVmType.UNKNOWN) {
                // use the in-memory value
                vm = track.getVmType(destination);
            }
        }
        return vm;
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
    public BigInteger getBlockDifficulty() {
        return this.blockDifficulty;
    }

    @Override
    public AionAddress getMinerAddress() {
        return blockCoinbase;
    }
}
