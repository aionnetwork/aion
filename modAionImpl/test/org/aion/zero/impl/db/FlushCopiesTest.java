package org.aion.zero.impl.db;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import java.math.BigInteger;
import org.aion.base.db.IContractDetails;
import org.aion.base.db.IRepository;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.vm.api.interfaces.Address;
import org.aion.zero.db.AionRepositoryCache;
import org.aion.zero.impl.StandaloneBlockchain;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the {@link org.aion.zero.db.AionRepositoryCache#flushCopiesTo(IRepository, boolean)}
 * method.
 */
public class FlushCopiesTest {
    private static IRepository repository;

    @Before
    public void setup() {
        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
            .withDefaultAccounts()
            .withValidatorConfiguration("simple")
            .build();
        this.repository = bundle.bc.getRepository();
    }

    @After
    public void tearDown() {
        this.repository = null;
    }

    @Test
    public void testAccountStateObjectReference() {
        AionRepositoryCache repositoryChild = (AionRepositoryCache) repository.startTracking();

        Address account = randomAionAddress();
        BigInteger nonce = BigInteger.TEN;
        BigInteger balance = BigInteger.valueOf(11223344);
        byte[] code = new byte[100];

        System.out.println("testAccountStateObjectReference using address: " + account);

        // Create a new account state in the child, flush to the parent without clearing child
        // state.
        repositoryChild.createAccount(account);
        repositoryChild.setNonce(account, nonce);
        repositoryChild.addBalance(account, balance);
        repositoryChild.saveCode(account, code);
        repositoryChild.flushCopiesTo(repository, false);

        // Compare object references.
        AccountState accountStateInChild = repositoryChild.getAccountState(account);
        AccountState accountStateInParent = (AccountState) repository.getAccountState(account);

        // These references must be different.
        assertNotSame(accountStateInChild, accountStateInParent);
        assertNotSame(accountStateInChild.getStateRoot(), accountStateInParent.getStateRoot());

        // Ensure that the essential state of the objects are equivalent.
        assertEquals(accountStateInChild.getNonce(), accountStateInParent.getNonce());
        assertEquals(accountStateInChild.getBalance(), accountStateInParent.getBalance());
        assertArrayEquals(accountStateInChild.getStateRoot(), accountStateInParent.getStateRoot());
        assertArrayEquals(accountStateInChild.getCodeHash(), accountStateInParent.getCodeHash());
    }

    @Test
    public void testContractDetailsObjectReference() {
        AionRepositoryCache repositoryChild = (AionRepositoryCache) repository.startTracking();

        Address account = randomAionAddress();
        BigInteger nonce = BigInteger.TEN;
        BigInteger balance = BigInteger.valueOf(11223344);
        byte[] code = new byte[100];

        System.out.println("testContractDetailsObjectReference using address: " + account);

        // Create a new account state in the child, flush to the parent without clearing child
        // state.
        ByteArrayWrapper key = new DataWord(5).toWrapper();
        ByteArrayWrapper value = new DoubleDataWord(13429765314L).toWrapper();

        repositoryChild.createAccount(account);
        repositoryChild.setNonce(account, nonce);
        repositoryChild.addBalance(account, balance);
        repositoryChild.saveCode(account, code);
        repositoryChild.addStorageRow(account, key, value);
        repositoryChild.flushCopiesTo(repository, false);

        // Compare object references.
        IContractDetails detailsInChild = repositoryChild.getContractDetails(account);
        IContractDetails detailsInParent = repository.getContractDetails(account);

        // These references must be different.
        assertNotSame(detailsInChild, detailsInParent);

        // Ensure that the essential state of the objects are equivalent.
        assertArrayEquals(detailsInChild.getStorageHash(), detailsInParent.getStorageHash());
        assertArrayEquals(detailsInChild.getCode(), detailsInParent.getCode());
        assertEquals(detailsInChild.getAddress(), detailsInParent.getAddress());
    }

    @Test
    public void testSiblingStateModificationsAreIndependentOfOneAnother() {
        AionRepositoryCache firstChild = (AionRepositoryCache) repository.startTracking();

        Address account = randomAionAddress();
        BigInteger firstNonce = BigInteger.TEN;
        BigInteger firstBalance = BigInteger.valueOf(11223344);
        byte[] code = new byte[100];

        System.out.println(
                "testSiblingStateModificationsAreIndependentOfOneAnother using address: "
                        + account);

        // Create a new account state in the child, flush to the parent without clearing child
        // state.
        ByteArrayWrapper firstKey = new DataWord(5).toWrapper();
        ByteArrayWrapper firstValue = new DoubleDataWord(13429765314L).toWrapper();

        firstChild.createAccount(account);
        firstChild.setNonce(account, firstNonce);
        firstChild.addBalance(account, firstBalance);
        firstChild.saveCode(account, code);
        firstChild.addStorageRow(account, firstKey, firstValue);
        firstChild.flushCopiesTo(repository, false);

        byte[] firstStorageHash = firstChild.getContractDetails(account).getStorageHash();

        // Now create a sibling, and make modifications to this same account in the sibling.
        AionRepositoryCache secondChild = (AionRepositoryCache) repository.startTracking();

        BigInteger secondNonce = firstNonce.multiply(BigInteger.TWO);
        ByteArrayWrapper secondKey = new DoubleDataWord(289356).toWrapper();
        ByteArrayWrapper secondValue = new DataWord(23674).toWrapper();

        secondChild.setNonce(account, secondNonce);
        secondChild.addBalance(account, firstBalance);
        secondChild.addStorageRow(account, secondKey, secondValue);
        secondChild.flushCopiesTo(repository, false);

        // Ensure that the first child's state has not been modified by the second.
        assertEquals(firstNonce, firstChild.getNonce(account));
        assertEquals(firstBalance, firstChild.getBalance(account));
        assertArrayEquals(firstStorageHash, firstChild.getContractDetails(account).getStorageHash());
    }

    private Address randomAionAddress() {
        byte[] bytes = RandomUtils.nextBytes(Address.SIZE);
        bytes[0] = (byte) 0xa0;
        return new AionAddress(bytes);
    }
}
