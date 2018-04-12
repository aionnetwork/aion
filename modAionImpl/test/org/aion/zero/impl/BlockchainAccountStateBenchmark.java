package org.aion.zero.impl;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.HashUtil;
import org.aion.db.impl.DBVendor;
import org.aion.db.utils.FileUtils;
import org.aion.mcf.core.ImportResult;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@RunWith(Parameterized.class)
public class BlockchainAccountStateBenchmark {

    public static String baseTestPath = "test_db";

    public static String[] dbPaths = {
            "level_db_state_test",
            "level_db_expansion_test",
            "h2_db_state_test",
            "h2_db_expansion_test"
    };

    public static void resetFileState() {
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
        StandaloneBlockchain.Bundle mockBundle = new StandaloneBlockchain.Builder()
                .withDefaultAccounts()
                .withValidatorConfiguration("simple")
                .withRepoConfig(new MockRepositoryConfig() {
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
                })
                .build();

        // levelDB
        StandaloneBlockchain.Bundle levelDbBundle = new StandaloneBlockchain.Builder()
                .withDefaultAccounts()
                .withValidatorConfiguration("simple")
                .withRepoConfig(new MockRepositoryConfig() {
                    @Override
                    public String[] getVendorList() {
                        return new String[] { DBVendor.LEVELDB.toValue() };
                    }

                    @Override
                    public String getActiveVendor() {
                        return DBVendor.LEVELDB.toValue();
                    }

                    @Override
                    public String getDbPath() {
                        return baseTestPath + "/" + dbPaths[1];
                    }
                })
                .build();

        // RocksSB
        StandaloneBlockchain.Bundle rocksDbBundle = new StandaloneBlockchain.Builder()
                .withDefaultAccounts()
                .withValidatorConfiguration("simple")
                .withRepoConfig(new MockRepositoryConfig() {
                    @Override
                    public String[] getVendorList() {
                        return new String[] { DBVendor.ROCKSDB.toValue() };
                    }

                    @Override
                    public String getActiveVendor() {
                        return DBVendor.ROCKSDB.toValue();
                    }

                    @Override
                    public String getDbPath() {
                        return baseTestPath + "/" + dbPaths[1];
                    }
                })
                .build();

        // h2
        StandaloneBlockchain.Bundle h2DbBundle = new StandaloneBlockchain.Builder()
                .withDefaultAccounts()
                .withValidatorConfiguration("simple")
                .withRepoConfig(new MockRepositoryConfig() {
                    @Override
                    public String[] getVendorList() {
                        return new String[] { DBVendor.H2.toValue() };
                    }

                    @Override
                    public String getActiveVendor() {
                        return DBVendor.H2.toValue();
                    }

                    @Override
                    public String getDbPath() {
                        return baseTestPath + "/" + dbPaths[3];
                    }
                })
                .build();

        return Arrays.asList(new Object[][] {
                {"mockDb", mockBundle},
                {"levelDb", levelDbBundle},
                {"rocksDb", rocksDbBundle},
                {"h2Db", h2DbBundle}
        });
    }

    private String name;

    private StandaloneBlockchain.Bundle bundle;

    public BlockchainAccountStateBenchmark(String name, StandaloneBlockchain.Bundle bundle) {
        this.name = name;
        this.bundle = bundle;
    }

    /**
     * Test the effects of growing state of a single account
     */
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
    private static AionBlock createBundleAndCheck(StandaloneBlockchain bc, ECKey key, AionBlock parentBlock) {
        BigInteger accountNonce = bc.getRepository().getNonce(new Address(key.getAddress()));
        List<AionTransaction> transactions = new ArrayList<>();

        // create 400 transactions per bundle
        //byte[] nonce, Address to, byte[] value, byte[] data, long nrg, long nrgPrice
        for (int i = 0; i < 400; i++) {
            Address destAddr = new Address(HashUtil.h256(accountNonce.toByteArray()));
            AionTransaction sendTransaction = new AionTransaction(accountNonce.toByteArray(),
                    destAddr, BigInteger.ONE.toByteArray(), ZERO_BYTE, 21000, 1);
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

    private static final String STATE_EXPANSION_BYTECODE = "0x605060405260006001600050909055341561001a5760006000fd5b61001f565b6101688061002e6000396000f30060506040526000356c01000000000000000000000000900463ffffffff16806331e658a514610049578063549262ba1461008957806361bc221a1461009f57610043565b60006000fd5b34156100555760006000fd5b610073600480808060100135903590916020019091929050506100c9565b6040518082815260100191505060405180910390f35b34156100955760006000fd5b61009d6100eb565b005b34156100ab5760006000fd5b6100b3610133565b6040518082815260100191505060405180910390f35b6000600050602052818160005260105260306000209050600091509150505481565b6001600060005060006001600050546000825281601001526020019081526010016000209050600050819090905550600160008181505480929190600101919050909055505b565b600160005054815600a165627a7a72305820c615f3373321aa7e9c05d9a69e49508147861fb2a54f2945fbbaa7d851125fe80029";

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

            byte[] contractCode = bc.getRepository().getCode(info.getReceipt().getTransaction().getContractAddress());

            System.out.println("deployed contract code: " + ByteUtil.toHexString(contractCode));
            System.out.println("deployed at: " + contractAddress);

            for (int i = 0; i < 100; i++)
                createContractBundle(bc, senderKey, bc.getBestBlock(), contractAddress);
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            bundle.bc.getRepository().close();
            Thread.sleep(1000L);
        }
    }

    public static Pair<AionBlock, byte[]> createContract(StandaloneBlockchain bc, ECKey key, AionBlock parentBlock) {
        BigInteger accountNonce = bc.getRepository().getNonce(new Address(key.getAddress()));

        // deploy
        AionTransaction creationTx = new AionTransaction(
                accountNonce.toByteArray(),
                Address.EMPTY_ADDRESS(),
                BigInteger.ZERO.toByteArray(),
                ByteUtil.hexStringToBytes(STATE_EXPANSION_BYTECODE),
                1000000,
                1);

        creationTx.sign(key);
        AionBlock block = bc.createNewBlock(parentBlock, Arrays.asList(creationTx), true);
        return Pair.of(block, creationTx.getHash());
    }



    private static AionBlock createContractBundle(final StandaloneBlockchain bc,
                                                  final ECKey key,
                                                  final AionBlock parentBlock,
                                                  final Address contractAddress) {
        BigInteger accountNonce = bc.getRepository().getNonce(new Address(key.getAddress()));
        List<AionTransaction> transactions = new ArrayList<>();

        // command
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.put(HashUtil.keccak256("put()".getBytes()), 0, 4);

        // create 400 transactions per bundle
        //byte[] nonce, Address to, byte[] value, byte[] data, long nrg, long nrgPrice
        for (int i = 0; i < 133; i++) {
            AionTransaction sendTransaction = new AionTransaction(
                    accountNonce.toByteArray(),
                    contractAddress,
                    BigInteger.ZERO.toByteArray(),
                    buf.array(),
                    200000,
                    1);
            sendTransaction.sign(key);
            transactions.add(sendTransaction);
            accountNonce = accountNonce.add(BigInteger.ONE);
        }

        AionBlock block = bc.createNewBlock(parentBlock, transactions, true);

        assertThat(block.getTransactionsList().size()).isEqualTo(133);

        // clear the trie
        bc.getRepository().flush();

        long startTime = System.nanoTime();
        ImportResult result = bc.tryToConnect(block);
        long endTime = System.nanoTime();
        System.out.println("processing time: " + (endTime - startTime) + " ns");

        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        return block;
    }
}
