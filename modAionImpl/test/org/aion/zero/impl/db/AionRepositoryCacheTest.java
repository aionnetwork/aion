package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.base.ConstantUtil.EMPTY_TRIE_HASH;
import static org.aion.crypto.HashUtil.h256;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.aion.base.AccountState;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.mcf.db.ContractDetails;
import org.aion.mcf.db.InternalVmType;
import org.aion.mcf.db.RepositoryCache;
import org.aion.zero.impl.config.CfgPrune;
import org.aion.zero.impl.config.PruneConfig;
import org.aion.util.types.DataWord;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AionRepositoryCacheTest {
    private  AionRepositoryImpl repository;
    private AionRepositoryCache cache;
    private static final int SINGLE_BYTES = 16;
    private static final int DOUBLE_BYTES = 32;
    private static final ByteArrayWrapper ZERO_WRAPPED_32 = ByteArrayWrapper.wrap(new byte[32]);

    @Before
    public void setup() {
        RepositoryConfig repoConfig =
                new RepositoryConfig() {
                    @Override
                    public String getDbPath() {
                        return "";
                    }

                    @Override
                    public PruneConfig getPruneConfig() {
                        return new CfgPrune(false);
                    }

                    @Override
                    public Properties getDatabaseConfig(String db_name) {
                        Properties props = new Properties();
                        props.setProperty(DatabaseFactory.Props.DB_TYPE, DBVendor.MOCKDB.toValue());
                        return props;
                    }
                };
        repository = AionRepositoryImpl.createForTesting(repoConfig);
        cache = new AionRepositoryCache(repository);
    }

    @After
    public void tearDown() {
        cache = null;
    }

    @Test
    public void testGetStorageValueNoSuchAddress() {
        assertNull(
                cache.getStorageValue(
                        getNewAddress(),
                        new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper()));
    }

    @Test
    public void testGetStorageValueIsSingleZero() {
        AionAddress address = getNewAddress();
        ByteArrayWrapper key = ByteArrayWrapper.wrap(RandomUtils.nextBytes(SINGLE_BYTES));
        cache.removeStorageRow(address, key);
        assertNull(cache.getStorageValue(address, key));

        key = ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));
        cache.removeStorageRow(address, key);
        assertNull(cache.getStorageValue(address, key));
    }

    @Test
    public void testGetStorageValueIsDoubleZero() {
        AionAddress address = getNewAddress();
        ByteArrayWrapper key = ByteArrayWrapper.wrap(RandomUtils.nextBytes(16));
        cache.removeStorageRow(address, key);
        assertNull(cache.getStorageValue(address, key));

        key = ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));
        cache.removeStorageRow(address, key);
        assertNull(cache.getStorageValue(address, key));
    }

    @Test
    public void testGetStorageValueWithSingleZeroKey() {
        AionAddress address = getNewAddress();
        ByteArrayWrapper value =
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper();
        cache.addStorageRow(address, DataWord.ZERO.toWrapper(), value);
        assertEquals(value, cache.getStorageValue(address, DataWord.ZERO.toWrapper()));

        value = ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));
        cache.addStorageRow(address, DataWord.ZERO.toWrapper(), value);
        assertEquals(value, cache.getStorageValue(address, DataWord.ZERO.toWrapper()));
    }

    @Test
    public void testGetStorageValueWithDoubleZeroKey() {
        AionAddress address = getNewAddress();
        ByteArrayWrapper value =
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper();
        cache.addStorageRow(address, ZERO_WRAPPED_32, value);
        assertEquals(value, cache.getStorageValue(address, ZERO_WRAPPED_32));

        value = ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));
        cache.addStorageRow(address, ZERO_WRAPPED_32, value);
        assertEquals(value, cache.getStorageValue(address, ZERO_WRAPPED_32));
    }

    @Test
    public void testGetStorageValueWithZeroKeyAndValue() {
        AionAddress address = getNewAddress();

        // single-single
        cache.removeStorageRow(address, DataWord.ZERO.toWrapper());
        assertNull(cache.getStorageValue(address, DataWord.ZERO.toWrapper()));

        // single-double
        cache.removeStorageRow(address, DataWord.ZERO.toWrapper());
        assertNull(cache.getStorageValue(address, DataWord.ZERO.toWrapper()));

        // double-single
        cache.removeStorageRow(address, ZERO_WRAPPED_32);
        assertNull(cache.getStorageValue(address, ZERO_WRAPPED_32));

        // double-double
        cache.removeStorageRow(address, ZERO_WRAPPED_32);
        assertNull(cache.getStorageValue(address, ZERO_WRAPPED_32));
    }

    @Test
    public void testOverwriteValueWithSingleZero() {
        AionAddress address = getNewAddress();
        ByteArrayWrapper key =
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper();
        ByteArrayWrapper value = ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));

        cache.addStorageRow(address, key, value);
        assertEquals(value, cache.getStorageValue(address, key));
        cache.removeStorageRow(address, key);
        assertNull(cache.getStorageValue(address, key));
    }

    @Test
    public void testOverwriteValueWithDoubleZero() {
        AionAddress address = getNewAddress();
        ByteArrayWrapper key = ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));

        ByteArrayWrapper value =
                new DataWord(RandomUtils.nextBytes(DataWord.BYTES)).toWrapper();
        cache.addStorageRow(address, key, value);
        assertEquals(value, cache.getStorageValue(address, key));
        cache.removeStorageRow(address, key);
        assertNull(cache.getStorageValue(address, key));
    }

    @Test
    public void testGetStorageValueEnMass() {
        int numEntries = RandomUtils.nextInt(300, 700);
        int deleteOdds = 5;
        int numAddrs = 8;
        List<AionAddress> addresses = getAddressesInBulk(numAddrs);
        List<ByteArrayWrapper> keys = getKeysInBulk(numEntries);
        List<ByteArrayWrapper> values = getValuesInBulk(numEntries);

        for (AionAddress address : addresses) {
            massAddToCache(address, keys, values);
            deleteEveryNthEntry(address, keys, deleteOdds);
        }

        for (AionAddress address : addresses) {
            checkStorage(address, keys, values, deleteOdds);
        }
    }

    @Test
    public void testLoadAccountState_withNewAccount() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));

        AionRepositoryCache tracker = (AionRepositoryCache) cache.startTracking();
        assertThat(tracker.hasAccountState(address)).isFalse();
        assertThat(tracker.hasContractDetails(address)).isFalse();
        assertThat(tracker.cachedAccounts.containsKey(address)).isFalse();
        assertThat(tracker.cachedDetails.containsKey(address)).isFalse();

        // load account state for new address
        AccountState account = tracker.getAccountState(address);
        InnerContractDetails details = (InnerContractDetails) tracker.getContractDetails(address);

        // 1. Ensure new account and details were created
        assertThat(tracker.cachedAccounts.containsKey(address)).isTrue();
        assertThat(tracker.cachedDetails.containsKey(address)).isTrue();

        assertThat(account.isEmpty()).isTrue();
        assertThat(account.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);

        assertThat(details.origContract).isNull();
        assertThat(details.isDeleted()).isFalse();
        assertThat(details.isDirty()).isFalse();

        // 2. Ensure that the cache does not contain the new account and details
        assertThat(cache.cachedAccounts.containsKey(address)).isFalse();
        assertThat(cache.cachedDetails.containsKey(address)).isFalse();

        // 3. Ensure that the repository does not contain the new account and details
        assertThat(repository.hasAccountState(address)).isFalse();
        assertThat(repository.hasContractDetails(address)).isFalse();
    }

    @Test
    public void testLoadAccountState_withExistingAccountFromRepository() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        byte[] code = RandomUtils.nextBytes(100);
        byte[] codeHash = h256(code);

        // initialize contract in the repository
        RepositoryCache<AccountState> tempCache = repository.startTracking();
        tempCache.createAccount(address);
        tempCache.saveCode(address, code);
        tempCache.saveVmType(address, InternalVmType.FVM);
        tempCache.addBalance(address, BigInteger.TEN);
        tempCache.flushTo(repository, true);

        AionRepositoryCache tracker = (AionRepositoryCache) cache.startTracking();
        assertThat(tracker.hasAccountState(address)).isTrue();
        assertThat(tracker.hasContractDetails(address)).isTrue();
        assertThat(tracker.cachedAccounts.containsKey(address)).isFalse();
        assertThat(tracker.cachedDetails.containsKey(address)).isFalse();

        // load account state for new address
        AccountState account = tracker.getAccountState(address);
        InnerContractDetails details = (InnerContractDetails) tracker.getContractDetails(address);

        // 1. Ensure new account and details were created
        assertThat(tracker.cachedAccounts.containsKey(address)).isTrue();
        assertThat(tracker.cachedDetails.containsKey(address)).isTrue();

        assertThat(account.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(account.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(account.getCodeHash()).isEqualTo(codeHash);
        assertThat(account.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);

        assertThat(details.origContract).isNotNull();
        assertThat(details.getCode(codeHash)).isEqualTo(code);
        assertThat(details.getVmType()).isEqualTo(InternalVmType.FVM);
        assertThat(details.isDeleted()).isFalse();
        assertThat(details.isDirty()).isFalse();

        // 2. Ensure that the cache does not contain the account and details
        assertThat(cache.cachedAccounts.containsKey(address)).isFalse();
        assertThat(cache.cachedDetails.containsKey(address)).isFalse();

        // 3. Ensure that the repository does contain the account and details
        assertThat(repository.hasAccountState(address)).isTrue();
        assertThat(repository.hasContractDetails(address)).isTrue();

    }

    @Test
    public void testLoadAccountState_withExistingAccountFromCache() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        byte[] code = RandomUtils.nextBytes(100);
        byte[] codeHash = h256(code);

        // initialize contract in the repository
        RepositoryCache<AccountState> tempCache = repository.startTracking();
        tempCache.createAccount(address);
        tempCache.saveCode(address, code);
        tempCache.saveVmType(address, InternalVmType.FVM);
        tempCache.addBalance(address, BigInteger.ONE);
        tempCache.flushTo(repository, true);

        // update the contract in the cache without flushing
        cache.addBalance(address, BigInteger.ONE);
        ByteArrayWrapper store = ByteArrayWrapper.wrap(RandomUtils.nextBytes(32));
        cache.addStorageRow(address, store, store);

        AionRepositoryCache tracker = (AionRepositoryCache) cache.startTracking();
        assertThat(tracker.hasAccountState(address)).isTrue();
        assertThat(tracker.hasContractDetails(address)).isTrue();
        assertThat(tracker.cachedAccounts.containsKey(address)).isFalse();
        assertThat(tracker.cachedDetails.containsKey(address)).isFalse();

        // load account state for new address
        AccountState account = tracker.getAccountState(address);
        InnerContractDetails details = (InnerContractDetails) tracker.getContractDetails(address);

        // 1. Ensure new account and details were created
        assertThat(tracker.cachedAccounts.containsKey(address)).isTrue();
        assertThat(tracker.cachedDetails.containsKey(address)).isTrue();

        assertThat(account.getBalance()).isEqualTo(BigInteger.TWO);
        assertThat(account.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(account.getCodeHash()).isEqualTo(codeHash);
        assertThat(account.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);

        assertThat(details.origContract).isNotNull();
        assertThat(details.getCode(codeHash)).isEqualTo(code);
        assertThat(details.get(store)).isEqualTo(store);
        assertThat(details.getVmType()).isEqualTo(InternalVmType.FVM);
        assertThat(details.isDeleted()).isFalse();
        assertThat(details.isDirty()).isFalse();

        // 2. Ensure that the cache contains the loaded account and details
        assertThat(cache.cachedAccounts.containsKey(address)).isTrue();
        assertThat(cache.cachedDetails.containsKey(address)).isTrue();

        AccountState accountCache = cache.getAccountState(address);
        InnerContractDetails detailsCache = (InnerContractDetails) cache.getContractDetails(address);

        assertThat(accountCache.getBalance()).isEqualTo(BigInteger.TWO);
        assertThat(detailsCache.get(store)).isEqualTo(store);

        // 3. Ensure that the repository contains the old account and details
        assertThat(repository.hasAccountState(address)).isTrue();
        assertThat(repository.hasContractDetails(address)).isTrue();

        AccountState accountRepo = repository.getAccountState(address);
        ContractDetails detailsRepo = repository.getContractDetails(address);

        assertThat(accountRepo.getBalance()).isEqualTo(BigInteger.ONE);
        assertThat(detailsRepo.get(store)).isNull();
    }

    // <-----------------------------------------HELPERS-------------------------------------------->

    /** Returns a new random address. */
    private AionAddress getNewAddress() {
        return new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
    }

    private List<AionAddress> getAddressesInBulk(int num) {
        List<AionAddress> addresses = new ArrayList<>(num);
        for (int i = 0; i < num; i++) {
            addresses.add(getNewAddress());
        }
        return addresses;
    }

    /**
     * Checks that cache's storage, given by cache.getStorage(), contains all key-value pairs in
     * keys and values, where it is assumed every n'th pair was deleted.
     */
    private void checkStorage(
            AionAddress address,
            List<ByteArrayWrapper> keys,
            List<ByteArrayWrapper> values,
            int n) {
        Map<ByteArrayWrapper, ByteArrayWrapper> storage = cache.getStorage(address, keys);
        int count = 1;
        for (ByteArrayWrapper key : keys) {
            if (count % n == 0) {
                assertNull(storage.get(key));
            } else {
                assertEquals(values.get(count - 1), storage.get(key));
            }
            count++;
        }
    }

    /**
     * Iterates over every key in keys -- which are assumed to exist in cache -- and then deletes
     * any key-value pair in cache for every n'th key in keys.
     */
    private void deleteEveryNthEntry(AionAddress address, List<ByteArrayWrapper> keys, int n) {
        int count = 1;
        for (ByteArrayWrapper key : keys) {
            if (count % n == 0) {
                cache.removeStorageRow(address, key);
            }
            count++;
        }
    }

    /** Puts all of the key-value pairs in keys and values into cache under address. */
    private void massAddToCache(
            AionAddress address, List<ByteArrayWrapper> keys, List<ByteArrayWrapper> values) {
        int size = keys.size();
        assertEquals(size, values.size());
        for (int i = 0; i < size; i++) {
            cache.addStorageRow(address, keys.get(i), values.get(i));
        }
    }

    /** Returns a list of numKeys keys, every other one is single and then double. */
    private List<ByteArrayWrapper> getKeysInBulk(int numKeys) {
        List<ByteArrayWrapper> keys = new ArrayList<>(numKeys);
        boolean isSingleKey = true;
        for (int i = 0; i < numKeys; i++) {
            keys.add(getRandomWord(isSingleKey));
            isSingleKey = !isSingleKey;
        }
        return keys;
    }

    /** Returns a list of numValues values, every other one is single and then double. */
    private List<ByteArrayWrapper> getValuesInBulk(int numValues) {
        List<ByteArrayWrapper> values = new ArrayList<>(numValues);
        boolean isSingleValue = true;
        for (int i = 0; i < numValues; i++) {
            values.add(getRandomWord(isSingleValue));
            isSingleValue = !isSingleValue;
        }
        return values;
    }

    /** Returns a random byte array of length 16 if isSingleWord is true, otherwise length 32. */
    private ByteArrayWrapper getRandomWord(boolean isSingleWord) {
        return (isSingleWord)
                ? ByteArrayWrapper.wrap(RandomUtils.nextBytes(SINGLE_BYTES))
                : ByteArrayWrapper.wrap(RandomUtils.nextBytes(DOUBLE_BYTES));
    }
}
