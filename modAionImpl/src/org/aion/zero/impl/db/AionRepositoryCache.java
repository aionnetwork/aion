package org.aion.zero.impl.db;

import static java.util.stream.Collectors.toMap;
import static org.aion.crypto.HashUtil.h256;
import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import com.google.common.annotations.VisibleForTesting;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

public final class AionRepositoryCache implements RepositoryCache<AccountState> {

    // Logger
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.DB.name());

    /** the repository being tracked */
    private final AionRepositoryImpl repository;

    /** local accounts cache */
    @VisibleForTesting
    final Map<AionAddress, AccountState> cachedAccounts;
    /** local contract details cache */
    @VisibleForTesting
    final Map<AionAddress, InnerContractDetails> cachedDetails;
    /** local transformed code cache */
    private final Map<AionAddress, TransformedCodeInfo> cachedTransformedCode;

    private final Lock lock = new ReentrantLock();

    public AionRepositoryCache(final AionRepositoryCache trackedRepository) {
        this.repository = trackedRepository.repository;
        this.cachedAccounts = trackedRepository.cachedAccounts.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> new AccountState(e.getValue())));
        this.cachedDetails = trackedRepository.cachedDetails.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> new InnerContractDetails(e.getValue())));
        this.cachedTransformedCode = new HashMap<>();
        for (Map.Entry<AionAddress, TransformedCodeInfo> entry : trackedRepository.cachedTransformedCode.entrySet()) {
            for (Map.Entry<ByteArrayWrapper, Map<Integer, byte[]>> infoMap : entry.getValue().transformedCodeMap.entrySet()) {
                for (Map.Entry<Integer, byte[]> innerEntry : infoMap.getValue().entrySet()) {
                    setTransformedCode(entry.getKey(), infoMap.getKey().toBytes(), innerEntry.getKey(), innerEntry.getValue());
                }
            }
        }
    }

    public AionRepositoryCache(final AionRepositoryImpl trackedRepository) {
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
    public Repository getParent() {
        return this.repository;
    }

    @Override
    public void createAccount(AionAddress address) {
        lock.lock();
        try {
            AccountState accountState = new AccountState();
            cachedAccounts.put(address, accountState);

            // TODO: unify contract details initialization from Impl and Track
            InnerContractDetails contractDetails = new InnerContractDetails(null);
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
            return getLocalAccountState(address);
        } finally {
            lock.unlock();
        }
    }

    private AccountState getLocalAccountState(AionAddress address) {
        // check if the account is cached locally
        AccountState accountState = this.cachedAccounts.get(address);

        // when the account is not cached load it from the repository
        if (accountState == null) {
            Pair<AccountState, InnerContractDetails> pair = getAccountStateFromParent(address);
            this.cachedAccounts.put(address, pair.getLeft());
            this.cachedDetails.put(address, pair.getRight());
            accountState = pair.getLeft();
        }

        return accountState;
    }

    /**
     * Returns {@code true} only if the specified account has non-empty storage associated with it. Otherwise {@code false}.
     *
     * @param address The account address.
     * @return whether the account has non-empty storage or not.
     */
    @Override
    public boolean hasStorage(AionAddress address) {
        lock.lock();

        try {
            return getLocalAccountState(address).hasStorage() || getInnerContractDetails(address).hasStorage();
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
            return getInnerContractDetails(address);
        } finally {
            lock.unlock();
        }
    }

    private InnerContractDetails getInnerContractDetails(AionAddress address) {
        InnerContractDetails contractDetails = this.cachedDetails.get(address);

        if (contractDetails == null) {
            Pair<AccountState, InnerContractDetails> pair = getAccountStateFromParent(address);
            this.cachedAccounts.put(address, pair.getLeft());
            this.cachedDetails.put(address, pair.getRight());
            contractDetails = pair.getRight();
        }

        return contractDetails;
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
     * Returns copies of the {@link AccountState} and {@link ContractDetails} from the closest
     * ancestor.
     *
     * @param address the address for the account of interest
     * @return a pair of objects representing copies of the {@link AccountState} and {@link
     *     ContractDetails} as they are known to the closest ancestor repository
     */
    private Pair<AccountState, InnerContractDetails> getAccountStateFromParent(AionAddress address) {
        AccountState account = repository.getAccountState(address);
        InnerContractDetails details;
        if (account != null) {
            account = new AccountState(account);
            details = new InnerContractDetails(repository.getContractDetails(address));
        } else {
            account = new AccountState();
            details = new InnerContractDetails(null);
        }
        return Pair.of(account, details);
    }

    @Override
    public void deleteAccount(AionAddress address) {
        lock.lock();
        try {
            getLocalAccountState(address).delete();
            getInnerContractDetails(address).delete();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public BigInteger incrementNonce(AionAddress address) {
        lock.lock();
        try {
            return getLocalAccountState(address).incrementNonce();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public BigInteger setNonce(AionAddress address, BigInteger newNonce) {
        lock.lock();
        try {
            return getLocalAccountState(address).setNonce(newNonce);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public BigInteger getNonce(AionAddress address) {
        lock.lock();
        try {
            AccountState accountState = getLocalAccountState(address);
            // account state can never be null, but may be empty or deleted
            return (accountState.isEmpty() || accountState.isDeleted())
                    ? BigInteger.ZERO
                    : accountState.getNonce();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public BigInteger getBalance(AionAddress address) {
        lock.lock();
        try {
            AccountState accountState = getLocalAccountState(address);
            // account state can never be null, but may be empty or deleted
            return (accountState.isEmpty() || accountState.isDeleted())
                    ? BigInteger.ZERO
                    : accountState.getBalance();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public BigInteger addBalance(AionAddress address, BigInteger value) {
        lock.lock();
        try {
            // TODO: where do we ensure that this does not result in a negative value?
            AccountState accountState = getLocalAccountState(address);
            return accountState.addToBalance(value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void saveCode(AionAddress address, byte[] code) {
        lock.lock();
        try {
            getInnerContractDetails(address).setCode(code);

            // update the code hash
            getLocalAccountState(address).setCodeHash(h256(code));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public byte[] getCode(AionAddress address) {
        lock.lock();

        try {
            if (!hasAccountState(address)) {
                return EMPTY_BYTE_ARRAY;
            }

            byte[] codeHash = getLocalAccountState(address).getCodeHash();
            return getInnerContractDetails(address).getCode(codeHash);
        } finally {
            lock.unlock();
        }
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
            getInnerContractDetails(contract).setVmType(vmType);
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
            return getInnerContractDetails(contract).getVmType();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void saveObjectGraph(AionAddress contract, byte[] graph) {
        lock.lock();
        try {
            // this change will mark the contract as dirty (requires update in the db)
            InnerContractDetails details = getInnerContractDetails(contract);
            details.setObjectGraph(graph);

            // update the storage hash
            getLocalAccountState(contract).setStateRoot(details.getStorageHash());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public byte[] getObjectGraph(AionAddress contract) {
        lock.lock();
        try {
            return getInnerContractDetails(contract).getObjectGraph();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void addStorageRow(AionAddress address, ByteArrayWrapper key, ByteArrayWrapper value) {
        lock.lock();
        try {
            getInnerContractDetails(address).put(key, value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeStorageRow(AionAddress address, ByteArrayWrapper key) {
        lock.lock();
        try {
            getInnerContractDetails(address).delete(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ByteArrayWrapper getStorageValue(AionAddress address, ByteArrayWrapper key) {
        lock.lock();
        try {
            return getInnerContractDetails(address).get(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Map<ByteArrayWrapper, ByteArrayWrapper> getStorage(
            AionAddress address, Collection<ByteArrayWrapper> keys) {
        lock.lock();
        try {
            InnerContractDetails details = getInnerContractDetails(address);
            return (details == null) ? Collections.emptyMap() : details.getStorage(keys);
        } finally {
            lock.unlock();
        }
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
            for (Map.Entry<AionAddress, InnerContractDetails> entry : cachedDetails.entrySet()) {
                InnerContractDetails contractDetailsCache = entry.getValue();
                contractDetailsCache.commit();

                if (contractDetailsCache.origContract == null && other.hasContractDetails(entry.getKey())) {
                    // in forked block the contract account might not exist thus it is created without origin,
                    // but on the main chain details can contain data which should be merged into a single storage trie
                    // so both branches with different stateRoots are valid
                    contractDetailsCache.origContract = other.getContractDetails(entry.getKey());
                    contractDetailsCache.commit();
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
                InnerContractDetails contractDetailsCache = (InnerContractDetails) ctdEntry.getValue();
                if (contractDetailsCache.origContract != null
                        && !(contractDetailsCache.origContract instanceof StoredContractDetails)) {
                    // Copying the parent because contract details changes were pushed to the parent
                    // in previous method (flush)
                    cachedDetails.put(
                            ctdEntry.getKey(),
                            InnerContractDetails.copy(
                                    (InnerContractDetails) contractDetailsCache.origContract));
                } else {
                    // Either no parent or we have RepoImpl's StoredContractDetails
                    // which should be flushed through RepoImpl
                    cachedDetails.put(
                            ctdEntry.getKey(), InnerContractDetails.copy(contractDetailsCache));
                }
            }
        } finally {
            lock.unlock();
        }
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
    public boolean isValidRoot(byte[] root) {
        return this.repository.isValidRoot(root);
    }

    @Override
    public boolean isIndexed(byte[] hash, long level) {
        return repository.isIndexed(hash, level);
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
            for (Map.Entry<AionAddress, InnerContractDetails> ca : cachedDetails.entrySet()) {
                s.append(ca.getKey()).append("\n").append(ca.getValue()).append("\n");
            }
        } else {
            s.append("]\n");
        }

        return s.toString();
    }
}
