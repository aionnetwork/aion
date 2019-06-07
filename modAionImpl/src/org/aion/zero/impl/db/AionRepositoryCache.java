package org.aion.zero.impl.db;

import static org.aion.crypto.HashUtil.h256;
import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.aion.types.AionAddress;
import org.aion.interfaces.db.ByteArrayKeyValueStore;
import org.aion.interfaces.db.ContractDetails;
import org.aion.interfaces.db.InternalVmType;
import org.aion.interfaces.db.Repository;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.ContractFactory;
import org.aion.vm.api.types.ByteArrayWrapper;
import org.slf4j.Logger;

public class AionRepositoryCache implements RepositoryCache<AccountState, IBlockStoreBase<?, ?>> {

    // Logger
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    /** the repository being tracked */
    protected Repository<AccountState, IBlockStoreBase<?, ?>> repository;

    /** local accounts cache */
    protected Map<AionAddress, AccountState> cachedAccounts;

    protected ReadWriteLock lockAccounts = new ReentrantReadWriteLock();
    /** local contract details cache */
    protected Map<AionAddress, ContractDetails> cachedDetails;

    protected ReadWriteLock lockDetails = new ReentrantReadWriteLock();

    public AionRepositoryCache(final Repository trackedRepository) {
        this.repository = trackedRepository;
        this.cachedAccounts = new HashMap<>();
        this.cachedDetails = new HashMap<>();
    }

    @Override
    public RepositoryCache startTracking() {
        return new AionRepositoryCache(this);
    }

    @Override
    public AccountState createAccount(AionAddress address) {
        fullyWriteLock();
        try {
            AccountState accountState = new AccountState();
            cachedAccounts.put(address, accountState);

            // TODO: unify contract details initialization from Impl and Track
            ContractDetails contractDetails = new ContractDetailsCacheImpl(null);
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
    public AccountState getAccountState(AionAddress address) {
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

    public boolean hasAccountState(AionAddress address) {
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
    public ContractDetails getContractDetails(AionAddress address) {
        lockDetails.readLock().lock();

        try {
            ContractDetails contractDetails = this.cachedDetails.get(address);

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
    public boolean hasContractDetails(AionAddress address) {
        lockDetails.readLock().lock();

        try {
            ContractDetails contractDetails = cachedDetails.get(address);

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
            AionAddress address,
            Map<AionAddress, AccountState> accounts,
            Map<AionAddress, ContractDetails> details) {
        fullyReadLock();

        try {
            // check if the account is cached locally
            AccountState accountState = this.cachedAccounts.get(address);
            ContractDetails contractDetails = this.cachedDetails.get(address);

            // when account not cached load from repository
            if (accountState == null) {
                // load directly to the caches given as parameters
                repository.loadAccountState(address, accounts, details);
            } else {
                // copy the objects if they were cached locally
                accounts.put(address, new AccountState(accountState));
                ContractDetails cd = new ContractDetailsCacheImpl(contractDetails);
                details.put(address, cd);
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
    private void loadAccountState(AionAddress address) {
        fullyWriteLock();
        try {
            repository.loadAccountState(address, this.cachedAccounts, this.cachedDetails);
        } finally {
            fullyWriteUnlock();
        }
    }

    @Override
    public void deleteAccount(AionAddress address) {
        fullyWriteLock();
        try {
            getAccountState(address).delete();
            ContractDetails cd = getContractDetails(address);
            if (cd != null) {
                cd.setTransformedCode(null);
                cd.setDeleted(true);
            }
        } finally {
            fullyWriteUnlock();
        }
    }

    @Override
    public BigInteger incrementNonce(AionAddress address) {
        lockAccounts.writeLock().lock();
        try {
            return getAccountState(address).incrementNonce();
        } finally {
            lockAccounts.writeLock().unlock();
        }
    }

    @Override
    public BigInteger setNonce(AionAddress address, BigInteger newNonce) {
        lockAccounts.writeLock().lock();
        try {
            return getAccountState(address).setNonce(newNonce);
        } finally {
            lockAccounts.writeLock().unlock();
        }
    }

    @Override
    public BigInteger getNonce(AionAddress address) {
        AccountState accountState = getAccountState(address);
        // account state can never be null, but may be empty or deleted
        return (accountState.isEmpty() || accountState.isDeleted())
                ? BigInteger.ZERO
                : accountState.getNonce();
    }

    @Override
    public BigInteger getBalance(AionAddress address) {
        AccountState accountState = getAccountState(address);
        // account state can never be null, but may be empty or deleted
        return (accountState.isEmpty() || accountState.isDeleted())
                ? BigInteger.ZERO
                : accountState.getBalance();
    }

    @Override
    public BigInteger addBalance(AionAddress address, BigInteger value) {
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
    public void saveCode(AionAddress address, byte[] code) {
        fullyWriteLock();
        try {
            // save the code
            // TODO: why not create contract here directly? also need to check that there is no
            // preexisting code!
            ContractDetails contractDetails = getContractDetails(address);
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
    public byte[] getCode(AionAddress address) {
        if (!hasAccountState(address)) {
            return EMPTY_BYTE_ARRAY;
        }

        byte[] codeHash = getAccountState(address).getCodeHash();

        // TODO: why use codeHash here? may require refactoring
        return getContractDetails(address).getCode(codeHash);
    }

    @Override
    public byte[] getTransformedCode(AionAddress address) {
        if (!hasAccountState(address)) {
            return null;
        }

        return getContractDetails(address).getTransformedCode();
    }

    @Override
    public void saveVmType(AionAddress contract, InternalVmType vmType) {
        fullyWriteLock();
        try {
            getContractDetails(contract).setVmType(vmType);
        } finally {
            fullyWriteUnlock();
        }
    }

    /** IMPORTNAT: a new cache must be created before calling this method */
    @Override
    public InternalVmType getVmType(AionAddress contract) {
        if (ContractFactory.isPrecompiledContract(contract)) {
            // skip the call to disk
            return InternalVmType.FVM;
        }
        // retrieving the VM type involves updating the contract details values
        // this requires loading the account and details
        fullyWriteLock();
        try {
            return getContractDetails(contract).getVmType();
        } finally {
            fullyWriteUnlock();
        }
    }

    @Override
    public void saveObjectGraph(AionAddress contract, byte[] graph) {
        // TODO: unsure about impl
        fullyWriteLock();
        try {
            // this change will mark the contract as dirty (requires update in the db)
            ContractDetails contractDetails = getContractDetails(contract);
            contractDetails.setObjectGraph(graph);

            // update the storage hash
            getAccountState(contract).setStateRoot(contractDetails.getStorageHash());
        } finally {
            fullyWriteUnlock();
        }
    }

    @Override
    public byte[] getObjectGraph(AionAddress contract) {
        fullyWriteLock();
        try {
            return getContractDetails(contract).getObjectGraph();
        } finally {
            fullyWriteUnlock();
        }
    }

    @Override
    public void addStorageRow(AionAddress address, ByteArrayWrapper key, ByteArrayWrapper value) {
        lockDetails.writeLock().lock();
        try {
            getContractDetails(address).put(key, value);
        } finally {
            lockDetails.writeLock().unlock();
        }
    }

    @Override
    public void removeStorageRow(AionAddress address, ByteArrayWrapper key) {
        lockDetails.writeLock().lock();
        try {
            getContractDetails(address).delete(key);
        } finally {
            lockDetails.writeLock().unlock();
        }
    }

    @Override
    public ByteArrayWrapper getStorageValue(AionAddress address, ByteArrayWrapper key) {
        return getContractDetails(address).get(key);
    }

    @Override
    public Map<ByteArrayWrapper, ByteArrayWrapper> getStorage(
            AionAddress address, Collection<ByteArrayWrapper> keys) {
        ContractDetails details = getContractDetails(address);
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
    public Repository getSnapshotTo(byte[] root) {
        return repository.getSnapshotTo(root);
    }

    // This method was originally disabled because changes to the blockstore
    // can not be reverted. The reason for re-enabling it is that we have no way
    // to
    // get a reference of the blockstore without using
    // NProgInvoke/NcpProgInvoke.
    @Override
    public IBlockStoreBase<?, ?> getBlockStore() {
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

    /**
     * Flushes its state to other in such a manner that other receives sufficiently deep copies of
     * its {@link AccountState} and {@link ContractDetails} objects.
     *
     * <p>If {@code clearStateAfterFlush == true} then this repository's state will be completely
     * cleared after this method returns, otherwise it will retain all of its state.
     *
     * <p>A "sufficiently deep copy" is an imperfect deep copy (some original object references get
     * leaked) but such that for all conceivable use cases these imperfections should go unnoticed.
     * This is because doing something like copying the underlying data store makes no sense, both
     * repositories should be accessing it, and there are some other cases where objects are defined
     * as type {@link Object} and are cast to their expected types and copied, but will not be
     * copied if they are not in fact their expected types. This is something to be aware of. Most
     * of the imperfection results from the inability to copy {@link ByteArrayKeyValueStore} and
     * {@link org.aion.mcf.trie.SecureTrie} perfectly or at all (in the case of the former), for the
     * above reasons.
     *
     * @param other The repository that will consume the state of this repository.
     * @param clearStateAfterFlush True if this repository should clear its state after flushing.
     */
    public void flushCopiesTo(Repository other, boolean clearStateAfterFlush) {
        fullyWriteLock();
        try {
            // determine which accounts should get stored
            HashMap<AionAddress, AccountState> cleanedCacheAccounts = new HashMap<>();
            for (Map.Entry<AionAddress, AccountState> entry : cachedAccounts.entrySet()) {
                AccountState account = entry.getValue().copy();
                if (account != null && account.isDirty() && account.isEmpty()) {
                    // ignore contract state for empty accounts at storage
                    cachedDetails.remove(entry.getKey());
                } else {
                    cleanedCacheAccounts.put(entry.getKey(), account);
                }
            }
            // determine which contracts should get stored
            for (Map.Entry<AionAddress, ContractDetails> entry : cachedDetails.entrySet()) {
                ContractDetails ctd = entry.getValue().copy();
                // TODO: this functionality will be improved with the switch to a
                // different ContractDetails implementation
                if (ctd != null && ctd instanceof ContractDetailsCacheImpl) {
                    ContractDetailsCacheImpl contractDetailsCache = (ContractDetailsCacheImpl) ctd;
                    contractDetailsCache.commit();

                    if (contractDetailsCache.origContract == null
                            && other.hasContractDetails(entry.getKey())) {
                        // in forked block the contract account might not exist thus
                        // it is created without
                        // origin, but on the main chain details can contain data
                        // which should be merged
                        // into a single storage trie so both branches with
                        // different stateRoots are valid
                        contractDetailsCache.origContract =
                                other.getContractDetails(entry.getKey()).copy();
                        contractDetailsCache.commit();
                    }
                }
            }

            other.updateBatch(cleanedCacheAccounts, cachedDetails);
            if (clearStateAfterFlush) {
                cachedAccounts.clear();
                cachedDetails.clear();
            }
        } finally {
            fullyWriteUnlock();
        }
    }

    @Override
    public void flushTo(Repository other, boolean clearStateAfterFlush) {
        fullyWriteLock();
        try {
            // determine which accounts should get stored
            HashMap<AionAddress, AccountState> cleanedCacheAccounts = new HashMap<>();
            for (Map.Entry<AionAddress, AccountState> entry : cachedAccounts.entrySet()) {
                AccountState account = entry.getValue();
                if (account != null && account.isDirty() && account.isEmpty()) {
                    // ignore contract state for empty accounts at storage
                    cachedDetails.remove(entry.getKey());
                } else {
                    cleanedCacheAccounts.put(entry.getKey(), entry.getValue());
                }
            }
            // determine which contracts should get stored
            for (Map.Entry<AionAddress, ContractDetails> entry : cachedDetails.entrySet()) {
                ContractDetails ctd = entry.getValue();
                // TODO: this functionality will be improved with the switch to a
                // different ContractDetails implementation
                if (ctd instanceof ContractDetailsCacheImpl) {
                    ContractDetailsCacheImpl contractDetailsCache = (ContractDetailsCacheImpl) ctd;
                    contractDetailsCache.commit();

                    if (contractDetailsCache.origContract == null
                            && other.hasContractDetails(entry.getKey())) {
                        // in forked block the contract account might not exist thus
                        // it is created without
                        // origin, but on the main chain details can contain data
                        // which should be merged
                        // into a single storage trie so both branches with
                        // different stateRoots are valid
                        contractDetailsCache.origContract =
                                other.getContractDetails(entry.getKey());
                        contractDetailsCache.commit();
                    }
                }
            }

            other.updateBatch(cleanedCacheAccounts, cachedDetails);
            if (clearStateAfterFlush) {
                cachedAccounts.clear();
                cachedDetails.clear();
            }
        } finally {
            fullyWriteUnlock();
        }
    }

    /**
     * @implNote To maintain intended functionality this method does not call the parent's {@code
     *     flush()} method. The changes are propagated to the parent through calling the parent's
     *     {@code updateBatch()} method.
     */
    @Override
    public void flush() {
        flushTo(repository, true);
    }

    @Override
    public void updateBatch(
            Map<AionAddress, AccountState> accounts, final Map<AionAddress, ContractDetails> details) {
        fullyWriteLock();
        try {

            for (Map.Entry<AionAddress, AccountState> accEntry : accounts.entrySet()) {
                this.cachedAccounts.put(accEntry.getKey(), accEntry.getValue());
            }

            for (Map.Entry<AionAddress, ContractDetails> ctdEntry : details.entrySet()) {
                ContractDetailsCacheImpl contractDetailsCache =
                        (ContractDetailsCacheImpl) ctdEntry.getValue().copy();
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

    public InternalVmType getVMUsed(AionAddress contract) {
        return repository.getVMUsed(contract);
    }

    @Override
    public void setTransformedCode(AionAddress contractAddr, byte[] code) {
        if (contractAddr == null || code == null) {
            throw new NullPointerException();
        }

        fullyWriteLock();
        try {
            if (!hasAccountState(contractAddr)) {
                LOG.debug("No accountState of the account: {}", contractAddr);
                return;
            }

            ContractDetails cd = getContractDetails(contractAddr);
            if (cd == null) {
                LOG.debug("No contract detail of account: {}", contractAddr);
                return;
            }

            cd.setTransformedCode(code);
            cd.setDirty(true);
        } finally {
            fullyWriteUnlock();
        }
    }
}
