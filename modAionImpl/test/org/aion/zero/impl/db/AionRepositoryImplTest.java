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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/
package org.aion.zero.impl.db;

import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.db.IContractDetails;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.db.IRepositoryConfig;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.db.impl.leveldb.LevelDBConstants;
import org.aion.mcf.core.AccountState;
import org.aion.crypto.HashUtil;
import org.aion.db.impl.DBVendor;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.aion.zero.db.AionContractDetailsImpl;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractDetailsAion;

import java.math.BigInteger;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AionRepositoryImplTest {

    protected IRepositoryConfig repoConfig = new IRepositoryConfig() {
        @Override
        public String[] getVendorList() {
            return new String[] { DBVendor.MOCKDB.toValue() };
        }

        @Override
        public String getActiveVendor() {
            return DBVendor.MOCKDB.toValue();
        }

        @Override
        public String getDbPath() {
            return "";
        }

        @Override
        public int getPrune() {
            return 0;
        }

        @Override
        public IContractDetails contractDetailsImpl() {
            return ContractDetailsAion.createForTesting(0, 1000000).getDetails();
        }

        @Override
        public boolean isAutoCommitEnabled() {
            return false;
        }

        @Override
        public boolean isDbCacheEnabled() {
            return false;
        }

        @Override
        public boolean isDbCompressionEnabled() {
            return false;
        }

        @Override
        public boolean isHeapCacheEnabled() {
            return true;
        }

        @Override
        public String getMaxHeapCacheSize() {
            return "0";
        }

        @Override
        public boolean isHeapCacheStatsEnabled() {
            return false;
        }

        @Override
        public int getMaxFdAllocSize() {
            return LevelDBConstants.MAX_OPEN_FILES;
        }

        // default levelDB setting, may want to change this later
        @Override
        public int getBlockSize() {
            return LevelDBConstants.BLOCK_SIZE;
        }

        @Override
        public int getWriteBufferSize() {
            return LevelDBConstants.WRITE_BUFFER_SIZE;
        }

        @Override
        public int getCacheSize() {
            return LevelDBConstants.CACHE_SIZE;
        }
    };

    @Test
    public void testAccountStateUpdate() {
        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        byte[] originalRoot = repository.getRoot();

        Address defaultAccount = Address.wrap(ByteUtil.hexStringToBytes("CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3"));

        IRepositoryCache track = repository.startTracking();
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
        IRepositoryCache track = repository.startTracking();

        Address defaultAccount = Address.wrap(ByteUtil.hexStringToBytes("CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3"));
        track.addBalance(defaultAccount, BigInteger.valueOf(1));

        byte[] originalRoot = repository.getRoot();
        track.saveCode(defaultAccount, defaultAccount.toBytes());

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
        IRepositoryCache track = repository.startTracking();

        Address defaultAccount = Address.wrap(ByteUtil.hexStringToBytes("CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3"));
        track.addBalance(defaultAccount, BigInteger.valueOf(1));

        // Consider the original root the one after an account has been added
        byte[] originalRoot = repository.getRoot();
        byte[] key = HashUtil.blake128("hello".getBytes());
        byte[] value = HashUtil.blake128("world".getBytes());
        track.addStorageRow(defaultAccount, new DataWord(key), new DataWord(value));

        track.flush();

        byte[] retrievedValue = repository.getStorageValue(defaultAccount, new DataWord(key)).getNoLeadZeroesData();
        assertThat(retrievedValue).isEqualTo(value);

        byte[] newRoot = repository.getRoot();

        System.out.println(String.format("original root: %s", ByteUtil.toHexString(originalRoot)));
        System.out.println(String.format("new root: %s", ByteUtil.toHexString(newRoot)));
    }

    @Test
    public void testAccountStateUpdateStorageRowFlush() {
        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        IRepositoryCache track = repository.startTracking();

        Address defaultAccount = Address.wrap(ByteUtil.hexStringToBytes("CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3"));
        track.addBalance(defaultAccount, BigInteger.valueOf(1));

        // Consider the original root the one after an account has been added
        byte[] originalRoot = repository.getRoot();
        byte[] key = HashUtil.blake128("hello".getBytes());
        byte[] value = HashUtil.blake128("world".getBytes());
        track.addStorageRow(defaultAccount, new DataWord(key), new DataWord(value));

        // does not call parent's flush
        track.flush();

        repository.flush();

        /**
         * Verify that the account has been flushed
         */
        IByteArrayKeyValueDatabase detailsDB = repository.getDetailsDatabase();
        Optional<byte[]> serializedDetails = detailsDB.get(defaultAccount.toBytes());

        assertThat(serializedDetails.isPresent()).isEqualTo(true);

        AionContractDetailsImpl details = new AionContractDetailsImpl(0, 1000000);
        details.decode(serializedDetails.get());
        assertThat(details.get(new DataWord(key))).isEqualTo(new DataWord(value));
    }

    /**
     * Repo track test suite
     */


    /**
     * This test confirms that updates done on the repo track are successfully translated
     * into the root repository.
     */
    @Test
    public void testRepoTrackUpdateStorageRow() {
        final AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        final IRepositoryCache<AccountState,DataWord,IBlockStoreBase<?, ?>> repoTrack = repository.startTracking();
        final Address defaultAccount = Address.wrap(ByteUtil.hexStringToBytes("CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3CAF3"));
        final byte[] key = HashUtil.blake128("hello".getBytes());
        final byte[] value = HashUtil.blake128("world".getBytes());

        repoTrack.addBalance(defaultAccount, BigInteger.valueOf(1));

        final byte[] originalRoot = repository.getRoot();

        repoTrack.addStorageRow(defaultAccount, new DataWord(key), new DataWord(value));

        DataWord retrievedStorageValue = repoTrack.getStorageValue(defaultAccount, new DataWord(key));
        assertThat(retrievedStorageValue).isEqualTo(new DataWord(value));

        // commit changes, then check that the root has updated
        repoTrack.flush();

        assertThat(repository.getStorageValue(defaultAccount, new DataWord(key))).isEqualTo(retrievedStorageValue);

        final byte[] newRoot = repository.getRoot();
        assertThat(newRoot).isNotEqualTo(originalRoot);
    }

    /**
     * Tests behaviour for trie when trying to revert to a previous root without
     * first flushing. Note the behaviour here. Interestingly enough, it seems like
     * the trie must first be flushed, so that the root node is in the caching/db layer.
     *
     * Otherwise the retrieval methods will not be able to find the temporal root value.
     */
    @Test
    public void testSyncToPreviousRootNoFlush() {
        final Address FIRST_ACC = Address.wrap("CAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFE");
        final Address SECOND_ACC = Address.wrap("BEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEFBEEF");

        final AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);
        byte[] originalRoot = repository.getRoot();

        // now create a new account
        IRepositoryCache track = repository.startTracking();
        track.addBalance(FIRST_ACC, BigInteger.ONE);
        track.flush();

        System.out.println("after first account added");
        System.out.println(repository.getWorldState().getTrieDump());

        byte[] firstRoot = repository.getRoot();

        track = repository.startTracking();
        track.addBalance(SECOND_ACC, BigInteger.TWO);
        track.flush();

        byte[] secondRoot = repository.getRoot();

        System.out.println("after second account added");
        System.out.println(repository.getWorldState().getTrieDump());

        assertThat(firstRoot).isNotEqualTo(originalRoot);
        assertThat(secondRoot).isNotEqualTo(firstRoot);

        repository.syncToRoot(firstRoot);

        assertThat(repository.getRoot()).isEqualTo(firstRoot);
        BigInteger balance = repository.getBalance(FIRST_ACC);

        // notice that the first blocks balance is also zero
        assertThat(balance).isEqualTo(BigInteger.ZERO);
    }

    @Test
    public void testSyncToPreviousRootWithFlush() {
        final Address FIRST_ACC = Address.wrap("CAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFE");
        AionRepositoryImpl repository = AionRepositoryImpl.createForTesting(repoConfig);

        byte[] originalRoot = repository.getRoot();
        IRepositoryCache track = repository.startTracking();
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
        IRepositoryCache track = repository.startTracking();
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
}
