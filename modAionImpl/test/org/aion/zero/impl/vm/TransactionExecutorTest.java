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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import org.aion.base.type.AionAddress;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.util.Hex;
import org.aion.crypto.ECKey;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.vm.types.DataWord;
import org.aion.vm.ExecutionBatch;
import org.aion.vm.BulkExecutor;
import org.aion.vm.PostExecutionWork;
import org.aion.zero.impl.BlockContext;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.StandaloneBlockchain.Builder;
import org.aion.zero.impl.vm.contracts.ContractUtils;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

/** Tests TransactionExecutor in more of an integration style testing. */
public class TransactionExecutorTest {
    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    private static final String f_func = "26121ff0";
    private static final String g_func = "e2179b8e";
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;
    private AionAddress deployer;

    @Before
    public void setup() {
        StandaloneBlockchain.Bundle bundle =
                (new StandaloneBlockchain.Builder())
                        .withValidatorConfiguration("simple")
                        .withDefaultAccounts()
                        .build();
        blockchain = bundle.bc;
        deployerKey = bundle.privateKeys.get(0);
        deployer = new AionAddress(deployerKey.getAddress());
    }

    @After
    public void tearDown() {
        blockchain = null;
        deployerKey = null;
        deployer = null;
    }

    @Test
    public void testExecutor() throws IOException {
        AionAddress to = getNewRecipient(true);
        byte[] deployCode = ContractUtils.getContractDeployer("ByteArrayMap.sol", "ByteArrayMap");
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), to, value.toByteArray(), deployCode, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());
        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));
        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);

        IRepositoryCache repo = blockchain.getRepository().startTracking();
        ExecutionBatch details = new ExecutionBatch(context.block, Collections.singletonList(tx));
        BulkExecutor exec = new BulkExecutor(details, repo, false, true, context.block.getNrgLimit(), LOGGER_VM, getPostExecutionWork());
        AionTxExecSummary summary = exec.execute().get(0);
        BigInteger refund = summary.getRefund();

        // We expect that there is a new account created, the contract, with 0 balance and 0 nonce
        // and that its code is the contract body. We also expect that the deployer (sender) has
        // its nonce incremented and its balance is now equal to its old balance minus the
        // transaction
        // fee plus the refund
        byte[] body = ContractUtils.getContractBody("ByteArrayMap.sol", "ByteArrayMap");

        assertEquals("", summary.getReceipt().getError());
        assertArrayEquals(body, summary.getResult());

        AionAddress contract = summary.getTransaction().getContractAddress();
        assertArrayEquals(body, repo.getCode(contract));
        assertEquals(BigInteger.ZERO, repo.getBalance(contract));
        assertEquals(BigInteger.ZERO, repo.getNonce(contract));

        BigInteger txFee = BigInteger.valueOf(nrg).multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(
                Builder.DEFAULT_BALANCE.subtract(txFee).add(refund), repo.getBalance(deployer));
        assertEquals(BigInteger.ONE, repo.getNonce(deployer));
    }

    @Test
    public void testExecutorBlind() throws IOException {
        AionAddress to = getNewRecipient(true);
        byte[] deployCode = ContractUtils.getContractDeployer("ByteArrayMap.sol", "ByteArrayMap");
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), to, value.toByteArray(), deployCode, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        ImportResult result = blockchain.tryToConnect(context.block);
        assertEquals(ImportResult.IMPORTED_BEST, result);

        // We expect that there is a new account created, the contract, with 0 balance and 0 nonce
        // and that its code is the contract body. We also expect that the deployer (sender) has
        // its nonce incremented and its balance is now equal to its old balance minus the
        // transaction
        // fee plus the refund
        byte[] body = ContractUtils.getContractBody("ByteArrayMap.sol", "ByteArrayMap");
        AionAddress contract = tx.getContractAddress();

        assertArrayEquals(body, blockchain.getRepository().getCode(contract));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getBalance(contract));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(contract));
        assertEquals(BigInteger.ONE, blockchain.getRepository().getNonce(deployer));
        assertEquals(
                Builder.DEFAULT_BALANCE.subtract(
                        BigInteger.valueOf(tx.getNrgConsume())
                                .multiply(BigInteger.valueOf(nrgPrice))),
                blockchain.getRepository().getBalance(deployer));
    }

    @Test
    public void testDeployedCodeFunctionality() throws IOException {
        AionAddress contract = deployByteArrayContract();
        byte[] callingCode = Hex.decode(f_func);
        BigInteger nonce = blockchain.getRepository().getNonce(deployer);
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        callingCode,
                        1_000_000,
                        1);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        ExecutionBatch details = new ExecutionBatch(context.block, Collections.singletonList(tx));
        BulkExecutor exec = new BulkExecutor(details, repo, false, true, context.block.getNrgLimit(), LOGGER_VM, getPostExecutionWork());
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        System.out.println(Hex.toHexString(summary.getResult()));

        // We called the function f() which returns nothing.
        assertEquals(0, summary.getReceipt().getTransactionOutput().length);

        byte[] body = ContractUtils.getContractBody("ByteArrayMap.sol", "ByteArrayMap");
        assertArrayEquals(body, blockchain.getRepository().getCode(contract));

        // Now we call the g() function, which returns a byte array of 1024 bytes that starts with
        // 'a' and ends with 'b'
        callingCode = Hex.decode(g_func);
        nonce = repo.getNonce(deployer);
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        callingCode,
                        1_000_000,
                        1);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);

        details = new ExecutionBatch(context.block, Collections.singletonList(tx));
        exec = new BulkExecutor(details, repo, false, true, context.block.getNrgLimit(), LOGGER_VM, getPostExecutionWork());
        summary = exec.execute().get(0);

        System.out.println(Hex.toHexString(summary.getResult()));
        System.out.println(summary.getResult().length);

        //        // I'm guessing: first data word is the number of bytes that follows. Then those
        // following
        //        // bytes denote the size of the output, which follows these last bytes.
        //        int len = new DataWord(Arrays.copyOfRange(output, 0, DataWord.BYTES)).intValue();
        //        byte[] outputLen = new byte[len];
        //        System.arraycopy(output, DataWord.BYTES, outputLen, 0, len);
        //        int outputSize = new BigInteger(outputLen).intValue();
        //
        //        byte[] expected = new byte[1024];
        //        expected[0] = 'a';
        //        expected[1023] = 'b';
        //
        //        byte[] out = new byte[outputSize];
        //        System.arraycopy(output, DataWord.BYTES + len, out, 0, outputSize);

        byte[] expected = new byte[1024];
        expected[0] = 'a';
        expected[1023] = 'b';

        byte[] out = extractActualOutput(summary.getResult());

        assertArrayEquals(expected, out);
    }

    @Test
    public void testGfunction() throws IOException {
        AionAddress contract = deployByteArrayContract();
        byte[] callingCode = Hex.decode(g_func);
        BigInteger nonce = blockchain.getRepository().getNonce(deployer);
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        callingCode,
                        1_000_000,
                        1);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        IRepositoryCache repo = blockchain.getRepository().startTracking();

        ExecutionBatch details = new ExecutionBatch(context.block, Collections.singletonList(tx));
        BulkExecutor exec = new BulkExecutor(details, repo, false, true, context.block.getNrgLimit(), LOGGER_VM, getPostExecutionWork());
        AionTxExecSummary summary = exec.execute().get(0);
        System.out.println(summary.getReceipt());

        //        System.out.println(Hex.toHexString(res.getOutput()));
        //        System.out.println(res.getOutput().length);

        byte[] out = extractActualOutput(summary.getResult());
        assertEquals(0, out.length);
    }

    // <-----------------------------------------HELPERS------------------------------------------->

    private AionAddress deployByteArrayContract() throws IOException {
        AionAddress to = getNewRecipient(true);
        byte[] deployCode = ContractUtils.getContractDeployer("ByteArrayMap.sol", "ByteArrayMap");
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), to, value.toByteArray(), deployCode, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        blockchain.tryToConnect(context.block);

        return tx.getContractAddress();
    }

    private AionAddress getNewRecipient(boolean isContractCreation) {
        return (isContractCreation)
                ? null
                : new AionAddress(RandomUtils.nextBytes(AionAddress.SIZE));
    }

    private byte[] extractActualOutput(byte[] rawOutput) {
        // I'm guessing: first data word is the number of bytes that follows. Then those following
        // bytes denote the size of the output, which follows these last bytes.
        int len = new DataWord(Arrays.copyOfRange(rawOutput, 0, DataWord.BYTES)).intValue();
        byte[] outputLen = new byte[len];
        System.arraycopy(rawOutput, DataWord.BYTES, outputLen, 0, len);
        int outputSize = new BigInteger(outputLen).intValue();

        byte[] out = new byte[outputSize];
        System.arraycopy(rawOutput, DataWord.BYTES + len, out, 0, outputSize);
        return out;
    }

    private PostExecutionWork getPostExecutionWork() {
        return (r, s, t, b) -> {
            return 0L;
        };
    }

}
