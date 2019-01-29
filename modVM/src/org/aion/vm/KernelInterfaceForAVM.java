package org.aion.vm;

import java.math.BigInteger;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.vm.VirtualMachineSpecs;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.valid.TxNrgRule;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.precompiled.ContractFactory;
import org.aion.vm.api.interfaces.Address;
import org.aion.vm.api.interfaces.KernelInterface;

public class KernelInterfaceForAVM implements KernelInterface {
    private IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repositoryCache;
    private boolean allowNonceIncrement, isLocalCall;

    public KernelInterfaceForAVM(
        IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repositoryCache,
        boolean allowNonceIncrement,
        boolean isLocalCall) {

        if (repositoryCache == null) {
            throw new NullPointerException("Cannot set null repositoryCache!");
        }
        this.repositoryCache = repositoryCache;
        this.allowNonceIncrement = allowNonceIncrement;
        this.isLocalCall = isLocalCall;
    }

    // These 4 methods are temporary. Really any of this type of functionality should be moved out
    // into the kernel.
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
        this.repositoryCache.flushTo(((KernelInterfaceForAVM) target).repositoryCache, false);
    }

    public void rollback() {
        this.repositoryCache.rollback();
    }

    public IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> getRepositoryCache() {
        return this.repositoryCache;
    }
    // The above 4 methods are temporary. See comment just above.

    @Override
    public void createAccount(Address address) {
        this.repositoryCache.createAccount(address);
    }

    @Override
    public boolean hasAccountState(Address address) {
        return this.repositoryCache.hasAccountState(address);
    }

    @Override
    public void putCode(Address address, byte[] code) {
        this.repositoryCache.saveCode(address, code);
    }

    @Override
    public byte[] getCode(Address address) {
        return this.repositoryCache.getCode(address);
    }

    @Override
    public void putStorage(Address address, byte[] key, byte[] value) {
        //new byte[]{21, 22, 23, 24, 25, 26, 27, 28, 29, 20};
        ByteArrayWrapper storageKey = new ByteArrayWrapper(key);
        ByteArrayWrapper storageValue = new ByteArrayWrapper(value);
        this.repositoryCache.addStorageRow(address, storageKey, storageValue);
    }

    @Override
    public void removeStorage(Address address, byte[] key) {
        //TODO
//        ByteArrayWrapper storageKey = new ByteArrayWrapper(key);
//        this.repositoryCache.removeStorageRow(address, storageKey);
    }

    @Override
    public byte[] getStorage(Address address, byte[] key) {
        ByteArrayWrapper storageKey = new ByteArrayWrapper(key);
        ByteArrayWrapper value = this.repositoryCache.getStorageValue(address, storageKey);
        return (value == null) ? null : value.getData();
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
        // Avm cannot run pre-compiled contracts.
        if (ContractFactory.isPrecompiledContract(address)) {
            return false;
        }

        // If address has no code then it is always safe.
        if (getCode(address).length == 0) {
            return true;
        }

        // Otherwise, it must be an Avm contract address.
        return address.toBytes()[0] == VirtualMachineSpecs.AVM_VM_CODE;
    }
}
