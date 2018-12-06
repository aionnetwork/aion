/*
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
 */
package org.aion.mcf.db;

import static org.aion.base.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.aion.crypto.HashUtil.h256;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.aion.base.db.IContractDetails;
import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.vm.IDataWord;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.core.AccountState;
import org.slf4j.Logger;
import org.aion.vm.api.interfaces.Address;

/**
 * Abstract repository cache.
 *
 * @author Alexandra Roatis
 */
public abstract class AbstractRepositoryCache<BSB extends IBlockStoreBase<?, ?>>
        implements IRepositoryCache<AccountState, IDataWord, BSB> {

    // Logger
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    /** the repository being tracked */
    protected IRepository<AccountState, IDataWord, BSB> repository;

    /** local accounts cache */
    protected Map<Address, AccountState> cachedAccounts;

    protected ReadWriteLock lockAccounts = new ReentrantReadWriteLock();
    /** local contract details cache */
    protected Map<Address, IContractDetails<IDataWord>> cachedDetails;

    protected ReadWriteLock lockDetails = new ReentrantReadWriteLock();

    @Override
    public AccountState createAccount(Address address) {
        fullyWriteLock();
        try {
            AccountState accountState = new AccountState();
            cachedAccounts.put(address, accountState);

            // TODO: unify contract details initialization from Impl and Track
            IContractDetails<IDataWord> contractDetails = new ContractDetailsCacheImpl(null);
            // TODO: refactor to use makeDirty() from AbstractState
            contractDetails.setDirty(true);
            cachedDetails.put(address, contractDetails);

            return accountState;
        } finally {
            fullyWriteUnlock();
        }
    }

    /**
     * Retrieves the current state of the account associated with the given address.
     *
     * @param address the address of the account of interest
     * @return a {@link AccountState} object representing the account state as is stored in the
     *     database or cache
     * @implNote If there is no account associated with the given address, it will create it.
     */
    @Override
    public AccountState getAccountState(Address address) {
        lockAccounts.readLock().lock();

        try {
            // check if the account is cached locally
            AccountState accountState = this.cachedAccounts.get(address);

            // when the account is not cached load it from the repository
            if (accountState == null) {
                // must unlock to perform write operation from loadAccountState(address)
                lockAccounts.readLock().unlock();
                loadAccountState(address);
                lockAccounts.readLock().lock();
                accountState = this.cachedAccounts.get(address);
            }

            return accountState;
        } finally {
            try {
                lockAccounts.readLock().unlock();
            } catch (Exception e) {
                // there was nothing to unlock
            }
        }
    }

    public boolean hasAccountState(Address address) {
        lockAccounts.readLock().lock();
        try {
            AccountState accountState = cachedAccounts.get(address);

            if (accountState != null) {
                // checks that the account is not cached as deleted
                // TODO: may also need to check if the state is empty
                return !accountState.isDeleted();
            } else {
                // check repository when not cached
                return repository.hasAccountState(address);
            }
        } finally {
            lockAccounts.readLock().unlock();
        }
    }

    @Override
    public IContractDetails<IDataWord> getContractDetails(Address address) {
        lockDetails.readLock().lock();

        try {
            IContractDetails<IDataWord> contractDetails = this.cachedDetails.get(address);

            if (contractDetails == null) {
                // loads the address into cache
                // must unlock to perform write operation from loadAccountState(address)
                lockDetails.readLock().unlock();
                loadAccountState(address);
                lockDetails.readLock().lock();
                // retrieves the contract details
                contractDetails = this.cachedDetails.get(address);
            }

            return contractDetails;
        } finally {
            try {
                lockDetails.readLock().unlock();
            } catch (Exception e) {
                // there was nothing to unlock
            }
        }
    }

    @Override
    public boolean hasContractDetails(Address address) {
        lockDetails.readLock().lock();

        try {
            IContractDetails<IDataWord> contractDetails = cachedDetails.get(address);

            if (contractDetails == null) {
                // ask repository when not cached
                return repository.hasContractDetails(address);
            } else {
                // TODO: may also need to check if the details are empty
                return !contractDetails.isDeleted();
            }
        } finally {
            lockDetails.readLock().unlock();
        }
    }

    /**
     * @implNote The loaded objects are fresh copies of the locally cached account state and
     *     contract details.
     */
    @Override
    public void loadAccountState(
            Address address,
            Map<Address, AccountState> accounts,
            Map<Address, IContractDetails<IDataWord>> details) {
        fullyReadLock();

        try {
            // check if the account is cached locally
            AccountState accountState = this.cachedAccounts.get(address);
            IContractDetails<IDataWord> contractDetails = this.cachedDetails.get(address);

            // when account not cached load from repository
            if (accountState == null) {
                // load directly to the caches given as parameters
                repository.loadAccountState(address, accounts, details);
            } else {
                // copy the objects if they were cached locally
                accounts.put(address, new AccountState(accountState));
                details.put(address, new ContractDetailsCacheImpl(contractDetails));
            }
        } finally {
            fullyReadUnlock();
        }
    }

    /**
     * Loads the state of the account into <b>this object' caches</b>. Requires write locks on both
     * {@link #lockAccounts} and {@link #lockDetails}.
     *
     * @implNote If the calling method has acquired a weaker lock, the lock must be released before
     *     calling this method.
     * @apiNote If the account was never stored this call will create it.
     */
    private void loadAccountState(Address address) {
        fullyWriteLock();
        try {
            repository.loadAccountState(address, this.cachedAccounts, this.cachedDetails);
        } finally {
            fullyWriteUnlock();
        }
    }

    @Override
    public void deleteAccount(Address address) {
        fullyWriteLock();
        try {
            getAccountState(address).delete();
            getContractDetails(address).setDeleted(true);
        } finally {
            fullyWriteUnlock();
        }
    }

    @Override
    public BigInteger incrementNonce(Address address) {
        lockAccounts.writeLock().lock();
        try {
            return getAccountState(address).incrementNonce();
        } finally {
            lockAccounts.writeLock().unlock();
        }
    }

    @Override
    public BigInteger setNonce(Address address, BigInteger newNonce) {
        lockAccounts.writeLock().lock();
        try {
            return getAccountState(address).setNonce(newNonce);
        } finally {
            lockAccounts.writeLock().unlock();
        }
    }

    @Override
    public BigInteger getNonce(Address address) {
        AccountState accountState = getAccountState(address);
        // account state can never be null, but may be empty or deleted
        return (accountState.isEmpty() || accountState.isDeleted())
                ? BigInteger.ZERO
                : accountState.getNonce();
    }

    @Override
    public BigInteger getBalance(Address address) {
        AccountState accountState = getAccountState(address);
        // account state can never be null, but may be empty or deleted
        return (accountState.isEmpty() || accountState.isDeleted())
                ? BigInteger.ZERO
                : accountState.getBalance();
    }

    @Override
    public BigInteger addBalance(Address address, BigInteger value) {
        lockAccounts.writeLock().lock();
        try {
            // TODO: where do we ensure that this does not result in a negative value?
            AccountState accountState = getAccountState(address);
            return accountState.addToBalance(value);
        } finally {
            lockAccounts.writeLock().unlock();
        }
    }

    @Override
    public void saveCode(Address address, byte[] code) {
        fullyWriteLock();
        try {
            // save the code
            // TODO: why not create contract here directly? also need to check that there is no
            // preexisting code!
            IContractDetails<IDataWord> contractDetails = getContractDetails(address);
            contractDetails.setCode(code);
            // TODO: ensure that setDirty is done by the class itself
            contractDetails.setDirty(true);

            // update the code hash
            getAccountState(address).setCodeHash(h256(code));
        } finally {
            fullyWriteUnlock();
        }
    }

    @Override
    public byte[] getCode(Address address) {
        if (!hasAccountState(address)) {
            return EMPTY_BYTE_ARRAY;
        }

        byte[] codeHash = getAccountState(address).getCodeHash();

        // TODO: why use codeHash here? may require refactoring
        return getContractDetails(address).getCode(codeHash);
    }

    @Override
    public void addStorageRow(Address address, IDataWord key, IDataWord value) {
        lockDetails.writeLock().lock();
        try {
            getContractDetails(address).put(key, value);
        } finally {
            lockDetails.writeLock().unlock();
        }
    }

    @Override
    public IDataWord getStorageValue(Address address, IDataWord key) {
        IDataWord value = getContractDetails(address).get(key);
        if (value == null) {
            return null;
        }
        return (value.isZero()) ? null : value;
    }

    @Override
    public Map<IDataWord, IDataWord> getStorage(Address address, Collection<IDataWord> keys) {
        IContractDetails<IDataWord> details = getContractDetails(address);
        return (details == null) ? Collections.emptyMap() : details.getStorage(keys);
    }

    @Override
    public void rollback() {
        fullyWriteLock();
        try {
            cachedAccounts.clear();
            cachedDetails.clear();
        } finally {
            fullyWriteUnlock();
        }
    }

    @Override
    public IRepository getSnapshotTo(byte[] root) {
        return repository.getSnapshotTo(root);
    }

    // This method was originally disabled because changes to the blockstore
    // can not be reverted. The reason for re-enabling it is that we have no way
    // to
    // get a reference of the blockstore without using
    // NProgInvoke/NcpProgInvoke.
    @Override
    public BSB getBlockStore() {
        return repository.getBlockStore();
    }

    /** Lock to prevent writing on both accounts and details. */
    protected void fullyWriteLock() {
        lockAccounts.writeLock().lock();
        lockDetails.writeLock().lock();
    }

    /** Unlock to allow writing on both accounts and details. */
    protected void fullyWriteUnlock() {
        lockDetails.writeLock().unlock();
        lockAccounts.writeLock().unlock();
    }

    /** Lock for reading both accounts and details. */
    protected void fullyReadLock() {
        lockAccounts.readLock().lock();
        lockDetails.readLock().lock();
    }

    /** Unlock reading for both accounts and details. */
    protected void fullyReadUnlock() {
        lockDetails.readLock().unlock();
        lockAccounts.readLock().unlock();
    }

    @Override
    public boolean isSnapshot() {
        return repository.isSnapshot();
    }
}
