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
 *
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.zero.db;

import org.aion.base.db.IContractDetails;
import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.AbstractRepositoryCache;
import org.aion.mcf.db.ContractDetailsCacheImpl;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;

import java.util.HashMap;
import java.util.Map;

public class AionRepositoryCache extends AbstractRepositoryCache<IBlockStoreBase<?, ?>> {

    public AionRepositoryCache(final IRepository trackedRepository) {
        this.repository = trackedRepository;
        this.cachedAccounts = new HashMap<>();
        this.cachedDetails = new HashMap<>();
    }

    @Override
    public synchronized IRepositoryCache startTracking() {
        return new AionRepositoryCache(this);
    }

    /**
     * @implNote To maintain intended functionality this method does not call
     *           the parent's {@code flush()} method. The changes are propagated
     *           to the parent through calling the parent's
     *           {@code updateBatch()} method.
     */
    @Override
    public synchronized void flush() {

        // determine which accounts should get stored
        HashMap<Address, AccountState> cleanedCacheAccounts = new HashMap<>();
        for (Map.Entry<Address, AccountState> entry : cachedAccounts.entrySet()) {
            AccountState account = entry.getValue();
            if (account != null && account.isDirty() && account.isEmpty()) {
                // ignore contract state for empty accounts at storage
                cachedDetails.remove(entry.getKey());
            } else {
                cleanedCacheAccounts.put(entry.getKey(), entry.getValue());
            }
        }

        // determine which contracts should get stored
        for (Map.Entry<Address, IContractDetails<DataWord>> entry : cachedDetails.entrySet()) {
            IContractDetails<DataWord> ctd = entry.getValue();
            // TODO: this functionality will be improved with the switch to a
            // different ContractDetails implementation
            if (ctd != null && ctd instanceof ContractDetailsCacheImpl) {
                ContractDetailsCacheImpl contractDetailsCache = (ContractDetailsCacheImpl) ctd;
                contractDetailsCache.commit();

                if (contractDetailsCache.origContract == null && repository.hasContractDetails(entry.getKey())) {
                    // in forked block the contract account might not exist thus
                    // it is created without
                    // origin, but on the main chain details can contain data
                    // which should be merged
                    // into a single storage trie so both branches with
                    // different stateRoots are valid
                    contractDetailsCache.origContract = repository.getContractDetails(entry.getKey());
                    contractDetailsCache.commit();
                }
            }
        }

        repository.updateBatch(cleanedCacheAccounts, cachedDetails);

        cachedAccounts.clear();
        cachedDetails.clear();
    }

    @Override
    public synchronized void updateBatch(Map<Address, AccountState> accounts,
            Map<Address, IContractDetails<DataWord>> details) {

        for (Map.Entry<Address, AccountState> accEntry : accounts.entrySet()) {
            this.cachedAccounts.put(accEntry.getKey(), accEntry.getValue());
        }

        for (Map.Entry<Address, IContractDetails<DataWord>> ctdEntry : details.entrySet()) {
            ContractDetailsCacheImpl contractDetailsCache = (ContractDetailsCacheImpl) ctdEntry.getValue();
            if (contractDetailsCache.origContract != null
                    && !(contractDetailsCache.origContract instanceof AionContractDetailsImpl)) {
                // TODO: what's the purpose of this implementation?
                cachedDetails.put(ctdEntry.getKey(), contractDetailsCache.origContract);
            } else {
                cachedDetails.put(ctdEntry.getKey(), contractDetailsCache);
            }
        }
    }

    @Override
    public boolean isClosed() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void close() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public byte[] getRoot() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void syncToRoot(byte[] root) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public boolean isValidRoot(byte[] root) {
        return this.repository.isValidRoot(root);
    }
}