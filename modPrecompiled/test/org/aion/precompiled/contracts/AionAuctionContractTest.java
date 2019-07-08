package org.aion.precompiled.contracts;

import static junit.framework.TestCase.assertEquals;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.core.IBlockchain;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.db.ContractDetails;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.db.PruneConfig;
import org.aion.mcf.db.RepositoryCache;
import org.aion.mcf.db.RepositoryConfig;
import org.aion.precompiled.PrecompiledResultCode;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.types.AionAddress;
import org.aion.util.types.AddressUtils;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.db.AionRepositoryCache;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.aion.zero.impl.types.AionBlock;
import org.aion.base.AionTransaction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/** Test of the Aion Auction Contract */
@Ignore
public class AionAuctionContractTest {
    // use this addr for test to trigger test time periods
    private static final AionAddress AION =
            AddressUtils.wrapAddress(
                    "0xa0eeaeabdbc92953b072afbd21f3e3fd8a4a4f5e6a6e22200db746ab75e9a99a");
    private AionAddress domainAddress1 =
            AddressUtils.wrapAddress(
                    "a011111111111111111111111111111101010101010101010101010101010101");
    private String domainName1 = "bion.aion";
    private String domainName2 = "cion.aion.aion";
    private RepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo;
    private AionAuctionContract testAAC;

    private ECKey defaultKey;
    private BigInteger defaultBidAmount = new BigInteger("1000");
    private long DEFAULT_INPUT_NRG = 24000;
    private ECKey k;
    private ECKey k2;
    private ECKey k3;
    private ECKey k4;
    private static IBlockchain blockchain;

    @BeforeClass
    public static void setupBlockChain() throws InterruptedException {
        // create standalone blockchain for testing
        createBlockchain(1, TimeUnit.SECONDS.toMillis(1));
    }

    @Before
    public void setup() {
        // setup repo
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
        repo = new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));

        defaultKey = ECKeyFac.inst().create();
        testAAC = new AionAuctionContract(repo, AION, blockchain);
        repo.createAccount(new AionAddress(defaultKey.getAddress()));
        repo.addBalance(new AionAddress(defaultKey.getAddress()), new BigInteger("4000000"));

        k = ECKeyFac.inst().create();
        k2 = ECKeyFac.inst().create();
        k3 = ECKeyFac.inst().create();
        k4 = ECKeyFac.inst().create();
        repo.createAccount(new AionAddress(k.getAddress()));
        repo.createAccount(new AionAddress(k2.getAddress()));
        repo.createAccount(new AionAddress(k3.getAddress()));
        repo.createAccount(new AionAddress(k4.getAddress()));
        repo.addBalance(new AionAddress(k.getAddress()), new BigInteger("10000"));
        repo.addBalance(new AionAddress(k2.getAddress()), new BigInteger("10000"));
        repo.addBalance(new AionAddress(k3.getAddress()), new BigInteger("10000"));
        repo.addBalance(new AionAddress(k4.getAddress()), new BigInteger("10000"));
    }

    @Test()
    public void newTest() {
        final long inputEnergy = 24000L;

        BigInteger amount = new BigInteger("1000");
        byte[] combined =
                setupInputs(
                        "12312421412.41dsfsdgsdg.aion",
                        new AionAddress(defaultKey.getAddress()),
                        amount.toByteArray(),
                        defaultKey);
        AionAuctionContract aac = new AionAuctionContract(repo, AION, blockchain);
        PrecompiledTransactionResult result = aac.execute(combined, inputEnergy);

        try {
            Thread.sleep(3 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertEquals(PrecompiledResultCode.SUCCESS, result.getResultCode());
    }

    // -------------------------------Auction Correctness Test------------------------------------//
    @Test()
    public void testWithANS() {
        final long inputEnergy = 24000L;

        BigInteger amount = new BigInteger("1000");
        byte[] combined =
                setupInputs(domainName2, new AionAddress(k.getAddress()), amount.toByteArray(), k);
        AionAuctionContract aac = new AionAuctionContract(repo, AION, blockchain);
        PrecompiledTransactionResult result = aac.execute(combined, inputEnergy);

        BigInteger amount4 = new BigInteger("6000");
        byte[] combined4 =
                setupInputs(
                        domainName2, new AionAddress(k4.getAddress()), amount4.toByteArray(), k4);
        AionAuctionContract aac4 = new AionAuctionContract(repo, AION, blockchain);
        aac4.execute(combined4, inputEnergy);

        BigInteger amount2 = new BigInteger("5000");
        byte[] combined2 =
                setupInputs(
                        domainName2, new AionAddress(k2.getAddress()), amount2.toByteArray(), k2);
        AionAuctionContract aac2 = new AionAuctionContract(repo, AION, blockchain);
        aac2.execute(combined2, inputEnergy);

        BigInteger amount3 = new BigInteger("2000");
        byte[] combined3 =
                setupInputs(
                        domainName2, new AionAddress(k3.getAddress()), amount3.toByteArray(), k3);
        AionAuctionContract aac3 = new AionAuctionContract(repo, AION, blockchain);
        aac3.execute(combined3, inputEnergy);

        // check balances after bidding
        assertEquals(9000, repo.getBalance(new AionAddress(k.getAddress())).intValue());
        assertEquals(5000, repo.getBalance(new AionAddress(k2.getAddress())).intValue());
        assertEquals(8000, repo.getBalance(new AionAddress(k3.getAddress())).intValue());
        assertEquals(4000, repo.getBalance(new AionAddress(k4.getAddress())).intValue()); // winner

        try {
            Thread.sleep(3 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // check balances after auction, balances should be returned
        assertEquals(10000, repo.getBalance(new AionAddress(k.getAddress())).intValue());
        assertEquals(10000, repo.getBalance(new AionAddress(k2.getAddress())).intValue());
        assertEquals(10000, repo.getBalance(new AionAddress(k3.getAddress())).intValue());
        assertEquals(
                5000,
                repo.getBalance(new AionAddress(k4.getAddress())).intValue()); // deposits 5000

        AionNameServiceContract ansc2 =
                new AionNameServiceContract(
                        repo,
                        new AionAddress(result.getReturnData()),
                        new AionAddress(k4.getAddress()));
        assertEquals(PrecompiledResultCode.SUCCESS, result.getResultCode());
    }

    @Test
    public void testCheckBidBalances() {
        final long inputEnergy = 24000L;
        BigInteger amount = new BigInteger("1000");
        byte[] combined =
                setupInputs(domainName1, new AionAddress(k.getAddress()), amount.toByteArray(), k);
        AionAuctionContract aac = new AionAuctionContract(repo, AION, blockchain);
        aac.execute(combined, inputEnergy);

        BigInteger amount2 = new BigInteger("3000");
        byte[] combined2 =
                setupInputs(
                        domainName1, new AionAddress(k2.getAddress()), amount2.toByteArray(), k2);
        AionAuctionContract aac2 = new AionAuctionContract(repo, AION, blockchain);
        aac2.execute(combined2, inputEnergy);

        BigInteger amount3 = new BigInteger("2000");
        byte[] combined3 =
                setupInputs(
                        domainName1, new AionAddress(k3.getAddress()), amount3.toByteArray(), k3);
        AionAuctionContract aac3 = new AionAuctionContract(repo, AION, blockchain);
        aac3.execute(combined3, inputEnergy);

        BigInteger amount4 = new BigInteger("5000");
        byte[] combined4 =
                setupInputs(
                        domainName1, new AionAddress(k4.getAddress()), amount4.toByteArray(), k4);
        AionAuctionContract aac4 = new AionAuctionContract(repo, AION, blockchain);
        aac4.execute(combined4, inputEnergy);

        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // check balances after bidding
        assertEquals(9000, repo.getBalance(new AionAddress(k.getAddress())).intValue());
        assertEquals(7000, repo.getBalance(new AionAddress(k2.getAddress())).intValue());
        assertEquals(8000, repo.getBalance(new AionAddress(k3.getAddress())).intValue());
        assertEquals(5000, repo.getBalance(new AionAddress(k4.getAddress())).intValue());

        BigInteger amount6 = new BigInteger("2000");

        byte[] combined6 =
                setupInputs(domainName2, new AionAddress(k.getAddress()), amount6.toByteArray(), k);
        AionAuctionContract aac6 = new AionAuctionContract(repo, AION, blockchain);
        aac6.execute(combined6, inputEnergy);

        BigInteger amount7 = new BigInteger("4000");
        byte[] combined7 =
                setupInputs(
                        domainName2, new AionAddress(k2.getAddress()), amount7.toByteArray(), k2);
        AionAuctionContract aac7 = new AionAuctionContract(repo, AION, blockchain);
        aac7.execute(combined7, inputEnergy);

        // check balances after bidding both domains
        assertEquals(7000, repo.getBalance(new AionAddress(k.getAddress())).intValue());
        assertEquals(3000, repo.getBalance(new AionAddress(k2.getAddress())).intValue());
        assertEquals(8000, repo.getBalance(new AionAddress(k3.getAddress())).intValue());
        assertEquals(5000, repo.getBalance(new AionAddress(k4.getAddress())).intValue());

        try {
            Thread.sleep(2500L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // check balances after both auctions are complete, winners should have their deposits gone
        assertEquals(10000, repo.getBalance(new AionAddress(k.getAddress())).intValue());
        assertEquals(8000, repo.getBalance(new AionAddress(k2.getAddress())).intValue());
        assertEquals(10000, repo.getBalance(new AionAddress(k3.getAddress())).intValue());
        assertEquals(7000, repo.getBalance(new AionAddress(k4.getAddress())).intValue());

        try {
            Thread.sleep(2 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // check balances after both domains become inactive, all accounts should have their
        // original balance
        assertEquals(10000, repo.getBalance(new AionAddress(k.getAddress())).intValue());
        assertEquals(10000, repo.getBalance(new AionAddress(k2.getAddress())).intValue());
        assertEquals(10000, repo.getBalance(new AionAddress(k3.getAddress())).intValue());
        assertEquals(10000, repo.getBalance(new AionAddress(k4.getAddress())).intValue());
    }

    @Test()
    public void testQuery() {
        ECKey notRegisteredKey = ECKeyFac.inst().create();

        // register multiple domain names
        BigInteger amount = new BigInteger("1000");
        BigInteger amount2 = new BigInteger("2000");
        BigInteger amount3 = new BigInteger("3000");
        BigInteger amount4 = new BigInteger("4000");

        byte[] combined =
                setupInputs("aion.aion", new AionAddress(k.getAddress()), amount.toByteArray(), k);
        AionAuctionContract aac = new AionAuctionContract(repo, AION, blockchain);
        aac.execute(combined, DEFAULT_INPUT_NRG);

        byte[] combined2 =
                setupInputs("aaaa.aion", new AionAddress(k.getAddress()), amount2.toByteArray(), k);
        AionAuctionContract aac2 = new AionAuctionContract(repo, AION, blockchain);
        aac.execute(combined2, DEFAULT_INPUT_NRG);

        byte[] combined3 =
                setupInputs(
                        "bbbb.aaaa.aion",
                        new AionAddress(k2.getAddress()),
                        amount3.toByteArray(),
                        k2);
        AionAuctionContract aac3 = new AionAuctionContract(repo, AION, blockchain);
        aac.execute(combined3, DEFAULT_INPUT_NRG);

        byte[] combined8 =
                setupInputs("aion.aion", new AionAddress(k.getAddress()), amount2.toByteArray(), k);
        AionAuctionContract aac8 = new AionAuctionContract(repo, AION, blockchain);
        aac.execute(combined8, DEFAULT_INPUT_NRG);

        aac.displayMyBidsLRU(k);
        aac.displayMyBidForDomainLRU("aion.aion", k);

        // displayAuctionDomainLRU Tests, show with debug
        aac.execute(
                setupInputs("111.aion", new AionAddress(k.getAddress()), amount.toByteArray(), k),
                DEFAULT_INPUT_NRG);
        aac.execute(
                setupInputs("222.aion", new AionAddress(k.getAddress()), amount.toByteArray(), k),
                DEFAULT_INPUT_NRG);
        aac.execute(
                setupInputs("333.aion", new AionAddress(k.getAddress()), amount.toByteArray(), k),
                DEFAULT_INPUT_NRG);
        aac.execute(
                setupInputs("444.aion", new AionAddress(k.getAddress()), amount.toByteArray(), k),
                DEFAULT_INPUT_NRG);
        aac.execute(
                setupInputs("555.aion", new AionAddress(k.getAddress()), amount.toByteArray(), k),
                DEFAULT_INPUT_NRG);
        aac.execute(
                setupInputs("666.aion", new AionAddress(k.getAddress()), amount.toByteArray(), k),
                DEFAULT_INPUT_NRG);
        aac.displayAuctionDomainLRU("111.aion"); // 1
        aac.displayAuctionDomainLRU("222.aion"); // 1 2
        aac.displayAuctionDomainLRU("333.aion"); // 1 2 3
        aac.displayAuctionDomainLRU("444.aion"); // 1 2 3 4
        aac.displayAuctionDomainLRU("111.aion"); // 2 3 4 1
        aac.displayAuctionDomainLRU("555.aion"); // 3 4 1 5
        aac.displayAuctionDomainLRU("666.aion"); // 4 1 5 6
        aac.displayAuctionDomainLRU("555.aion"); // 4 1 6 5
        aac.displayAuctionDomainLRU("nnn.aion");

        // getResultCode queries
        aac.displayMyBidsLRU(notRegisteredKey);
        aac.displayMyBidsLRU(k4); // has no bids
        aac.displayMyBidForDomainLRU("111.aion", notRegisteredKey);
        aac.displayMyBidForDomainLRU("111.aion", k4);
        aac.displayMyBidForDomainLRU("notInAuctionDomain, ", k);

        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        byte[] combined4 =
                setupInputs(
                        "cccc.aaaa.aion",
                        new AionAddress(k.getAddress()),
                        amount3.toByteArray(),
                        k);
        AionAuctionContract aac4 = new AionAuctionContract(repo, AION, blockchain);
        aac.execute(combined4, DEFAULT_INPUT_NRG);

        byte[] combined6 =
                setupInputs(
                        "cccc.aaaa.aion",
                        new AionAddress(k2.getAddress()),
                        amount.toByteArray(),
                        k2);
        AionAuctionContract aac6 = new AionAuctionContract(repo, AION, blockchain);
        aac.execute(combined6, DEFAULT_INPUT_NRG);

        byte[] combined7 =
                setupInputs(
                        "cccc.aaaa.aion",
                        new AionAddress(k2.getAddress()),
                        amount4.toByteArray(),
                        k2);
        AionAuctionContract aac7 = new AionAuctionContract(repo, AION, blockchain);
        aac.execute(combined7, DEFAULT_INPUT_NRG);

        aac.displayAllAuctionDomains();

        // wait for some auction domains to finish
        try {
            Thread.sleep(1500L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        aac.displayAllAuctionDomains();

        // wait for all auction to complete
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        aac.displayAllAuctionDomains();
    }

    // -------------------------------------Basic Tests ------------------------------------------//
    @Test
    public void testActiveDomainTimeExtensionRequestPass() {
        final long inputEnergy = 24000L;

        BigInteger amount = new BigInteger("1000");
        byte[] combined =
                setupInputs(domainName1, new AionAddress(k.getAddress()), amount.toByteArray(), k);
        AionAuctionContract aac = new AionAuctionContract(repo, AION, blockchain);
        aac.execute(combined, inputEnergy);

        try {
            Thread.sleep(2500L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // try to extend - should work
        byte[] combined2 = setupForExtension(domainName1, new AionAddress(k.getAddress()));
        PrecompiledTransactionResult res = aac.execute(combined2, inputEnergy);

        // try to extend 2nd time in a row - should be denied
        byte[] combined3 = setupForExtension(domainName1, new AionAddress(k.getAddress()));
        PrecompiledTransactionResult res2 = aac.execute(combined3, inputEnergy);

        assertEquals(PrecompiledResultCode.SUCCESS, res.getResultCode());

        assertEquals(PrecompiledResultCode.FAILURE, res2.getResultCode());
        Assert.assertArrayEquals("already been extended".getBytes(), res2.getReturnData());

        // uncomment to see extension output
        //        try {
        //            Thread.sleep(2000L);
        //        } catch (InterruptedException e) {
        //            e.printStackTrace();
        //        }
    }

    @Test
    public void testActiveDomainTimeExtensionRequestFailure() {
        final long inputEnergy = 24000L;

        BigInteger amount = new BigInteger("1000");
        byte[] combined =
                setupInputs(domainName1, new AionAddress(k.getAddress()), amount.toByteArray(), k);
        AionAuctionContract aac = new AionAuctionContract(repo, AION, blockchain);
        aac.execute(combined, inputEnergy);

        try {
            Thread.sleep(2500L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // try to extend - should not work since owner is incorrect
        byte[] combined2 = setupForExtension(domainName1, new AionAddress(k2.getAddress()));
        PrecompiledTransactionResult res = aac.execute(combined2, inputEnergy);

        assertEquals(PrecompiledResultCode.FAILURE, res.getResultCode());
    }

    @Test
    public void testHasActiveParentDomain() {
        final long inputEnergy = 24000L;
        BigInteger amount = new BigInteger("1000");

        byte[] combined =
                setupInputs(
                        "parent.aion", new AionAddress(k.getAddress()), amount.toByteArray(), k);
        AionAuctionContract aac = new AionAuctionContract(repo, AION, blockchain);
        aac.execute(combined, inputEnergy);

        // wait for parent domain to become active
        try {
            Thread.sleep(2500L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        byte[] combined2 =
                setupInputs(
                        "child.parent.aion",
                        new AionAddress(k.getAddress()),
                        amount.toByteArray(),
                        k);
        AionAuctionContract aac2 = new AionAuctionContract(repo, AION, blockchain);
        PrecompiledTransactionResult result2 = aac2.execute(combined2, inputEnergy);

        assertEquals(PrecompiledResultCode.FAILURE, result2.getResultCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnregisteredDomain() {
        ECKey k = ECKeyFac.inst().create();
        AionNameServiceContract ansc =
                new AionNameServiceContract(repo, domainAddress1, new AionAddress(k.getAddress()));
    }

    @Test()
    public void testInvalidDomainNames() {
        byte[] combined =
                setupInputs(
                        "aa.aion",
                        new AionAddress(defaultKey.getAddress()),
                        defaultBidAmount.toByteArray(),
                        defaultKey);
        AionAuctionContract aac = new AionAuctionContract(repo, AION, blockchain);
        PrecompiledTransactionResult result = aac.execute(combined, DEFAULT_INPUT_NRG);
        assertEquals(PrecompiledResultCode.FAILURE, result.getResultCode());

        byte[] combined2 =
                setupInputs(
                        "#$%aion.aion",
                        new AionAddress(defaultKey.getAddress()),
                        defaultBidAmount.toByteArray(),
                        defaultKey);
        AionAuctionContract aac2 = new AionAuctionContract(repo, AION, blockchain);
        PrecompiledTransactionResult result2 = aac2.execute(combined2, DEFAULT_INPUT_NRG);
        assertEquals(PrecompiledResultCode.FAILURE, result2.getResultCode());

        byte[] combined3 =
                setupInputs(
                        "withoutdotaion",
                        new AionAddress(defaultKey.getAddress()),
                        defaultBidAmount.toByteArray(),
                        defaultKey);
        AionAuctionContract aac3 = new AionAuctionContract(repo, AION, blockchain);
        PrecompiledTransactionResult result3 = aac3.execute(combined3, DEFAULT_INPUT_NRG);
        assertEquals(PrecompiledResultCode.FAILURE, result3.getResultCode());

        byte[] combined4 =
                setupInputs(
                        "ai.ai.ai.ai.aion",
                        new AionAddress(defaultKey.getAddress()),
                        defaultBidAmount.toByteArray(),
                        defaultKey);
        AionAuctionContract aac4 = new AionAuctionContract(repo, AION, blockchain);
        PrecompiledTransactionResult result4 = aac4.execute(combined4, DEFAULT_INPUT_NRG);
        assertEquals(PrecompiledResultCode.FAILURE, result4.getResultCode());

        byte[] combined5 =
                setupInputs(
                        "network.aion",
                        new AionAddress(defaultKey.getAddress()),
                        defaultBidAmount.toByteArray(),
                        defaultKey);
        AionAuctionContract aac5 = new AionAuctionContract(repo, AION, blockchain);
        PrecompiledTransactionResult result5 = aac5.execute(combined5, DEFAULT_INPUT_NRG);
        assertEquals(PrecompiledResultCode.FAILURE, result5.getResultCode());
    }

    @Test
    public void testBidderAddressDoesNotExist() {
        ECKey notExistKey = ECKeyFac.inst().create();
        byte[] combined =
                setupInputs(
                        "bion.bion.aion",
                        new AionAddress(notExistKey.getAddress()),
                        defaultBidAmount.toByteArray(),
                        notExistKey);
        AionAuctionContract aac = new AionAuctionContract(repo, AION, blockchain);
        PrecompiledTransactionResult result = aac.execute(combined, DEFAULT_INPUT_NRG);
        assertEquals(PrecompiledResultCode.FAILURE, result.getResultCode());
        Assert.assertArrayEquals(
                "bidder account does not exist".getBytes(), result.getReturnData());
    }

    @Test
    public void testInsufficientBalance() {
        ECKey poorKey = ECKeyFac.inst().create();
        repo.createAccount(new AionAddress(poorKey.getAddress()));
        repo.addBalance(new AionAddress(poorKey.getAddress()), new BigInteger("100"));

        byte[] combined3 =
                setupInputs(
                        domainName1,
                        new AionAddress(poorKey.getAddress()),
                        defaultBidAmount.toByteArray(),
                        poorKey);
        PrecompiledTransactionResult result = testAAC.execute(combined3, DEFAULT_INPUT_NRG);
        assertEquals(PrecompiledResultCode.FAILURE, result.getResultCode());
        Assert.assertArrayEquals("insufficient balance".getBytes(), result.getReturnData());
    }

    @Test
    public void testIncorrectInputLength() {
        byte[] input =
                setupInputs(
                        domainName1,
                        new AionAddress(defaultKey.getAddress()),
                        defaultBidAmount.toByteArray(),
                        defaultKey);
        byte[] wrongInput = new byte[130];
        byte[] wrongInput2 = new byte[131];
        byte[] wrongInput3 =
                setupForExtension(domainName1, new AionAddress(defaultKey.getAddress()));
        byte[] wrongInput5 = new byte[wrongInput3.length];
        byte[] wrongInput4 = new byte[input.length - 2];

        System.arraycopy(input, 0, wrongInput, 0, 130);
        PrecompiledTransactionResult result = testAAC.execute(wrongInput, DEFAULT_INPUT_NRG);
        System.arraycopy(input, 0, wrongInput2, 0, 131);
        PrecompiledTransactionResult result2 = testAAC.execute(wrongInput2, DEFAULT_INPUT_NRG);

        assertEquals(PrecompiledResultCode.FAILURE, result.getResultCode());
        assertEquals(result.getEnergyRemaining(), 4000);
        Assert.assertArrayEquals("incorrect input length".getBytes(), result.getReturnData());

        wrongInput3[0] = -1;
        System.arraycopy(input, 0, wrongInput4, 0, input.length - 2);
        PrecompiledTransactionResult result3 = testAAC.execute(wrongInput5, DEFAULT_INPUT_NRG);
        PrecompiledTransactionResult result4 = testAAC.execute(wrongInput4, DEFAULT_INPUT_NRG);
    }

    @Test
    public void testIncorrectSignature() {
        byte[] input =
                setupInputs(
                        domainName1,
                        new AionAddress(defaultKey.getAddress()),
                        defaultBidAmount.toByteArray(),
                        defaultKey);
        // modify the signature in the 65th byte (arbitrarily)
        input[110] = (byte) ~input[65];
        PrecompiledTransactionResult result = testAAC.execute(input, DEFAULT_INPUT_NRG);

        assertEquals(PrecompiledResultCode.FAILURE, result.getResultCode());
        assertEquals(result.getEnergyRemaining(), 4000);
        Assert.assertArrayEquals("incorrect signature".getBytes(), result.getReturnData());
    }

    @Test
    public void testIncorrectPublicKey() {
        ECKey anotherKey = ECKeyFac.inst().create();
        // use another key as the input
        byte[] input =
                setupInputs(
                        domainName1,
                        new AionAddress(defaultKey.getAddress()),
                        defaultBidAmount.toByteArray(),
                        anotherKey);
        PrecompiledTransactionResult result = testAAC.execute(input, DEFAULT_INPUT_NRG);

        assertEquals(PrecompiledResultCode.FAILURE, result.getResultCode());
        assertEquals(result.getEnergyRemaining(), 4000);
        Assert.assertArrayEquals("incorrect key".getBytes(), result.getReturnData());
    }

    @Test
    public void testInsufficientEnergy() {
        byte[] input =
                setupInputs(
                        domainName1,
                        new AionAddress(defaultKey.getAddress()),
                        defaultBidAmount.toByteArray(),
                        defaultKey);
        PrecompiledTransactionResult result = testAAC.execute(input, 18000);

        assertEquals(PrecompiledResultCode.OUT_OF_NRG, result.getResultCode());
        assertEquals(result.getEnergyRemaining(), 0);
        Assert.assertArrayEquals("insufficient energy".getBytes(), result.getReturnData());
    }

    @Test
    public void testNegativeBidValue() {
        BigInteger negativeBidAmount = new BigInteger("-100");
        byte[] input =
                setupInputs(
                        domainName1,
                        new AionAddress(defaultKey.getAddress()),
                        negativeBidAmount.toByteArray(),
                        defaultKey);
        PrecompiledTransactionResult result = testAAC.execute(input, DEFAULT_INPUT_NRG);

        assertEquals(PrecompiledResultCode.FAILURE, result.getResultCode());
        assertEquals(result.getEnergyRemaining(), 4000);
        Assert.assertArrayEquals("negative bid value".getBytes(), result.getReturnData());
    }

    @Test
    public void testRequestedDomainIsAlreadyActive() {
        byte[] input =
                setupInputs(
                        domainName1,
                        new AionAddress(defaultKey.getAddress()),
                        defaultBidAmount.toByteArray(),
                        defaultKey);
        testAAC.execute(input, DEFAULT_INPUT_NRG);

        // wait more than 3 seconds for auction to finish and domainAddress1 to become active
        // times in the contract class need to be set for testing
        try {
            Thread.sleep(3000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // setup a new aac to call and request a domain that is already active
        BigInteger bidAmount2 = new BigInteger("2000");
        AionAuctionContract aac2 = new AionAuctionContract(repo, AION, blockchain);
        byte[] input2 =
                setupInputs(
                        domainName1,
                        new AionAddress(k2.getAddress()),
                        bidAmount2.toByteArray(),
                        k2);
        PrecompiledTransactionResult result2 = aac2.execute(input2, DEFAULT_INPUT_NRG);

        assertEquals(PrecompiledResultCode.FAILURE, result2.getResultCode());
        assertEquals(result2.getEnergyRemaining(), 4000);
        Assert.assertArrayEquals(
                "requested domain is already active".getBytes(), result2.getReturnData());
    }

    @Test
    public void testOverwritingBidsWithSmallerValue() {
        byte[] input =
                setupInputs(
                        domainName1,
                        new AionAddress(defaultKey.getAddress()),
                        defaultBidAmount.toByteArray(),
                        defaultKey);
        PrecompiledTransactionResult result = testAAC.execute(input, DEFAULT_INPUT_NRG);

        BigInteger newBidAmount = new BigInteger("50");
        byte[] input2 =
                setupInputs(
                        domainName1,
                        new AionAddress(defaultKey.getAddress()),
                        newBidAmount.toByteArray(),
                        defaultKey);
        PrecompiledTransactionResult result2 = testAAC.execute(input2, DEFAULT_INPUT_NRG);

        BigInteger anotherBid = new BigInteger("10");
        byte[] input3 =
                setupInputs(
                        domainName1,
                        new AionAddress(k2.getAddress()),
                        anotherBid.toByteArray(),
                        k2);
        PrecompiledTransactionResult result3 = testAAC.execute(input3, DEFAULT_INPUT_NRG);

        try {
            Thread.sleep(2 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testOverwritingBidsWithLargerValue() {
        BigInteger anotherBid = new BigInteger("50");
        byte[] input3 =
                setupInputs(
                        domainName1,
                        new AionAddress(k2.getAddress()),
                        anotherBid.toByteArray(),
                        k2);
        PrecompiledTransactionResult result3 = testAAC.execute(input3, DEFAULT_INPUT_NRG);

        byte[] input =
                setupInputs(
                        domainName1,
                        new AionAddress(defaultKey.getAddress()),
                        defaultBidAmount.toByteArray(),
                        defaultKey);
        PrecompiledTransactionResult result = testAAC.execute(input, DEFAULT_INPUT_NRG);

        BigInteger newBidAmount = new BigInteger("10000");
        byte[] input2 =
                setupInputs(
                        domainName1,
                        new AionAddress(defaultKey.getAddress()),
                        newBidAmount.toByteArray(),
                        defaultKey);
        PrecompiledTransactionResult result2 = testAAC.execute(input2, DEFAULT_INPUT_NRG);

        try {
            Thread.sleep(2 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testRequestInactiveDomain() {
        byte[] input =
                setupInputs(
                        domainName1,
                        new AionAddress(defaultKey.getAddress()),
                        defaultBidAmount.toByteArray(),
                        defaultKey);
        testAAC.execute(input, DEFAULT_INPUT_NRG);

        AionAuctionContract aac3 = new AionAuctionContract(repo, AION, blockchain);
        BigInteger anotherAmount = new BigInteger("5000");
        byte[] input3 =
                setupInputs(
                        domainName1,
                        new AionAddress(k3.getAddress()),
                        anotherAmount.toByteArray(),
                        k3);
        aac3.execute(input3, DEFAULT_INPUT_NRG);

        // let the domain become inactive,
        // times in the contract class need to be set for testing
        try {
            Thread.sleep(4500L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        byte[] input2 =
                setupInputs(
                        domainName1,
                        new AionAddress(defaultKey.getAddress()),
                        defaultBidAmount.toByteArray(),
                        defaultKey);
        PrecompiledTransactionResult result2 = testAAC.execute(input2, DEFAULT_INPUT_NRG);

        try {
            Thread.sleep(2 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals(PrecompiledResultCode.SUCCESS, result2.getResultCode());
        assertEquals(result2.getEnergyRemaining(), 4000);
        assertEquals(32, result2.getReturnData().length); // check that an address was returned
    }

    private byte[] setupInputs(
            String domainName, AionAddress ownerAddress, byte[] amount, ECKey k) {
        int domainLength = domainName.length();
        int amountLength = amount.length;
        int offset = 0;
        byte[] ret = new byte[1 + domainLength + 32 + 96 + 1 + amountLength];

        ISignature signature = k.sign(ownerAddress.toByteArray());

        System.arraycopy(new byte[] {(byte) domainLength}, 0, ret, offset, 1);
        offset++;
        System.arraycopy(domainName.getBytes(), 0, ret, offset, domainLength);
        offset = offset + domainLength;
        System.arraycopy(ownerAddress.toByteArray(), 0, ret, offset, 32);
        offset = offset + 32;
        System.arraycopy(signature.toBytes(), 0, ret, offset, 96);
        offset = offset + 96;
        System.arraycopy(new byte[] {(byte) amountLength}, 0, ret, offset, 1);
        offset++;
        System.arraycopy(amount, 0, ret, offset, amountLength);

        return ret;
    }

    private byte[] setupForExtension(String domainName, AionAddress ownerAddress) {
        int domainLength = domainName.length();
        int offset = 0;
        byte[] ret = new byte[1 + domainLength + 32 + 96 + 1];

        ISignature signature = k.sign(ownerAddress.toByteArray());

        System.arraycopy(new byte[] {(byte) domainLength}, 0, ret, offset, 1);
        offset++;
        System.arraycopy(domainName.getBytes(), 0, ret, offset, domainLength);
        offset = offset + domainLength;
        System.arraycopy(ownerAddress.toByteArray(), 0, ret, offset, 32);
        offset = offset + 32;
        System.arraycopy(signature.toBytes(), 0, ret, offset, 96);
        offset = offset + 96;
        System.arraycopy(new byte[] {(byte) 0}, 0, ret, offset, 1);

        return ret;
    }

    // Creates a new blockchain with numBlocks blocks and sets it to the blockchain field. This
    // method creates a new block every sleepDuration milliseconds.
    private static void createBlockchain(int numBlocks, long sleepDuration)
            throws InterruptedException {
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .build();

        StandaloneBlockchain bc = bundle.bc;
        ECKey senderKey = bundle.privateKeys.get(0);
        AionBlock previousBlock = bc.genesis;

        for (int i = 0; i < numBlocks; i++) {
            if (sleepDuration > 0) {
                Thread.sleep(sleepDuration);
            }
            previousBlock = createBundleAndCheck(bc, senderKey, previousBlock);
        }
        blockchain = bc;
    }

    private static AionBlock createBundleAndCheck(
            StandaloneBlockchain bc, ECKey key, AionBlock parentBlock) {
        byte[] ZERO_BYTE = new byte[0];

        BigInteger accountNonce = bc.getRepository().getNonce(new AionAddress(key.getAddress()));
        List<AionTransaction> transactions = new ArrayList<>();

        // create 100 transactions per bundle
        for (int i = 0; i < 100; i++) {
            AionAddress destAddr = new AionAddress(HashUtil.h256(accountNonce.toByteArray()));
            AionTransaction sendTransaction =
                    new AionTransaction(
                            accountNonce.toByteArray(),
                            destAddr,
                            BigInteger.ONE.toByteArray(),
                            ZERO_BYTE,
                            21000,
                            1);
            sendTransaction.sign(key);
            transactions.add(sendTransaction);
            accountNonce = accountNonce.add(BigInteger.ONE);
        }

        AionBlock block = bc.createNewBlock(parentBlock, transactions, true);
        assertEquals(100, block.getTransactionsList().size());
        // clear the trie
        bc.getRepository().flush();

        ImportResult result = bc.tryToConnect(block);
        assertEquals(ImportResult.IMPORTED_BEST, result);
        return block;
    }
}
