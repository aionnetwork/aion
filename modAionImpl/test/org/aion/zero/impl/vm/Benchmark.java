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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.ECKeyFac.ECKeyType;
import org.aion.crypto.SignatureFac;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.vm.ExecutionBatch;
import org.aion.vm.BulkExecutor;
import org.aion.vm.PostExecutionWork;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.vm.contracts.ContractUtils;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;

public class Benchmark {

    private static AionBlock block = createDummyBlock();
    private static AionRepositoryImpl db = AionRepositoryImpl.inst();
    private static IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo = db.startTracking();

    private static ECKey key;
    private static AionAddress owner;
    private static AionAddress contract;

    private static List<byte[]> recipients = new ArrayList<>();

    private static long timePrepare;
    private static long timeSignTransactions;
    private static long timeValidateTransactions;
    private static long timeExecuteTransactions;
    private static long timeFlush;

    private static Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.VM.name());

    private static void prepare() throws IOException {
        long t1 = System.currentTimeMillis();

        // create owner account
        ECKeyFac.setType(ECKeyType.ED25519);
        key = ECKeyFac.inst().create();
        owner = AionAddress.wrap(key.getAddress());
        repo.createAccount(owner);
        repo.addBalance(owner, BigInteger.valueOf(1_000_000_000L));

        // create transaction
        byte[] deployer =
                ContractUtils.getContractDeployer("BenchmarkERC20.sol", "FixedSupplyToken");
        byte[] nonce = DataWord.ZERO.getData();
        AionAddress from = owner;
        AionAddress to = null;
        byte[] value = DataWord.ZERO.getData();
        long nrg = 1_000_000L;
        long nrgPrice = 1L;
        AionTransaction tx = new AionTransaction(nonce, from, to, value, deployer, nrg, nrgPrice);

        // save contract address
        contract = tx.getContractAddress();

        // deploy contract
        ExecutionBatch details = new ExecutionBatch(block, Collections.singletonList(tx));
        BulkExecutor exec =
                new BulkExecutor(
                        details,
                        repo,
                        false,
                        true,
                        block.getNrgLimit(),
                        LOGGER,
                        getPostExecutionWork());
        AionTxExecSummary summary = exec.execute().get(0);
        assertFalse(summary.isFailed());

        long t2 = System.currentTimeMillis();
        timePrepare = t2 - t1;
    }

    private static List<AionTransaction> signTransactions(int num) {
        long t1 = System.currentTimeMillis();
        List<AionTransaction> list = new ArrayList<>();

        long ownerNonce = repo.getNonce(owner).longValue();

        for (int i = 0; i < num; i++) {
            byte[] recipient = RandomUtils.nextBytes(20);
            recipients.add(recipient);

            // transfer token to random people
            byte[] nonce = new DataWord(ownerNonce + i).getData();
            AionAddress from = owner;
            AionAddress to = contract;
            byte[] value = DataWord.ZERO.getData();
            byte[] data =
                    ByteUtil.merge(
                            Hex.decode("fbb001d6" + "000000000000000000000000"),
                            recipient,
                            DataWord.ONE.getData());
            long nrg = 1_000_000L;
            long nrgPrice = 1L;
            AionTransaction tx = new AionTransaction(nonce, from, to, value, data, nrg, nrgPrice);

            tx.sign(key);
            list.add(tx);
        }

        long t2 = System.currentTimeMillis();
        timeSignTransactions = t2 - t1;

        return list;
    }

    private static List<AionTxReceipt> validateTransactions(List<AionTransaction> txs) {
        long t1 = System.currentTimeMillis();
        List<AionTxReceipt> list = new ArrayList<>();

        for (AionTransaction tx : txs) {
            assertNotNull(tx.getTransactionHash());
            assertEquals(32, tx.getTransactionHash().length);
            assertNotNull(tx.getValue());
            assertEquals(16, tx.getValue().length);
            assertNotNull(tx.getData());
            assertNotNull(tx.getSenderAddress());
            assertNotNull(tx.getDestinationAddress());
            assertNotNull(tx.getNonce());
            assertEquals(16, tx.getNonce().length);
            assertTrue(tx.getEnergyLimit() > 0);
            assertTrue(tx.getEnergyPrice() > 0);
            assertTrue(SignatureFac.verify(tx.getRawHash(), tx.getSignature()));
        }

        long t2 = System.currentTimeMillis();
        timeValidateTransactions = t2 - t1;

        return list;
    }

    private static List<AionTxReceipt> executeTransactions(List<AionTransaction> txs) {
        long t1 = System.currentTimeMillis();
        List<AionTxReceipt> list = new ArrayList<>();

        for (AionTransaction tx : txs) {
            ExecutionBatch details = new ExecutionBatch(block, Collections.singletonList(tx));
            BulkExecutor exec =
                    new BulkExecutor(
                            details,
                            repo,
                            false,
                            true,
                            block.getNrgLimit(),
                            LOGGER,
                            getPostExecutionWork());
            AionTxExecSummary summary = exec.execute().get(0);
            assertFalse(summary.isFailed());

            list.add(summary.getReceipt());
        }

        long t2 = System.currentTimeMillis();
        timeExecuteTransactions = t2 - t1;

        return list;
    }

    private static void flush() {
        long t1 = System.currentTimeMillis();

        repo.flush();
        db.flush();

        long t2 = System.currentTimeMillis();
        timeFlush = t2 - t1;
    }

    private static void verifyState(int num) {
        long ownerNonce = repo.getNonce(owner).longValue();

        for (int i = 0; i < recipients.size(); i++) {
            byte[] nonce = new DataWord(ownerNonce + i).getData();
            AionAddress from = owner;
            AionAddress to = contract;
            byte[] value = DataWord.ZERO.getData();
            byte[] data =
                    ByteUtil.merge(
                            Hex.decode("70a08231" + "000000000000000000000000"), recipients.get(i));
            long nrg = 1_000_000L;
            long nrgPrice = 1L;
            AionTransaction tx = new AionTransaction(nonce, from, to, value, data, nrg, nrgPrice);

            ExecutionBatch details = new ExecutionBatch(block, Collections.singletonList(tx));
            BulkExecutor exec =
                    new BulkExecutor(
                            details,
                            repo,
                            false,
                            true,
                            block.getNrgLimit(),
                            LOGGER,
                            getPostExecutionWork());
            AionTxExecSummary summary = exec.execute().get(0);
            assertFalse(summary.isFailed());

            assertEquals(1, new DataWord(summary.getReceipt().getTransactionOutput()).longValue());
        }
    }

    public static void main(String args[]) throws IOException {
        int n = 10000;
        prepare();
        List<AionTransaction> list = signTransactions(n);
        validateTransactions(list);
        executeTransactions(list);
        flush();
        verifyState(n);

        System.out.println("==========================================");
        System.out.println("Benchmark (ERC20 transfer): " + n + " txs");
        System.out.println("==========================================");
        System.out.println("prepare               : " + timePrepare + " ms");
        System.out.println("sign_transactions     : " + timeSignTransactions + " ms");
        System.out.println("validate_transactions : " + timeValidateTransactions + " ms");
        System.out.println("execute_transactions  : " + timeExecuteTransactions + " ms");
        System.out.println("flush                 : " + timeFlush + " ms");
    }

    private static AionBlock createDummyBlock() {
        byte[] parentHash = new byte[32];
        byte[] coinbase = RandomUtils.nextBytes(AionAddress.SIZE);
        byte[] logsBloom = new byte[0];
        byte[] difficulty = new DataWord(0x1000000L).getData();
        long number = 1;
        long timestamp = System.currentTimeMillis() / 1000;
        byte[] extraData = new byte[0];
        byte[] nonce = new byte[32];
        byte[] receiptsRoot = new byte[32];
        byte[] transactionsRoot = new byte[32];
        byte[] stateRoot = new byte[32];
        List<AionTransaction> transactionsList = Collections.emptyList();
        byte[] solutions = new byte[0];

        // TODO: set a dummy limit of 5000000 for now
        return new AionBlock(
                parentHash,
                AionAddress.wrap(coinbase),
                logsBloom,
                difficulty,
                number,
                timestamp,
                extraData,
                nonce,
                receiptsRoot,
                transactionsRoot,
                stateRoot,
                transactionsList,
                solutions,
                0,
                5000000);
    }

    private static PostExecutionWork getPostExecutionWork() {
        return (r, s, t, b) -> {
            return 0L;
        };
    }
}
