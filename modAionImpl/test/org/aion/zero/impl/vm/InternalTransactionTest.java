/*
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
 * Contributors:
 *     Aion foundation.
 */

package org.aion.zero.impl.vm;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.aion.zero.impl.vm.common.VmFatalException;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.core.ImportResult;
import org.aion.mcf.db.RepositoryCache;
import org.aion.util.types.DataWord;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.vm.common.BlockCachingContext;
import org.aion.zero.impl.vm.common.BulkExecutor;
import org.aion.zero.impl.types.BlockContext;
import org.aion.zero.impl.blockchain.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.base.AionTxExecSummary;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;

public class InternalTransactionTest {
    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());

    private long minEnergyPrice = 10_000_000_000L;
    /*
    pragma solidity ^0.4.0;

    contract A {
      event X();

      function f(address addr, uint gas) public {
        X();

        // B(addr).f.gas(gas)();
        addr.call.gas(gas).value(0)(bytes4(sha3("f()")));
      }
    }

    contract B {
      event Y();

      function f() public {
        Y();

        uint n = 0;
        for (uint i = 0; i < 1000; i++) {
          n += i;
        }
      }
    }
    */

    @BeforeClass
    public static void setup() {
        AvmTestConfig.supportOnlyAvmVersion1();
    }

    @AfterClass
    public static void tearDown() {
        AvmTestConfig.clearConfigurations();
    }

    @Test
    public void testLogs() throws InterruptedException {
        String contractA =
                "0x605060405234156100105760006000fd5b610015565b61013c806100246000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680632d7df21a146100335761002d565b60006000fd5b341561003f5760006000fd5b61006660048080806010013590359091602001909192908035906010019091905050610068565b005b7fc1599bd9a91e57420b9b93745d7475dc054736a3f2becd4f08b450b7012e125760405160405180910390a1828282600060405180806f662829000000000000000000000000008152601001506003019050604051809103902090506c01000000000000000000000000900491906040518363ffffffff166c01000000000000000000000000028152600401600060405180830381858a8a89f195505050505050505b5050505600a165627a7a723058205e51c42347e4353247e8419ef6cda02250d358868e2cb3782d0d5d74065f2ef70029";
        String contractB =
                "0x605060405234156100105760006000fd5b610015565b60cb806100236000396000f30060506040526000356c01000000000000000000000000900463ffffffff16806326121ff014603157602b565b60006000fd5b3415603c5760006000fd5b60426044565b005b600060007f45b3fe4256d6d198dc4c34457a04e8c048ce54df933a93061f1a0e386b52f7a260405160405180910390a160009150600090505b6103e8811015609a57808201915081505b8080600101915050607d565b5b50505600a165627a7a72305820b2bf8aef36001079d347d250e50b098ad52629336644a841d19db288f30667470029";

        StandaloneBlockchain.Bundle bundle =
                (new StandaloneBlockchain.Builder())
                        .withValidatorConfiguration("simple")
                        .withDefaultAccounts()
                        .build();
        StandaloneBlockchain bc = bundle.bc;
        ECKey deployerAccount = bundle.privateKeys.get(0);

        // ======================
        // DEPLOY
        // ======================
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx1 =
                AionTransaction.create(
                        deployerAccount,
                        nonce.toByteArray(),
                        null,
                        new byte[0],
                        ByteUtil.hexStringToBytes(contractA),
                        1_000_000L,
                        minEnergyPrice,
                        TransactionTypes.DEFAULT, null);

        nonce = nonce.add(BigInteger.ONE);
        AionTransaction tx2 =
                AionTransaction.create(
                        deployerAccount,
                        nonce.toByteArray(),
                        null,
                        new byte[0],
                        ByteUtil.hexStringToBytes(contractB),
                        1_000_000L,
                        minEnergyPrice,
                        TransactionTypes.DEFAULT, null);

        BlockContext context =
                bc.createNewMiningBlockContext(bc.getBestBlock(), List.of(tx1, tx2), false);
        ImportResult result = bc.tryToConnect(context.block);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        AionAddress addressA = TxUtil.calculateContractAddress(tx1);
        System.out.println("contract A = " + addressA);
        AionAddress addressB = TxUtil.calculateContractAddress(tx2);
        System.out.println("contract B = " + addressB);
        Thread.sleep(1000);

        // ======================
        // CALL B
        // ======================
        nonce = nonce.add(BigInteger.ONE);
        AionTransaction tx3 =
                AionTransaction.create(
                        deployerAccount,
                        nonce.toByteArray(),
                        addressB,
                        new byte[0],
                        ByteUtil.hexStringToBytes("0x26121ff0"),
                        1_000_000L,
                        minEnergyPrice,
                        TransactionTypes.DEFAULT, null);

        context = bc.createNewMiningBlockContext(bc.getBestBlock(), List.of(tx3), false);
        result = bc.tryToConnect(context.block);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        AionTxInfo info = bc.getTransactionInfo(tx3.getTransactionHash());
        System.out.println(info.getReceipt());
        assertEquals(1, info.getReceipt().getLogInfoList().size());
        Thread.sleep(1000);

        // ======================
        // CALL A (calls B, 80k)
        // ======================
        nonce = nonce.add(BigInteger.ONE);
        AionTransaction tx4 =
                AionTransaction.create(
                        deployerAccount,
                        nonce.toByteArray(),
                        addressA,
                        new byte[0],
                        ByteUtil.merge(
                                ByteUtil.hexStringToBytes("0x2d7df21a"),
                                addressB.toByteArray(),
                                new DataWord(80_000).getData()),
                        1_000_000L,
                        minEnergyPrice,
                        TransactionTypes.DEFAULT, null);

        context = bc.createNewMiningBlockContext(bc.getBestBlock(), List.of(tx4), false);
        result = bc.tryToConnect(context.block);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        info = bc.getTransactionInfo(tx4.getTransactionHash());
        System.out.println(info.getReceipt());
        assertEquals(2, info.getReceipt().getLogInfoList().size());
        Thread.sleep(1000);

        // ======================
        // CALL A (calls B, 20k)
        // ======================

        nonce = nonce.add(BigInteger.ONE);
        AionTransaction tx6 =
                AionTransaction.create(
                        deployerAccount,
                        nonce.toByteArray(),
                        addressA,
                        new byte[0],
                        ByteUtil.merge(
                                ByteUtil.hexStringToBytes("0x2d7df21a"),
                                addressB.toByteArray(),
                                new DataWord(20_000).getData()),
                        1_000_000L,
                        minEnergyPrice,
                        TransactionTypes.DEFAULT, null);

        context = bc.createNewMiningBlockContext(bc.getBestBlock(), List.of(tx6), false);
        result = bc.tryToConnect(context.block);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        info = bc.getTransactionInfo(tx6.getTransactionHash());
        System.out.println(info.getReceipt());
        assertEquals(1, info.getReceipt().getLogInfoList().size());
        Thread.sleep(1000);
        bc.close();
    }

    /*
    pragma solidity ^0.4.0;

    contract A {
        function f(int n) {
            if (n > 0) {
                this.f(n - 1);
            }
        }
    }
         */
    @Test
    public void testRecursiveCall() throws Exception {
        String contractA =
                "0x605060405234156100105760006000fd5b610015565b60e9806100236000396000f30060506040526000356c01000000000000000000000000900463ffffffff168063ec77996414603157602b565b60006000fd5b3415603c5760006000fd5b605060048080359060100190919050506052565b005b600081131560b9573063ec779964600184036040518263ffffffff166c01000000000000000000000000028152600401808281526010019150506000604051808303816000888881813b151560a75760006000fd5b5af1151560b45760006000fd5b505050505b5b505600a165627a7a7230582033f76d593b80b3468bfb0f873882bc00903a790a9b996cb8ca3bac51295994cd0029";

        StandaloneBlockchain.Bundle bundle =
                (new StandaloneBlockchain.Builder())
                        .withValidatorConfiguration("simple")
                        .withDefaultAccounts()
                        .build();
        StandaloneBlockchain bc = bundle.bc;
        ECKey deployerAccount = bundle.privateKeys.get(0);

        // ======================
        // DEPLOY
        // ======================
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx1 =
                AionTransaction.create(
                        deployerAccount,
                        nonce.toByteArray(),
                        null,
                        new byte[0],
                        ByteUtil.hexStringToBytes(contractA),
                        1_000_000L,
                        minEnergyPrice,
                        TransactionTypes.DEFAULT, null);

        BlockContext context = bc.createNewMiningBlockContext(bc.getBestBlock(), List.of(tx1), false);
        ImportResult result = bc.tryToConnect(context.block);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        AionAddress addressA = TxUtil.calculateContractAddress(tx1);
        System.out.println("contract A = " + addressA);
        Thread.sleep(1000);

        // ======================
        // CALL
        // ======================
        nonce = nonce.add(BigInteger.ONE);
        AionTransaction tx2 =
                AionTransaction.create(
                        deployerAccount,
                        nonce.toByteArray(),
                        addressA,
                        new byte[0],
                        ByteUtil.merge(
                                ByteUtil.hexStringToBytes("0xec779964"),
                                new DataWord(2).getData()),
                        1_000_000L,
                        minEnergyPrice,
                        TransactionTypes.DEFAULT, null);

        context = bc.createNewMiningBlockContext(bc.getBestBlock(), List.of(tx2), false);
        AionTxExecSummary summary = executeTransaction(bc, context, tx2);

        assertEquals(2, summary.getInternalTransactions().size());
        bc.close();
    }

    /*
    pragma solidity ^0.4.0;

    contract B {}

    contract A {
        address b;

        function A() {
            b = new B();
        }
    }
         */
    @Test
    public void testNestedCreate() throws Exception {
        String contractA =
                "0x60506040523415600f5760006000fd5b5b60166048565b604051809103906000f0801582151615602f5760006000fd5b60006000508282909180600101839055555050505b6057565b604051605a8061009f83390190565b603a806100656000396000f30060506040526008565b60006000fd00a165627a7a72305820c0eea40d4778b01848164e58898e9e8c8ab068ed5ee36ed6f0582d119ecbbede002960506040523415600f5760006000fd5b6013565b603a8060206000396000f30060506040526008565b60006000fd00a165627a7a723058208c13bc92baf844f8574632dca44c49776516cb6cd537b10ed700bf61392b6ae80029";

        StandaloneBlockchain.Bundle bundle =
                (new StandaloneBlockchain.Builder())
                        .withValidatorConfiguration("simple")
                        .withDefaultAccounts()
                        .build();
        StandaloneBlockchain bc = bundle.bc;
        ECKey deployerAccount = bundle.privateKeys.get(0);

        // ======================
        // DEPLOY
        // ======================
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx1 =
                AionTransaction.create(
                        deployerAccount,
                        nonce.toByteArray(),
                        null,
                        new byte[0],
                        ByteUtil.hexStringToBytes(contractA),
                        1_000_000L,
                        minEnergyPrice,
                        TransactionTypes.DEFAULT, null);

        BlockContext context = bc.createNewMiningBlockContext(bc.getBestBlock(), List.of(tx1), false);
        AionTxExecSummary summary = executeTransaction(bc, context, tx1);

        System.out.println(summary.getReceipt());
        for (InternalTransaction tx : summary.getInternalTransactions()) {
            System.out.println(tx);
        }
        bc.close();
    }

    @Test
    public void testNestedCreateWithExistedAccount() throws Exception {
        String contractA =
                "0x60506040523415600f5760006000fd5b5b60166048565b604051809103906000f0801582151615602f5760006000fd5b60006000508282909180600101839055555050505b6057565b604051605a8061009f83390190565b603a806100656000396000f30060506040526008565b60006000fd00a165627a7a72305820c0eea40d4778b01848164e58898e9e8c8ab068ed5ee36ed6f0582d119ecbbede002960506040523415600f5760006000fd5b6013565b603a8060206000396000f30060506040526008565b60006000fd00a165627a7a723058208c13bc92baf844f8574632dca44c49776516cb6cd537b10ed700bf61392b6ae80029";

        StandaloneBlockchain.Bundle bundle =
                (new StandaloneBlockchain.Builder())
                        .withValidatorConfiguration("simple")
                        .withDefaultAccounts()
                        .build();
        StandaloneBlockchain bc = bundle.bc;
        ECKey deployerAccount = bundle.privateKeys.get(0);

        AionAddress firstContractAddr =
                TxUtil.calculateContractAddress(
                        deployerAccount.getAddress(), BigInteger.ONE);

        AionAddress internalContractAddress =
                TxUtil.calculateContractAddress(
                        firstContractAddr.toByteArray(), BigInteger.ZERO);

        BigInteger nonce = BigInteger.ZERO;

        // ======================
        // Transfer balance to the internal contract address
        // ======================
        AionTransaction tx =
                AionTransaction.create(
                        deployerAccount,
                        nonce.toByteArray(),
                        internalContractAddress,
                        BigInteger.ONE.toByteArray(),
                        new byte[0],
                        1_000_000L,
                        minEnergyPrice,
                        TransactionTypes.DEFAULT, null);

        Block parentBlock = bc.getBestBlock();

        AionBlock newBlock =
                bc.createBlock(
                        parentBlock,
                        Collections.singletonList(tx),
                        false,
                        parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> result = bc.tryToConnectAndFetchSummary(newBlock);
        assertTrue(result.getLeft().isSuccessful());
        nonce = nonce.add(BigInteger.ONE);

        bc.set040ForkNumber(1000);

        // ======================
        // DEPLOY Failed
        // ======================
        tx =
                AionTransaction.create(
                        deployerAccount,
                        nonce.toByteArray(),
                        null,
                        new byte[0],
                        ByteUtil.hexStringToBytes(contractA),
                        1_000_000L,
                        minEnergyPrice,
                        TransactionTypes.DEFAULT, null);

        parentBlock = bc.getBestBlock();
        newBlock =
                bc.createBlock(
                        parentBlock,
                        Collections.singletonList(tx),
                        false,
                        parentBlock.getTimestamp());
        result = bc.tryToConnectAndFetchSummary(newBlock);
        assertTrue(result.getLeft().isSuccessful());
        assertEquals("reverted", result.getRight().getReceipts().get(0).getError());
        nonce = nonce.add(BigInteger.ONE);

        bc.set040ForkNumber(0);
        // ======================
        // DEPLOY
        // ======================
        tx =
                AionTransaction.create(
                        deployerAccount,
                        nonce.toByteArray(),
                        null,
                        new byte[0],
                        ByteUtil.hexStringToBytes(contractA),
                        1_000_000L,
                        minEnergyPrice,
                        TransactionTypes.DEFAULT, null);

        System.out.println("contractaddr: " + TxUtil.calculateContractAddress(tx));

        BlockContext context = bc.createNewMiningBlockContext(bc.getBestBlock(), List.of(tx), false);
        AionTxExecSummary summary = executeTransaction(bc, context, tx);

        System.out.println(summary.getReceipt());
        boolean firstItx = true;
        for (InternalTransaction itx : summary.getInternalTransactions()) {
            System.out.println(itx);
            if (firstItx) {

                AionAddress contractAddress = TxUtil.calculateContractAddress(tx);
                assertNotNull(contractAddress);
                assertTrue(bc.getRepository().hasAccountState(contractAddress));

                assertTrue(
                        bc.getRepository()
                                .hasAccountState(
                                        TxUtil.calculateContractAddress(
                                                        contractAddress.toByteArray(),
                                                        BigInteger.ZERO)));

                firstItx = false;
            }
        }
        bc.close();
    }

    private AionTxExecSummary executeTransaction(
            StandaloneBlockchain bc, BlockContext context, AionTransaction transaction)
            throws VmFatalException {
        RepositoryCache cache = bc.getRepository().startTracking();

        AionBlock block = context.block;
        AionTxExecSummary summary =
                BulkExecutor.executeTransactionWithNoPostExecutionWork(
                        block.getDifficulty(),
                        block.getNumber(),
                        block.getTimestamp(),
                        block.getNrgLimit(),
                        block.getCoinbase(),
                        transaction,
                        cache,
                        false,
                        true,
                        false,
                        false,
                        LOGGER_VM,
                        BlockCachingContext.PENDING,
                        block.getNumber() - 1,
                        false);


        cache.flush();
        return summary;
    }
}
