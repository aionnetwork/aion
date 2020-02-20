package org.aion.zero.impl.vm;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import org.aion.zero.impl.vm.avm.AvmProvider;
import org.aion.avm.stub.AvmExecutionType;
import org.aion.avm.stub.AvmVersion;
import org.aion.avm.stub.IAionVirtualMachine;
import org.aion.avm.stub.IAvmExternalState;
import org.aion.avm.stub.IAvmFutureResult;
import org.aion.avm.stub.IEnergyRules;
import org.aion.avm.stub.IEnergyRules.TransactionType;
import org.aion.base.AccountState;
import org.aion.mcf.db.RepositoryCache;
import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import org.aion.types.TransactionResult;
import org.aion.zero.impl.blockchain.StandaloneBlockchain;
import org.apache.commons.lang3.RandomUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests more generic aspects of the {@link AvmProvider} class.
 *
 * What's being tested here are methods related specifically to the provider at large, or more integ
 * style tests.
 */
public class AvmProviderTest {
    private String projectRootDir;

    @Before
    public void setup() throws Exception {
        // Ensure we always begin with disabled avm versions.
        if (!AvmProvider.holdsLock()) {
            Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));
        }
        AvmProvider.disableAvmVersion(AvmVersion.VERSION_1);
        AvmProvider.disableAvmVersion(AvmVersion.VERSION_2);
        AvmProvider.releaseLock();
        this.projectRootDir = AvmPathManager.getPathOfProjectRootDirectory();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        // Ensure we never exit with enabled avm versions.
        if (!AvmProvider.holdsLock()) {
            Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));
        }
        AvmProvider.disableAvmVersion(AvmVersion.VERSION_1);
        AvmProvider.disableAvmVersion(AvmVersion.VERSION_2);
        AvmProvider.releaseLock();
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testReleaseLockWhenCallerDoesNotOwnIt() {
        AvmProvider.releaseLock();
    }

    @Test
    public void testBalanceTransferTransactionVersion1() throws Exception {
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_1, this.projectRootDir);
        AvmProvider.startAvm(AvmVersion.VERSION_1);

        // Set up the repo and give the sender account some balance.
        RepositoryCache<AccountState> repository = newRepository();
        AionAddress sender = randomAddress();
        AionAddress recipient = randomAddress();
        addBalance(repository, sender, BigInteger.valueOf(1_000_000));

        // Run the transaction.
        RepositoryCache<AccountState> repositoryChild = repository.startTracking();
        IAvmExternalState externalState = newExternalState(AvmVersion.VERSION_1, repositoryChild, newEnergyRules());
        Transaction transaction = newBalanceTransferTransaction(sender, recipient, BigInteger.TEN);
        IAionVirtualMachine avm = AvmProvider.getAvm(AvmVersion.VERSION_1);
        IAvmFutureResult[] futures = avm.run(externalState, new Transaction[]{ transaction }, AvmExecutionType.MINING, 0);

        // Assert the result and state changes we expect.
        Assert.assertEquals(1, futures.length);
        TransactionResult result = futures[0].getResult();
        Assert.assertTrue(result.transactionStatus.isSuccess());
        Assert.assertEquals(BigInteger.TEN, repositoryChild.getBalance(recipient));

        AvmProvider.shutdownAvm(AvmVersion.VERSION_1);
        AvmProvider.disableAvmVersion(AvmVersion.VERSION_1);
        AvmProvider.releaseLock();
    }

    @Test
    public void testBalanceTransferTransactionVersion2() throws Exception {
        Assert.assertTrue(AvmProvider.tryAcquireLock(1, TimeUnit.MINUTES));
        AvmProvider.enableAvmVersion(AvmVersion.VERSION_2, this.projectRootDir);
        AvmProvider.startAvm(AvmVersion.VERSION_2);

        // Set up the repo and give the sender account some balance.
        RepositoryCache<AccountState> repository = newRepository();
        AionAddress sender = randomAddress();
        AionAddress recipient = randomAddress();
        addBalance(repository, sender, BigInteger.valueOf(1_000_000));

        // Run the transaction.
        RepositoryCache<AccountState> repositoryChild = repository.startTracking();
        IAvmExternalState externalState = newExternalState(AvmVersion.VERSION_2, repositoryChild, newEnergyRules());
        Transaction transaction = newBalanceTransferTransaction(sender, recipient, BigInteger.TEN);
        IAionVirtualMachine avm = AvmProvider.getAvm(AvmVersion.VERSION_2);
        IAvmFutureResult[] futures = avm.run(externalState, new Transaction[]{ transaction }, AvmExecutionType.MINING, 0);

        // Assert the result and state changes we expect.
        Assert.assertEquals(1, futures.length);
        TransactionResult result = futures[0].getResult();
        Assert.assertTrue(result.transactionStatus.isSuccess());
        Assert.assertEquals(BigInteger.TEN, repositoryChild.getBalance(recipient));

        AvmProvider.shutdownAvm(AvmVersion.VERSION_2);
        AvmProvider.disableAvmVersion(AvmVersion.VERSION_2);
        AvmProvider.releaseLock();
    }

    private void addBalance(RepositoryCache<AccountState> repository, AionAddress account, BigInteger amount) {
        RepositoryCache cache = repository.startTracking();
        cache.addBalance(account, amount);
        cache.flushTo(repository, true);
    }

    private Transaction newBalanceTransferTransaction(AionAddress sender, AionAddress recipient, BigInteger value) {
        return Transaction.contractCallTransaction(sender, recipient, new byte[32], BigInteger.ZERO, value, new byte[0], 50_000, 1);
    }

    private RepositoryCache<AccountState> newRepository() {
        return new StandaloneBlockchain.Builder()
            .withDefaultAccounts()
            .withValidatorConfiguration("simple")
            .withAvmEnabled()
            .build()
            .bc
            .getRepository()
            .startTracking();
    }

    private IEnergyRules newEnergyRules() {
        return (t, l) -> {
            if (t == TransactionType.CREATE) {
                return (200_000 <= l) && (l <= 5_000_000);
            } else {
                return (21_000 <= l) && (l <= 2_000_000);
            }
        };
    }

    private IAvmExternalState newExternalState(AvmVersion version, RepositoryCache<AccountState> repository, IEnergyRules energyRules) {
        return AvmProvider.newExternalStateBuilder(version)
            .withRepository(repository)
            .withEnergyRules(energyRules)
            .withMiner(randomAddress())
            .withDifficulty(BigInteger.ONE)
            .withBlockEnergyLimit(15_000_000)
            .withBlockNumber(1)
            .withBlockTimestamp(0)
            .allowNonceIncrement(true)
            .isLocalCall(false)
            .build();
    }

    private static AionAddress randomAddress() {
        return new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
    }
}
