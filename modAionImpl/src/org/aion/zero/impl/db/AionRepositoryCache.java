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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.base.AccountState;
import org.aion.mcf.db.ContractDetails;
import org.aion.mcf.db.InternalVmType;
import org.aion.mcf.db.Repository;
import org.aion.mcf.db.RepositoryCache;
import org.aion.mcf.db.TransformedCodeInfo;
import org.aion.precompiled.ContractInfo;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;
import org.slf4j.Logger;

public final class AionRepositoryCache implements RepositoryCache<AccountState> {

    // Logger
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    /** the repository being tracked */
    private final Repository<AccountState> repository;

    /** local accounts cache */
    private final Map<AionAddress, AccountState> cachedAccounts;
    /** local contract details cache */
    private final Map<AionAddress, ContractDetails> cachedDetails;
    /** local transformed code cache */
    private final Map<AionAddress, TransformedCodeInfo> cachedTransformedCode;

    private final Lock lock = new ReentrantLock();

    public AionRepositoryCache(final Repository trackedRepository) {
        this.repository = trackedRepository;
        this.cachedAccounts = new HashMap<>();
        this.cachedDetails = new HashMap<>();
        this.cachedTransformedCode = new HashMap<>();
    }

    @Override
    public RepositoryCache startTracking() {
        return new AionRepositoryCache(this);
    }

    @Override
    public void createAccount(AionAddress address) {
        lock.lock();
        try {
            AccountState accountState = new AccountState();
            cachedAccounts.put(address, accountState);

            // TODO: unify contract details initialization from Impl and Track
            ContractDetails contractDetails = new ContractDetailsCacheImpl(null);
            contractDetails.markAsDirty();
            cachedDetails.put(address, contractDetails);
        } finally {
            lock.unlock();
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
        lock.lock();

        try {
            // check if the account is cached locally
            AccountState accountState = this.cachedAccounts.get(address);

            // when the account is not cached load it from the repository
            if (accountState == null) {
                // must unlock to perform write operation from loadAccountState(address)
                loadAccountState(address);
                accountState = this.cachedAccounts.get(address);
            }

            return accountState;
        } finally {
            lock.unlock();
        }
    }

    public boolean hasAccountState(AionAddress address) {
        lock.lock();
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
            lock.unlock();
        }
    }

    @Override
    public ContractDetails getContractDetails(AionAddress address) {
        lock.lock();

        try {
            ContractDetails contractDetails = this.cachedDetails.get(address);

            if (contractDetails == null) {
                // loads the address into cache
                // must unlock to perform write operation from loadAccountState(address)
                loadAccountState(address);
                // retrieves the contract details
                contractDetails = this.cachedDetails.get(address);
            }

            return contractDetails;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean hasContractDetails(AionAddress address) {
        lock.lock();

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
            lock.unlock();
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
        lock.lock();

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
            lock.unlock();
        }
    }

    /**
     * Loads the state of the account into <b>this object's caches</b>.
     *
     * @implNote If the calling method has acquired a weaker lock, the lock must be released before
     *     calling this method.
     * @apiNote If the account was never stored this call will create it.
     */
    private void loadAccountState(AionAddress address) {
        lock.lock();
        try {
            repository.loadAccountState(address, this.cachedAccounts, this.cachedDetails);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void deleteAccount(AionAddress address) {
        lock.lock();
        try {
            getAccountState(address).delete();
            ContractDetails cd = getContractDetails(address);
            if (cd != null) {
                cd.delete();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public BigInteger incrementNonce(AionAddress address) {
        lock.lock();
        try {
            return getAccountState(address).incrementNonce();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public BigInteger setNonce(AionAddress address, BigInteger newNonce) {
        lock.lock();
        try {
            return getAccountState(address).setNonce(newNonce);
        } finally {
            lock.unlock();
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
        lock.lock();
        try {
            // TODO: where do we ensure that this does not result in a negative value?
            AccountState accountState = getAccountState(address);
            return accountState.addToBalance(value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void saveCode(AionAddress address, byte[] code) {
        lock.lock();
        try {
            // save the code
            // TODO: why not create contract here directly? also need to check that there is no
            // preexisting code!
            ContractDetails contractDetails = getContractDetails(address);
            contractDetails.setCode(code);

            // update the code hash
            getAccountState(address).setCodeHash(h256(code));
        } finally {
            lock.unlock();
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
    public byte[] getTransformedCode(AionAddress address, byte[] codeHash, int avmVersion) {
        lock.lock();

        try {
            TransformedCodeInfo transformedCodeInfo = cachedTransformedCode.get(address);
            byte[] transformedCode = null;

            if (transformedCodeInfo != null) {
                transformedCode = transformedCodeInfo.getTransformedCode(ByteArrayWrapper.wrap(codeHash), avmVersion);
            }
            // If we don't find it in the cache, go to the underlying repo
            return transformedCode != null ? transformedCode : repository.getTransformedCode(address, codeHash, avmVersion);
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void setTransformedCode(AionAddress address, byte[] codeHash, int avmVersion, byte[] transformedCode) {
        if (address == null || codeHash == null || transformedCode == null) {
            throw new NullPointerException();
        }
        lock.lock();

        try {
            TransformedCodeInfo transformedCodeInfo = cachedTransformedCode.get(address);

            if (transformedCodeInfo == null) {
                transformedCodeInfo = new TransformedCodeInfo();
            }
            transformedCodeInfo.add(ByteArrayWrapper.wrap(codeHash), avmVersion, transformedCode);
            cachedTransformedCode.put(address, transformedCodeInfo);
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void saveVmType(AionAddress contract, InternalVmType vmType) {
        lock.lock();
        try {
            getContractDetails(contract).setVmType(vmType);
        } finally {
            lock.unlock();
        }
    }

    /** IMPORTNAT: a new cache must be created before calling this method */
    @Override
    public InternalVmType getVmType(AionAddress contract) {
        if (ContractInfo.isPrecompiledContract(contract)) {
            // skip the call to disk
            return InternalVmType.FVM;
        }
        // retrieving the VM type involves updating the contract details values
        // this requires loading the account and details
        lock.lock();
        try {
            return getContractDetails(contract).getVmType();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void saveObjectGraph(AionAddress contract, byte[] graph) {
        // TODO: unsure about impl
        lock.lock();
        try {
            // this change will mark the contract as dirty (requires update in the db)
            ContractDetails contractDetails = getContractDetails(contract);
            contractDetails.setObjectGraph(graph);

            // update the storage hash
            getAccountState(contract).setStateRoot(contractDetails.getStorageHash());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public byte[] getObjectGraph(AionAddress contract) {
        lock.lock();
        try {
            return getContractDetails(contract).getObjectGraph();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void addStorageRow(AionAddress address, ByteArrayWrapper key, ByteArrayWrapper value) {
        lock.lock();
        try {
            getContractDetails(address).put(key, value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeStorageRow(AionAddress address, ByteArrayWrapper key) {
        lock.lock();
        try {
            getContractDetails(address).delete(key);
        } finally {
            lock.unlock();
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
        lock.lock();
        try {
            cachedAccounts.clear();
            cachedDetails.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Repository getSnapshotTo(byte[] root) {
        return repository.getSnapshotTo(root);
    }

    @Override
    public byte[] getBlockHashByNumber(long blockNumber) {
        return repository.getBlockHashByNumber(blockNumber);
    }

    @Override
    public boolean isSnapshot() {
        return repository.isSnapshot();
    }

    @Override
    public void flushTo(Repository other, boolean clearStateAfterFlush) {
        lock.lock();
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

            other.updateBatch(cleanedCacheAccounts, cachedDetails, cachedTransformedCode);
            if (clearStateAfterFlush) {
                cachedAccounts.clear();
                cachedDetails.clear();
                cachedTransformedCode.clear();
            }
        } finally {
            lock.unlock();
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
            Map<AionAddress, AccountState> accounts,
            final Map<AionAddress, ContractDetails> details,
            Map<AionAddress, TransformedCodeInfo> transformedCodeCache) {

        lock.lock();
        try {

            for (Map.Entry<AionAddress, AccountState> accEntry : accounts.entrySet()) {
                this.cachedAccounts.put(accEntry.getKey(), accEntry.getValue());
            }

            for (Map.Entry<AionAddress, TransformedCodeInfo> entry : transformedCodeCache.entrySet()) {
                for (Map.Entry<ByteArrayWrapper, Map<Integer, byte[]>> infoMap : entry.getValue().transformedCodeMap.entrySet()) {
                    for (Map.Entry<Integer, byte[]> innerEntry : infoMap.getValue().entrySet()) {
                        setTransformedCode(entry.getKey(), infoMap.getKey().toBytes(), innerEntry.getKey(), innerEntry.getValue());
                    }
                }
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
            lock.unlock();
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

    public InternalVmType getVMUsed(AionAddress contract, byte[] codeHash) {
        return repository.getVMUsed(contract, codeHash);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();

        s.append("cachedAccounts [");

        if (cachedAccounts != null && !cachedAccounts.isEmpty()) {
            s.append("\n");
            for (Map.Entry<AionAddress, AccountState> ca : cachedAccounts.entrySet()) {
                s.append(ca.getKey()).append("\n").append(ca.getValue()).append("\n");
            }
        } else {
            s.append("]\n");
        }

        s.append("cachedDetails [");
        if (cachedDetails != null && !cachedDetails.isEmpty()) {
            s.append("\n");
            for (Map.Entry<AionAddress, ContractDetails> ca : cachedDetails.entrySet()) {
                s.append(ca.getKey()).append("\n").append(ca.getValue()).append("\n");
            }
        } else {
            s.append("]\n");
        }

        return s.toString();
    }
}
