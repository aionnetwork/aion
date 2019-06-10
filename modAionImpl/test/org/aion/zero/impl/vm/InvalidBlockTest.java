package org.aion.zero.impl.vm;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.tx.TransactionTypes;
import org.aion.vm.api.types.Address;
import org.aion.vm.LongLivedAvm;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.contracts.Contract;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class InvalidBlockTest {
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;
    private long energyPrice = 1;

    @BeforeClass
    public static void setupAvm() {
        LongLivedAvm.createAndStartLongLivedAvm();
    }

    @AfterClass
    public static void tearDownAvm() {
        LongLivedAvm.destroy();
    }

    @Before
    public void setup() {
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .withAvmEnabled()
                        .build();
        this.blockchain = bundle.bc;
        this.deployerKey = bundle.privateKeys.get(0);
    }

    @After
    public void tearDown() {
        this.blockchain = null;
        this.deployerKey = null;
    }

    @Test
    public void test() {
        BigInteger nonce =
                this.blockchain
                        .getRepository()
                        .getNonce(Address.wrap(this.deployerKey.getAddress()));
        List<AionTransaction> transactions = makeTransactions(5, nonce);

        AionBlock parent = this.blockchain.getBestBlock();
        AionBlock block = this.blockchain.createNewBlock(parent, transactions, false);

        Pair<ImportResult, AionBlockSummary> res =
                this.blockchain.tryToConnectAndFetchSummary(block);

        assertEquals(ImportResult.INVALID_BLOCK, res.getLeft());
    }

    private List<AionTransaction> makeTransactions(int num, BigInteger initialNonce) {
        List<AionTransaction> transactions = new ArrayList<>();

        byte[] jar =
                new CodeAndArguments(
                                JarBuilder.buildJarForMainAndClassesAndUserlib(Contract.class),
                                new byte[0])
                        .encodeToBytes();
        BigInteger nonce = initialNonce;

        for (int i = 0; i < num; i++) {

            AionTransaction transaction =
                    new AionTransaction(
                            nonce.toByteArray(),
                            Address.wrap(this.deployerKey.getAddress()),
                            null,
                            BigInteger.ZERO.toByteArray(),
                            jar,
                            5_000_000L,
                            10_000_000_000L,
                            TransactionTypes.AVM_CREATE_CODE);
            transaction.sign(this.deployerKey);

            transactions.add(transaction);
            nonce = nonce.add(BigInteger.ONE);
        }

        return transactions;
    }
}
