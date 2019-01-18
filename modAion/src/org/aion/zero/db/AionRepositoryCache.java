package org.aion.zero.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aion.base.db.IContractDetails;
import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.AbstractRepositoryCache;
import org.aion.mcf.db.ContractDetailsCacheImpl;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.vm.api.interfaces.Address;

public class AionRepositoryCache extends AbstractRepositoryCache<IBlockStoreBase<?, ?>> {

    public AionRepositoryCache(final IRepository trackedRepository) {
        this.repository = trackedRepository;
        this.cachedAccounts = new HashMap<>();
        this.cachedDetails = new HashMap<>();
    }

    @Override
    public IRepositoryCache startTracking() {
        return new AionRepositoryCache(this);
    }

    /**
     * @implNote To maintain intended functionality this method does not call the parent's {@code
     *     flush()} method. The changes are propagated to the parent through calling the parent's
     *     {@code updateBatch()} method.
     */
    @Override
    public void flush() {
        fullyWriteLock();
        try {
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
            for (Map.Entry<Address, IContractDetails> entry : cachedDetails.entrySet()) {
                IContractDetails ctd = entry.getValue();
                // TODO: this functionality will be improved with the switch to a
                // different ContractDetails implementation
                if (ctd != null && ctd instanceof ContractDetailsCacheImpl) {
                    ContractDetailsCacheImpl contractDetailsCache = (ContractDetailsCacheImpl) ctd;
                    contractDetailsCache.commit();

                    if (contractDetailsCache.origContract == null
                            && repository.hasContractDetails(entry.getKey())) {
                        // in forked block the contract account might not exist thus
                        // it is created without
                        // origin, but on the main chain details can contain data
                        // which should be merged
                        // into a single storage trie so both branches with
                        // different stateRoots are valid
                        contractDetailsCache.origContract =
                                repository.getContractDetails(entry.getKey());
                        contractDetailsCache.commit();
                    }
                }
            }

            repository.updateBatch(cleanedCacheAccounts, cachedDetails);
            cachedAccounts.clear();
            cachedDetails.clear();
        } finally {
            fullyWriteUnlock();
        }
    }

    @Override
    public void updateBatch(
            Map<Address, AccountState> accounts, final Map<Address, IContractDetails> details) {
        fullyWriteLock();
        try {

            for (Map.Entry<Address, AccountState> accEntry : accounts.entrySet()) {
                this.cachedAccounts.put(accEntry.getKey(), accEntry.getValue());
            }

            for (Map.Entry<Address, IContractDetails> ctdEntry : details.entrySet()) {
                ContractDetailsCacheImpl contractDetailsCache =
                        (ContractDetailsCacheImpl) ctdEntry.getValue();
                if (contractDetailsCache.origContract != null
                        && !(contractDetailsCache.origContract
                                instanceof AionContractDetailsImpl)) {
                    // Copying the parent because contract details changes were pushed to the parent
                    // in previous method (flush)
                    cachedDetails.put(
                            ctdEntry.getKey(),
                            ContractDetailsCacheImpl.copy(
                                    (ContractDetailsCacheImpl) contractDetailsCache.origContract));
                } else {
                    // Either no parent or we have Repo's AionContractDetailsImpl, which should be
                    // flushed through RepoImpl
                    cachedDetails.put(
                            ctdEntry.getKey(), ContractDetailsCacheImpl.copy(contractDetailsCache));
                }
            }
        } finally {
            fullyWriteUnlock();
        }
    }

    @Override
    public boolean isClosed() {
        // delegate to the tracked repository
        return repository.isClosed();
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException(
                "The tracking cache cannot be closed. \'Close\' should be called on the tracked repository.");
    }

    @Override
    public void compact() {
        throw new UnsupportedOperationException(
                "The tracking cache cannot be compacted. \'Compact\' should be called on the tracked repository.");
    }

    @Override
    public byte[] getRoot() {
        throw new UnsupportedOperationException(
                "The tracking cache cannot return the root. \'Get root\' should be called on the tracked repository.");
    }

    @Override
    public void syncToRoot(byte[] root) {
        throw new UnsupportedOperationException(
                "The tracking cache cannot sync to root. \'Sync to root\' should be called on the tracked repository.");
    }

    @Override
    public void addTxBatch(Map<byte[], byte[]> pendingTx, boolean isPool) {
        throw new UnsupportedOperationException(
                "addTxBatch should be called on the tracked repository.");
    }

    @Override
    public void removeTxBatch(Set<byte[]> pendingTx, boolean isPool) {
        throw new UnsupportedOperationException(
                "removeTxBatch should be called on the tracked repository.");
    }

    @Override
    public boolean isValidRoot(byte[] root) {
        return this.repository.isValidRoot(root);
    }

    @Override
    public boolean isIndexed(byte[] hash, long level) {
        return repository.isIndexed(hash, level);
    }

    @Override
    public List<byte[]> getPoolTx() {
        throw new UnsupportedOperationException(
                "getPoolTx should be called on the tracked repository.");
    }

    @Override
    public List<byte[]> getCacheTx() {
        throw new UnsupportedOperationException(
                "getCachelTx should be called on the tracked repository.");
    }
}
