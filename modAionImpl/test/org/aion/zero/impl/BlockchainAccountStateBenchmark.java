package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.ArrayList;
import java.util.Collections;

import org.aion.types.Address;
import org.aion.crypto.ECKey;
import org.aion.crypto.HashUtil;
import org.aion.db.impl.DBVendor;
import org.aion.db.utils.FileUtils;
import org.aion.mcf.core.ImportResult;

import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.zero.db.AionContractDetailsImpl;

import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BlockchainAccountStateBenchmark {

    private Random random = new SecureRandom();

    private static String baseTestPath = "test_db";

    private static String[] dbPaths = {
        "level_db_state_test",
        "level_db_expansion_test",
        "h2_db_state_test",
        "h2_db_expansion_test",
        "rocks_db_state_test",
        "rocks_db_expansion_test"
    };

    private static void resetFileState() {
        File f = new File(baseTestPath);
        if (f.exists()) {
            FileUtils.deleteRecursively(f);
            f.delete();
        }
        if (!f.mkdirs()) {
            System.out.println("failed to make directory: " + f);
        }
    }

    @AfterClass
    public static void deleteFiles() {
        File f = new File(baseTestPath);
        if (f.exists()) {
            FileUtils.deleteRecursively(f);
            f.delete();
        }
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        resetFileState();

        // memory
        StandaloneBlockchain.Bundle mockBundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .withRepoConfig(new MockRepositoryConfig(DBVendor.MOCKDB))
                        .build();

        // levelDB
        StandaloneBlockchain.Bundle levelDbBundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .withRepoConfig(
                                new MockRepositoryConfig(DBVendor.LEVELDB) {
                                    @Override
                                    public String getDbPath() {
                                        return baseTestPath + "/" + dbPaths[1];
                                    }
                                })
                        .build();

        // RocksSB
        StandaloneBlockchain.Bundle rocksDbBundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .withRepoConfig(
                                new MockRepositoryConfig(DBVendor.ROCKSDB) {
                                    @Override
                                    public String getDbPath() {
                                        return baseTestPath + "/" + dbPaths[5];
                                    }
                                })
                        .build();

        // h2
        StandaloneBlockchain.Bundle h2DbBundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .withRepoConfig(
                                new MockRepositoryConfig(DBVendor.H2) {
                                    @Override
                                    public String getDbPath() {
                                        return baseTestPath + "/" + dbPaths[3];
                                    }
                                })
                        .build();

        return Arrays.asList(
                new Object[][] {
                    {"mockDb", mockBundle},
                    {"levelDb", levelDbBundle},
                    {"rocksDb", rocksDbBundle},
                    {"h2Db", h2DbBundle}
                });
    }

    private StandaloneBlockchain.Bundle bundle;

    public BlockchainAccountStateBenchmark(StandaloneBlockchain.Bundle bundle) {
        this.bundle = bundle;
    }

    /** Test the effects of growing state of a single account */
    @Ignore
    @Test
    public void testAccountState() {
        // skipped until test can be refactored
        // TODO: we may just replace this with JMH later
        //        StandaloneBlockchain.Bundle bundle = this.bundle;
        //        StandaloneBlockchain bc = bundle.bc;
        //
        //        ECKey senderKey = bundle.privateKeys.get(0);
        //
        //        // send a total of 100 bundles,
        //        // given the rate we're sending this should give us
        //        // a 400,000 accounts (not counting the 10 pre-generated for us)
        //        AionBlock previousBlock = bc.genesis;
        //        for (int i = 0; i < 10; i++) {
        //            previousBlock = createBundleAndCheck(bc, senderKey, previousBlock);
        //        }
    }

    private static final byte[] ZERO_BYTE = new byte[0];

    private static AionBlock createBundleAndCheck(
            StandaloneBlockchain bc, ECKey key, AionBlock parentBlock) {
        BigInteger accountNonce = bc.getRepository().getNonce(new Address(key.getAddress()));
        List<AionTransaction> transactions = new ArrayList<>();

        // create 400 transactions per bundle
        // byte[] nonce, Address to, byte[] value, byte[] data, long nrg, long nrgPrice
        for (int i = 0; i < 400; i++) {
            Address destAddr = new Address(HashUtil.h256(accountNonce.toByteArray()));
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
        assertThat(block.getTransactionsList().size()).isEqualTo(400);
        // clear the trie
        bc.getRepository().flush();

        long startTime = System.nanoTime();
        ImportResult result = bc.tryToConnect(block);
        long endTime = System.nanoTime();
        System.out.println("processing time: " + (endTime - startTime) + " ns");

        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        return block;
    }

    private static final String STATE_EXPANSION_BYTECODE =
            "605060405234156100105760006000fd5b610015565b610146806100246000396000f30060506040526000356c01000000000000000000000000900463ffffffff16806326121ff0146100335761002d565b60006000fd5b341561003f5760006000fd5b610047610049565b005b6000600050805480600101828161006091906100b3565b91909060005260106000209050906002020160005b7e112233445566778899001122334455667788990011223344556677889900119091929091925091909060001916909091806001018390555550505b565b8154818355818115116100e25760020281600202836000526010600020905091820191016100e191906100e7565b5b505050565b61011791906100f1565b80821115610113576000818150806000905560010160009055506002016100f1565b5090565b905600a165627a7a72305820c4bdcf87b810c9e707e3df169b98d6a37a6e6f3356cc8c120ea06c64696f85c20029";

    @Ignore
    @Test
    public void testExpandOneAccountStorage() throws InterruptedException {
        try {
            StandaloneBlockchain.Bundle bundle = this.bundle;

            StandaloneBlockchain bc = bundle.bc;

            ECKey senderKey = bundle.privateKeys.get(0);

            // deploy contract
            Pair<AionBlock, byte[]> res = createContract(bc, senderKey, bc.getGenesis());
            bc.tryToConnect(res.getLeft());
            AionTxInfo info = bc.getTransactionInfo(res.getRight());
            assertThat(info.getReceipt().isValid()).isTrue();

            Address contractAddress = info.getReceipt().getTransaction().getContractAddress();

            byte[] contractCode =
                    bc.getRepository()
                            .getCode(info.getReceipt().getTransaction().getContractAddress());

            System.out.println("deployed contract code: " + ByteUtil.toHexString(contractCode));
            System.out.println("deployed at: " + contractAddress);

            for (int i = 0; i < 100; i++)
                createContractBundle(bc, senderKey, bc.getBestBlock(), contractAddress);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            bundle.bc.getRepository().close();
            Thread.sleep(1000L);
        }
    }

    private static Pair<AionBlock, byte[]> createContract(
            StandaloneBlockchain bc, ECKey key, AionBlock parentBlock) {
        BigInteger accountNonce = bc.getRepository().getNonce(new Address(key.getAddress()));

        // deploy
        AionTransaction creationTx =
                new AionTransaction(
                        accountNonce.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        ByteUtil.hexStringToBytes(STATE_EXPANSION_BYTECODE),
                        1000000,
                        1);

        creationTx.sign(key);
        AionBlock block = bc.createNewBlock(parentBlock, Collections.singletonList(creationTx), true);
        return Pair.of(block, creationTx.getTransactionHash());
    }

    private static AionBlock createContractBundle(
            final StandaloneBlockchain bc,
            final ECKey key,
            final AionBlock parentBlock,
            final Address contractAddress) {
        return createContractBundle(bc, key, parentBlock, contractAddress, 133);
    }

    private static AionBlock createContractBundle(
            final StandaloneBlockchain bc,
            final ECKey key,
            final AionBlock parentBlock,
            final Address contractAddress,
            final int repeat) {

        BigInteger accountNonce = bc.getRepository().getNonce(new Address(key.getAddress()));
        List<AionTransaction> transactions = new ArrayList<>();

        byte[] callData = Hex.decode("26121ff0");

        // byte[] nonce, Address to, byte[] value, byte[] data, long nrg, long nrgPrice
        for (int i = 0; i < repeat; i++) {
            AionTransaction sendTransaction =
                    new AionTransaction(
                            accountNonce.toByteArray(),
                            contractAddress,
                            BigInteger.ZERO.toByteArray(),
                            callData,
                            200000,
                            1);
            sendTransaction.sign(key);
            transactions.add(sendTransaction);
            accountNonce = accountNonce.add(BigInteger.ONE);
        }

        AionBlock block = bc.createNewBlock(parentBlock, transactions, true);

        assertThat(block.getTransactionsList().size()).isEqualTo(repeat);

        // clear the trie
        bc.getRepository().flush();

        long startTime = System.nanoTime();
        ImportResult result = bc.tryToConnect(block);
        long endTime = System.nanoTime();
        System.out.println("processing time: " + (endTime - startTime) + " ns");

        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        return block;
    }

    @Test
    public void testExpandContractsStorage() throws InterruptedException {
        try {
            StandaloneBlockchain.Bundle bundle = this.bundle;

            StandaloneBlockchain bc = bundle.bc;

            int r = random.nextInt(bundle.privateKeys.size());
            ECKey key = bundle.privateKeys.get(r);
            // deploy contract
            Pair<AionBlock, byte[]> res = createContract(bc, key, bc.getGenesis());
            bc.tryToConnect(res.getLeft());
            AionTxInfo info = bc.getTransactionInfo(res.getRight());
            assertThat(info.getReceipt().isValid()).isTrue();

            Address contractAddress = info.getReceipt().getTransaction().getContractAddress();

            byte[] contractCode =
                    bc.getRepository()
                            .getCode(info.getReceipt().getTransaction().getContractAddress());


            System.out.println("deployed contract code: " + ByteUtil.toHexString(contractCode));
            System.out.println("deployed at: " + contractAddress);

            AionContractDetailsImpl acdi = new AionContractDetailsImpl(bc.getRepository().getContractDetails(contractAddress).getEncoded());
            assertFalse(acdi.externalStorage);

            // around 350 tx to letting the contract storage from memory switch to the external storage.
            for (int i = 0; i < 9; i++) {
                createContractBundle(bc, key, bc.getBestBlock(), contractAddress, 50);
            }

             acdi = new AionContractDetailsImpl(bc.getRepository().getContractDetails(contractAddress).getEncoded());
            assertTrue(acdi.externalStorage);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            bundle.bc.getRepository().close();
            Thread.sleep(1000L);
        }
    }
}
