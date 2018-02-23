/*******************************************************************************
 *
 * Copyright (c) 2017, 2018 Aion foundation.
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 *******************************************************************************/

package org.aion.mcf.db;

import org.aion.base.db.IContractDetails;
import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.vm.types.DataWord;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.aion.base.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.aion.crypto.HashUtil.h256;

/**
 * Abstract repository cache.
 *
 * @author Alexandra Roatis
 */
public abstract class AbstractRepositoryCache<BSB extends IBlockStoreBase<?, ?>>
        implements IRepositoryCache<AccountState, DataWord, BSB> {

    // Logger
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    /**
     * the repository being tracked
     */
    protected IRepository<AccountState, DataWord, BSB> repository;

    /**
     * local accounts cache
     */
    protected Map<Address, AccountState> cachedAccounts;
    /**
     * local contract details cache
     */
    protected Map<Address, IContractDetails<DataWord>> cachedDetails;

    @Override
    public synchronized AccountState createAccount(Address address) {

        AccountState accountState = new AccountState();
        cachedAccounts.put(address, accountState);

        // TODO: unify contract details initialization from Impl and Track
        IContractDetails<DataWord> contractDetails = new ContractDetailsCacheImpl(null);
        // TODO: refactor to use makeDirty() from AbstractState
        contractDetails.setDirty(true);
        cachedDetails.put(address, contractDetails);

        return accountState;
    }

    /**
     * Retrieves the current state of the account associated with the given
     * address.
     *
     * @param address
     *         the address of the account of interest
     * @return a {@link AccountState} object representing the account state as
     * is stored in the database or cache
     * @implNote If there is no account associated with the given address, it
     * will create it.
     */
    @Override
    public synchronized AccountState getAccountState(Address address) {

        // check if the account is cached locally
        AccountState accountState = this.cachedAccounts.get(address);

        // when the account is not cached load it from the repository
        if (accountState == null) {
            // note that the call below will create the account if never stored
            this.repository.loadAccountState(address, this.cachedAccounts, this.cachedDetails);
            accountState = this.cachedAccounts.get(address);
        }

        return accountState;
    }

    public synchronized boolean hasAccountState(Address address) {
        AccountState accountState = cachedAccounts.get(address);

        if (accountState != null) {
            // checks that the account is not cached as deleted
            // TODO: may also need to check if the state is empty
            return !accountState.isDeleted();
        } else {
            // check repository when not cached
            return repository.hasAccountState(address);
        }
    }

    @Override
    public synchronized IContractDetails<DataWord> getContractDetails(Address address) {
        IContractDetails<DataWord> contractDetails = this.cachedDetails.get(address);

        if (contractDetails == null) {
            // loads the address into cache
            this.repository.loadAccountState(address, this.cachedAccounts, this.cachedDetails);
            // retrieves the contract details
            contractDetails = this.cachedDetails.get(address);
        }

        return contractDetails;
    }

    @Override
    public synchronized boolean hasContractDetails(Address address) {
        IContractDetails<DataWord> contractDetails = cachedDetails.get(address);

        if (contractDetails == null) {
            // ask repository when not cached
            return repository.hasContractDetails(address);
        } else {
            // TODO: may also need to check if the details are empty
            return !contractDetails.isDeleted();
        }
    }

    /**
     * @implNote The loaded objects are fresh copies of the locally cached
     * account state and contract details.
     */
    @Override
    public synchronized void loadAccountState(Address address, Map<Address, AccountState> accounts,
            Map<Address, IContractDetails<DataWord>> details) {

        // check if the account is cached locally
        AccountState accountState = this.cachedAccounts.get(address);
        IContractDetails<DataWord> contractDetails = this.cachedDetails.get(address);

        // when account not cached load from repository
        if (accountState == null) {
            // load directly to the caches given as parameters
            repository.loadAccountState(address, accounts, details);
        } else {
            // copy the objects if they were cached locally
            accounts.put(address, new AccountState(accountState));
            details.put(address, new ContractDetailsCacheImpl(contractDetails));
        }
    }

    @Override
    public synchronized void deleteAccount(Address address) {
        getAccountState(address).delete();
        getContractDetails(address).setDeleted(true);
    }

    @Override
    public synchronized BigInteger incrementNonce(Address address) {
        return getAccountState(address).incrementNonce();
    }

    @Override
    public synchronized BigInteger setNonce(Address address, BigInteger newNonce) {
        return getAccountState(address).setNonce(newNonce);
    }

    @Override
    public BigInteger getNonce(Address address) {
        AccountState accountState = getAccountState(address);
        // account state can never be null, but may be empty or deleted
        return (accountState.isEmpty() || accountState.isDeleted()) ? BigInteger.ZERO : accountState.getNonce();
    }

    @Override
    public BigInteger getBalance(Address address) {
        AccountState accountState = getAccountState(address);
        // account state can never be null, but may be empty or deleted
        return (accountState.isEmpty() || accountState.isDeleted()) ? BigInteger.ZERO : accountState.getBalance();
    }

    @Override
    public synchronized BigInteger addBalance(Address address, BigInteger value) {

        // TODO: where do we ensure that this does not result in a negative
        // value?
        AccountState accountState = getAccountState(address);
        return accountState.addToBalance(value);
    }

    @Override
    public synchronized void saveCode(Address address, byte[] code) {

        // save the code
        // TODO: why not create contract here directly? also need to check that
        // there is no preexisting code!
        IContractDetails<DataWord> contractDetails = getContractDetails(address);
        contractDetails.setCode(code);
        // TODO: ensure that setDirty is done by the class itself
        contractDetails.setDirty(true);

        // update the code hash
        getAccountState(address).setCodeHash(h256(code));
    }

    @Override
    public synchronized byte[] getCode(Address address) {

        if (!hasAccountState(address)) {
            return EMPTY_BYTE_ARRAY;
        }

        byte[] codeHash = getAccountState(address).getCodeHash();

        // TODO: why use codeHash here? may require refactoring
        return getContractDetails(address).getCode(codeHash);
    }

    @Override
    public synchronized void addStorageRow(Address address, DataWord key, DataWord value) {
        getContractDetails(address).put(key, value);
    }

    @Override
    public synchronized DataWord getStorageValue(Address address, DataWord key) {
        return getContractDetails(address).get(key);
    }

    @Override
    public synchronized int getStorageSize(Address address) {
        IContractDetails<DataWord> details = getContractDetails(address);
        return (details == null) ? 0 : details.getStorageSize();
    }

    @Override
    public synchronized Set<DataWord> getStorageKeys(Address address) {
        IContractDetails<DataWord> details = getContractDetails(address);
        return (details == null) ? Collections.emptySet() : details.getStorageKeys();
    }

    @Override
    public synchronized Map<DataWord, DataWord> getStorage(Address address, Collection<DataWord> keys) {
        IContractDetails<DataWord> details = getContractDetails(address);
        return (details == null) ? Collections.emptyMap() : details.getStorage(keys);
    }

    @Override
    public void rollback() {
        cachedAccounts.clear();
        cachedDetails.clear();
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
}
