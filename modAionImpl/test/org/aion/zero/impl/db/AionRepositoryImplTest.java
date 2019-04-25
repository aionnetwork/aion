package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import java.util.Optional;
import java.util.Properties;
import org.aion.crypto.HashUtil;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.interfaces.db.ByteArrayKeyValueDatabase;
import org.aion.interfaces.db.ContractDetails;
import org.aion.interfaces.db.InternalVmType;
import org.aion.interfaces.db.PruneConfig;
import org.aion.interfaces.db.Repository;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.interfaces.db.RepositoryConfig;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.trie.TrieNodeResult;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.types.Address;
import org.aion.types.ByteArrayWrapper;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.sync.DatabaseType;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AionRepositoryImplTest {

    protected RepositoryConfig repoConfig =
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
                public ContractDetails contractDetailsImpl() {
                    return ContractDetailsAion.createForTesting(0, 1000000).getDetails();
                }

                @Override
                public Properties getDatabaseConfig(String db_name) {
                    Properties props = new Properties();
                    props.setProperty(DatabaseFactory.Props.DB_TYPE, DBVendor.MOCKDB.toValue());
                    props.setProperty(DatabaseFactory.Props.ENABLE_HEAP_CACHE, "false");
                    return props;
                }
            };

    private static String value1 =
            "CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3";
    private static String value2 =
            "CAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFE";
    private static String value3 =
            "BEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEF";

    @Test
    public void testAccountStateUpdate() {
        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        byte[] originalRoot = repository.getRoot();

        Address defaultAccount = Address.wrap(ByteUtil.hexStringToBytes(value1));

        RepositoryCache track = repository.startTracking();
        track.addBalance(defaultAccount, BigInteger.valueOf(1));
        track.flush();

        byte[] newRoot = repository.getRoot();
        System.out.println(String.format("original root: %s", ByteUtil.toHexString(originalRoot)));
        System.out.println(String.format("new root: %s", ByteUtil.toHexString(newRoot)));

        assertThat(newRoot).isNotEqualTo(originalRoot);
    }

    @Test
    public void testAccountAddCodeStorage() {
        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        RepositoryCache track = repository.startTracking();

        Address defaultAccount = Address.wrap(ByteUtil.hexStringToBytes(value1));
        track.addBalance(defaultAccount, BigInteger.valueOf(1));

        byte[] originalRoot = repository.getRoot();
        track.saveCode(defaultAccount, defaultAccount.toBytes());
        track.saveVmType(defaultAccount, InternalVmType.FVM);

        track.flush();

        byte[] newRoot = repository.getRoot();
        assertThat(newRoot).isNotEqualTo(originalRoot);
        assertThat(repository.getCode(defaultAccount)).isEqualTo(defaultAccount.toBytes());

        System.out.println(String.format("originalRoot: %s", ByteUtil.toHexString(originalRoot)));
        System.out.println(String.format("newRoot: %s", ByteUtil.toHexString(originalRoot)));
    }

    @Test
    public void testAccountStateUpdateStorageRow() {
        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        RepositoryCache track = repository.startTracking();

        Address defaultAccount = Address.wrap(ByteUtil.hexStringToBytes(value1));
        track.addBalance(defaultAccount, BigInteger.valueOf(1));

        // Consider the original root the one after an account has been added
        byte[] originalRoot = repository.getRoot();
        byte[] key = HashUtil.blake128("hello".getBytes());
        byte[] value = HashUtil.blake128("world".getBytes());
        track.addStorageRow(
                defaultAccount,
                new DataWordImpl(key).toWrapper(),
                new DataWordImpl(value).toWrapper());
        track.saveVmType(defaultAccount, InternalVmType.FVM);

        track.flush();

        byte[] retrievedValue =
                repository
                        .getStorageValue(defaultAccount, new DataWordImpl(key).toWrapper())
                        .getNoLeadZeroesData();
        assertThat(retrievedValue).isEqualTo(value);

        byte[] newRoot = repository.getRoot();

        System.out.println(String.format("original root: %s", ByteUtil.toHexString(originalRoot)));
        System.out.println(String.format("new root: %s", ByteUtil.toHexString(newRoot)));
    }

    @Test
    public void testAccountStateUpdateStorageRowFlush() {
        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        RepositoryCache track = repository.startTracking();

        Address defaultAccount = Address.wrap(ByteUtil.hexStringToBytes(value1));
        track.addBalance(defaultAccount, BigInteger.valueOf(1));

        // Consider the original root the one after an account has been added
        byte[] originalRoot = repository.getRoot();
        byte[] key = HashUtil.blake128("hello".getBytes());
        byte[] value = HashUtil.blake128("world".getBytes());
        track.addStorageRow(
                defaultAccount,
                new DataWordImpl(key).toWrapper(),
                new DataWordImpl(value).toWrapper());
        track.saveVmType(defaultAccount, InternalVmType.FVM);

        // does not call parent's flush
        track.flush();

        repository.flush();

        /** Verify that the account has been flushed */
        ByteArrayKeyValueDatabase detailsDB = repository.getDetailsDatabase();
        Optional<byte[]> serializedDetails = detailsDB.get(defaultAccount.toBytes());

        assertThat(serializedDetails.isPresent()).isEqualTo(true);

        AionContractDetailsImpl details = new AionContractDetailsImpl(0, 1000000);
        details.decode(serializedDetails.get());
        assertThat(details.get(new DataWordImpl(key).toWrapper()))
                .isEqualTo(new DataWordImpl(value).toWrapper());
    }

    /** Repo track test suite */

    /**
     * This test confirms that updates done on the repo track are successfully translated into the
     * root repository.
     */
    @Test
    public void testRepoTrackUpdateStorageRow() {
        final AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        final RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repoTrack =
                repository.startTracking();
        final Address defaultAccount = Address.wrap(ByteUtil.hexStringToBytes(value1));
        final byte[] key = HashUtil.blake128("hello".getBytes());
        final byte[] value = HashUtil.blake128("world".getBytes());

        repoTrack.addBalance(defaultAccount, BigInteger.valueOf(1));

        final byte[] originalRoot = repository.getRoot();

        repoTrack.addStorageRow(
                defaultAccount,
                new DataWordImpl(key).toWrapper(),
                new DataWordImpl(value).toWrapper());
        repoTrack.saveVmType(defaultAccount, InternalVmType.FVM);

        ByteArrayWrapper retrievedStorageValue =
                repoTrack.getStorageValue(defaultAccount, new DataWordImpl(key).toWrapper());
        assertThat(retrievedStorageValue).isEqualTo(new DataWordImpl(value).toWrapper());

        // commit changes, then check that the root has updated
        repoTrack.flush();

        assertThat(repository.getStorageValue(defaultAccount, new DataWordImpl(key).toWrapper()))
                .isEqualTo(retrievedStorageValue);

        final byte[] newRoot = repository.getRoot();
        assertThat(newRoot).isNotEqualTo(originalRoot);
    }

    @Test
    public void testSyncToPreviousRootNoFlush() {
        final Address FIRST_ACC = Address.wrap(value2);
        final Address SECOND_ACC = Address.wrap(value3);

        final AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        byte[] originalRoot = repository.getRoot();

        // now create a new account
        RepositoryCache track = repository.startTracking();
        track.addBalance(FIRST_ACC, BigInteger.ONE);
        track.flush();

        System.out.println("after first account added");
        System.out.println(repository.getWorldState().getTrieDump());

        // check the update on the repo
        BigInteger balance = repository.getBalance(FIRST_ACC);
        assertThat(balance).isEqualTo(BigInteger.ONE);

        byte[] firstRoot = repository.getRoot();

        track = repository.startTracking();
        track.addBalance(SECOND_ACC, BigInteger.TWO);
        track.flush();

        byte[] secondRoot = repository.getRoot();

        System.out.println("after second account added");
        System.out.println(repository.getWorldState().getTrieDump());

        assertThat(firstRoot).isNotEqualTo(originalRoot);
        assertThat(secondRoot).isNotEqualTo(firstRoot);

        System.out.println("after sync to after first account added");
        repository.syncToRoot(firstRoot);
        assertThat(repository.isValidRoot(firstRoot)).isTrue();
        System.out.println(repository.getWorldState().getTrieDump());

        assertThat(repository.getRoot()).isEqualTo(firstRoot);
        balance = repository.getBalance(FIRST_ACC);

        // notice that the first blocks balance is also zero
        assertThat(balance).isEqualTo(BigInteger.ONE);
    }

    @Test
    public void testSyncToPreviousRootWithFlush() {
        final Address FIRST_ACC = Address.wrap(value2);
        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);

        byte[] originalRoot = repository.getRoot();
        RepositoryCache track = repository.startTracking();
        track.addBalance(FIRST_ACC, BigInteger.ONE);
        track.flush();
        byte[] newRoot = repository.getRoot();
        System.out.println("state after add one account");
        System.out.println(repository.getWorldState().getTrieDump());

        // flush into cache/db
        repository.flush();

        track = repository.startTracking();
        track.addBalance(FIRST_ACC, BigInteger.ONE); // total should be 2
        track.flush();
        assertThat(repository.getBalance(FIRST_ACC)).isEqualTo(BigInteger.TWO);

        System.out.println("state after adding balance to FIRST_ACC");
        System.out.println(repository.getWorldState().getTrieDump());

        // flush this state into cache/db
        repository.flush();
        repository.setRoot(newRoot);

        System.out.println("state after rewinding to previous root");
        System.out.println(repository.getWorldState().getTrieDump());

        assertThat(repository.getBalance(FIRST_ACC)).isEqualTo(BigInteger.ONE);
    }

    // test that intermediate nodes also get rolled back properly
    // intermediate nodes get created when two accounts have a common substring
    @Test
    public void test17NodePreviousRootTest() {
        // not that it matters since things are going to be hashed, but at least
        // the root node should point to a node that contains references to both
        final Address DOG_ACC = Address.wrap("00000000000000000000000000000dog".getBytes());
        final Address DOGE_ACC = Address.wrap("0000000000000000000000000000doge".getBytes());

        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        RepositoryCache track = repository.startTracking();
        track.addBalance(DOG_ACC, BigInteger.ONE);
        track.addBalance(DOGE_ACC, BigInteger.ONE);
        track.flush();

        final byte[] root = repository.getRoot();
        repository.flush();

        System.out.println("trie state after adding two accounts");
        System.out.println(repository.getWorldState().getTrieDump());

        track = repository.startTracking();
        track.addBalance(DOG_ACC, BigInteger.ONE);
        track.flush();
        System.out.println("trie state after updating balance on one account");
        System.out.println(repository.getWorldState().getTrieDump());
        assertThat(repository.getBalance(DOG_ACC)).isEqualTo(BigInteger.TWO);
        repository.flush();

        repository.syncToRoot(root);
        assertThat(repository.getBalance(DOG_ACC)).isEqualTo(BigInteger.ONE);
    }

    @Test
    public void testGetSnapshotToRoot() {
        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);

        // make some changes to the repository
        final Address account1 = Address.wrap(value1);
        final Address account2 = Address.wrap(value2);
        final Address account3 = Address.wrap(value3);
        RepositoryCache track = repository.startTracking();
        track.addBalance(account1, BigInteger.ONE);
        track.addBalance(account2, BigInteger.TWO);
        track.addBalance(account3, BigInteger.TEN);
        track.flush();
        repository.flush();

        // get snapshot to root
        Repository snapshot = repository.getSnapshotTo(repository.getRoot());

        // check that the same values are retrieved
        assertThat(repository.getBalance(account1)).isEqualTo(snapshot.getBalance(account1));
        assertThat(repository.getBalance(account2)).isEqualTo(snapshot.getBalance(account2));
        assertThat(repository.getBalance(account3)).isEqualTo(snapshot.getBalance(account3));

        // make more changes to the initial repo
        track = repository.startTracking();
        track.addBalance(account1, BigInteger.TWO);
        track.addBalance(account2, BigInteger.TWO);
        track.addBalance(account3, BigInteger.TWO);
        track.flush();

        // check that the values from the repo are larger
        assertThat(repository.getBalance(account1)).isGreaterThan(snapshot.getBalance(account1));
        assertThat(repository.getBalance(account2)).isGreaterThan(snapshot.getBalance(account2));
        assertThat(repository.getBalance(account3)).isGreaterThan(snapshot.getBalance(account3));

        // make the same changes to the snapshot
        track = snapshot.startTracking();
        track.addBalance(account1, BigInteger.TWO);
        track.addBalance(account2, BigInteger.TWO);
        track.addBalance(account3, BigInteger.TWO);
        track.flush();

        // check that the values are again equal
        assertThat(repository.getBalance(account1)).isEqualTo(snapshot.getBalance(account1));
        assertThat(repository.getBalance(account2)).isEqualTo(snapshot.getBalance(account2));
        assertThat(repository.getBalance(account3)).isEqualTo(snapshot.getBalance(account3));

        // make more changes on the snapshot
        track = snapshot.startTracking();
        track.addBalance(account1, BigInteger.TEN);
        track.addBalance(account2, BigInteger.TEN);
        track.addBalance(account3, BigInteger.TEN);
        track.flush();

        // check that the values from the snapshot are larger
        assertThat(repository.getBalance(account1)).isLessThan(snapshot.getBalance(account1));
        assertThat(repository.getBalance(account2)).isLessThan(snapshot.getBalance(account2));
        assertThat(repository.getBalance(account3)).isLessThan(snapshot.getBalance(account3));
    }

    @Test
    public void testImportTrieNode() {
        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        ByteArrayKeyValueDatabase db = repository.getStateDatabase();
        byte[] nodeKey =
                new byte[] {
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
                    23, 24, 25, 26, 27, 28, 29, 30, 31, 32
                };
        ;
        byte[] altNodeKey =
                new byte[] {
                    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
                    22, 23, 24, 25, 26, 27, 28, 29, 30, 31
                };
        byte[] smallNodeKey =
                new byte[] {
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
                    23, 24, 25, 26, 27, 28, 29, 30, 31
                };
        byte[] leafValue =
                new byte[] {
                    -8, 114, -97, 60, -96, -3, -97, 10, 112, 111, 28, -32, 44, 18, 101, -106, 51, 6,
                    -107, 0, 24, 13, 50, 81, -84, 68, 125, 110, 118, 97, -109, -96, -30, 107, -72,
                    80, -8, 78, -128, -118, -45, -62, 27, -50, -52, -19, -95, 0, 0, 0, -96, 69, -80,
                    -49, -62, 32, -50, -20, 91, 124, 28, 98, -60, -44, 25, 61, 56, -28, -21, -92,
                    -114, -120, 21, 114, -100, -25, 95, -100, 10, -80, -28, -63, -64, -96, 14, 87,
                    81, -64, 38, -27, 67, -78, -24, -85, 46, -80, 96, -103, -38, -95, -47, -27, -33,
                    71, 119, -113, 119, -121, -6, -85, 69, -51, -15, 47, -29, -88
                };
        byte[] branchValue =
                new byte[] {
                    -8, -111, -128, -96, -99, -57, 89, 41, -60, -8, -93, -128, 9, -59, -23, -116, 4,
                    70, -94, -76, 119, -16, 22, 117, -72, -96, -117, 125, -57, 95, 123, -29, -46,
                    37, -78, 86, -96, 89, -62, -94, 108, -21, -48, -19, 80, 5, 59, -70, 24, 90, 125,
                    19, -31, -82, 88, 49, 78, 44, 55, -44, 108, 31, 123, -120, 95, -39, 59, 104,
                    122, -96, -102, -109, -7, 6, 55, -12, 8, 32, -45, 116, 125, -50, 117, 7, 6, -59,
                    -109, 88, 32, 95, 92, 97, 26, -107, -84, 85, 25, 9, 83, 78, -20, -37, -128,
                    -128, -128, -128, -96, -35, -15, -76, -107, -93, -23, -114, 24, -105, -87, -79,
                    37, 125, 65, 114, -43, -97, -53, -32, -37, -94, 59, -117, -121, -127, 44, -94,
                    -91, 89, 25, -39, -85, -128, -128, -128, -128, -128, -128, -128, -128
                };
        byte[] emptyValue = new byte[0];

        // check import with empty database: TrieNodeResult.IMPORTED
        assertThat(db.isEmpty()).isTrue();
        assertThat(repository.importTrieNode(nodeKey, leafValue, DatabaseType.STATE))
                .isEqualTo(TrieNodeResult.IMPORTED);
        Optional<byte[]> value = db.get(nodeKey);
        assertThat(value.isPresent()).isTrue();
        assertThat(value.get()).isEqualTo(leafValue);

        // check import with same key + same value: TrieNodeResult.KNOWN
        assertThat(repository.importTrieNode(nodeKey, leafValue, DatabaseType.STATE))
                .isEqualTo(TrieNodeResult.KNOWN);
        value = db.get(nodeKey);
        assertThat(value.isPresent()).isTrue();
        assertThat(value.get()).isEqualTo(leafValue);

        // check import with same key + different value: TrieNodeResult.INCONSISTENT
        assertThat(repository.importTrieNode(nodeKey, branchValue, DatabaseType.STATE))
                .isEqualTo(TrieNodeResult.INCONSISTENT);
        value = db.get(nodeKey);
        assertThat(value.isPresent()).isTrue();
        assertThat(value.get()).isEqualTo(leafValue);

        // check import with incorrect key: TrieNodeResult.INVALID_KEY
        assertThat(repository.importTrieNode(smallNodeKey, branchValue, DatabaseType.STATE))
                .isEqualTo(TrieNodeResult.INVALID_KEY);
        value = db.get(smallNodeKey);
        assertThat(value.isPresent()).isFalse();

        // check import with incorrect value: TrieNodeResult.INVALID_VALUE
        assertThat(repository.importTrieNode(altNodeKey, emptyValue, DatabaseType.STATE))
                .isEqualTo(TrieNodeResult.INVALID_VALUE);
        value = db.get(altNodeKey);
        assertThat(value.isPresent()).isFalse();
    }
}
