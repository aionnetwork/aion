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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.aion.base.type.AionAddress;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.crypto.ECKey;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.vm.types.DataWord;
import org.aion.vm.BlockDetails;
import org.aion.vm.BulkExecutor;
import org.aion.vm.PostExecutionWork;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.vm.api.interfaces.InternalTransactionInterface;
import org.aion.zero.impl.BlockContext;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.StandaloneBlockchain.Builder;
import org.aion.zero.impl.vm.contracts.ContractUtils;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.IAionBlock;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

public class OpcodeIntegTest {
    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;
    private AionAddress deployer;
    private BigInteger deployerBalance;

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
        deployerBalance = Builder.DEFAULT_BALANCE;
    }

    @After
    public void tearDown() {
        blockchain = null;
        deployerKey = null;
        deployer = null;
        deployerBalance = null;
    }

    // ====================== test repo & track flushing over multiple levels ======================

    @Test
    public void testNoRevert() throws IOException {
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        AionAddress D = deployContract(repo, "F", "F.sol", BigInteger.ZERO);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger nonce = BigInteger.ONE;

        byte[] input = ByteUtil.merge(Hex.decode("f854bb89"), new DataWord(6).getData());
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        D,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);

        assertEquals("", summary.getReceipt().getError());
        assertEquals(summary.getNrgUsed().longValue(), summary.getNrgUsed().longValue());

        // Check that the logs from our internal transactions are as we expect.
        List<IExecutionLog> logs = summary.getReceipt().getLogInfoList();
        assertEquals(12, logs.size());
        assertArrayEquals(new DataWord(0).getData(), logs.get(0).getLogData());
        assertArrayEquals(new DataWord(6).getData(), logs.get(1).getLogData());
        assertArrayEquals(new DataWord(5).getData(), logs.get(2).getLogData());
        assertArrayEquals(new DataWord(4).getData(), logs.get(3).getLogData());
        assertArrayEquals(new DataWord(3).getData(), logs.get(4).getLogData());
        assertArrayEquals(new DataWord(2).getData(), logs.get(5).getLogData());
        assertArrayEquals(new DataWord(1).getData(), logs.get(6).getLogData());
        assertArrayEquals(new DataWord(1).getData(), logs.get(7).getLogData());
        assertArrayEquals(new DataWord(1).getData(), logs.get(8).getLogData());
        assertArrayEquals(new DataWord(1).getData(), logs.get(9).getLogData());
        assertArrayEquals(new DataWord(1).getData(), logs.get(10).getLogData());
        assertArrayEquals(new DataWord(1).getData(), logs.get(11).getLogData());
    }

    @Test
    public void testRevertAtBottomLevel() throws IOException {
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        AionAddress D = deployContract(repo, "F", "F.sol", BigInteger.ZERO);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger nonce = BigInteger.ONE;

        byte[] input = ByteUtil.merge(Hex.decode("8256cff3"), new DataWord(5).getData());
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        D,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);

        assertEquals("", summary.getReceipt().getError());
        assertEquals(summary.getNrgUsed().longValue(), summary.getNrgUsed().longValue());

        // Check that the logs from our internal transactions are as we expect.
        List<IExecutionLog> logs = summary.getReceipt().getLogInfoList();
        assertEquals(8, logs.size());
        assertArrayEquals(new DataWord(0).getData(), logs.get(0).getLogData());
        assertArrayEquals(new DataWord(5).getData(), logs.get(1).getLogData());
        assertArrayEquals(new DataWord(4).getData(), logs.get(2).getLogData());
        assertArrayEquals(new DataWord(3).getData(), logs.get(3).getLogData());
        assertArrayEquals(new DataWord(2).getData(), logs.get(4).getLogData());
        assertArrayEquals(new DataWord(2).getData(), logs.get(5).getLogData());
        assertArrayEquals(new DataWord(2).getData(), logs.get(6).getLogData());
        assertArrayEquals(new DataWord(2).getData(), logs.get(7).getLogData());
    }

    @Test
    public void testRevertAtMidLevel() throws IOException {
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        AionAddress D = deployContract(repo, "F", "F.sol", BigInteger.ZERO);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger nonce = BigInteger.ONE;

        byte[] input = ByteUtil.merge(Hex.decode("10462fd0"), new DataWord(7).getData());
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        D,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);

        assertEquals("", summary.getReceipt().getError());
        assertEquals(summary.getNrgUsed().longValue(), summary.getNrgUsed().longValue());

        // Check that the logs from our internal transactions are as we expect.
        List<IExecutionLog> logs = summary.getReceipt().getLogInfoList();
        assertEquals(8, logs.size());
        assertArrayEquals(new DataWord(0).getData(), logs.get(0).getLogData());
        assertArrayEquals(new DataWord(7).getData(), logs.get(1).getLogData());
        assertArrayEquals(new DataWord(6).getData(), logs.get(2).getLogData());
        assertArrayEquals(new DataWord(5).getData(), logs.get(3).getLogData());
        assertArrayEquals(new DataWord(4).getData(), logs.get(4).getLogData());
        assertArrayEquals(new DataWord(4).getData(), logs.get(5).getLogData());
        assertArrayEquals(new DataWord(4).getData(), logs.get(6).getLogData());
        assertArrayEquals(new DataWord(4).getData(), logs.get(7).getLogData());
    }

    // ======================================= test CALLCODE =======================================

    @Test
    public void testCallcodeStorage() throws IOException {
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        BigInteger n = new BigInteger("7638523");
        AionAddress D = deployContract(repo, "D", "D.sol", BigInteger.ZERO);
        AionAddress E = deployContract(repo, "E", "D.sol", BigInteger.ZERO);

        // Deployer calls contract D which performs CALLCODE to call contract E. We expect that the
        // storage in contract D is modified by the code that is called in contract E.
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger nonce = BigInteger.TWO;
        byte[] input = ByteUtil.merge(Hex.decode("5cce9fc2"), E.toBytes()); // use CALLCODE on E.
        input = ByteUtil.merge(input, new DataWord(n).getData()); // pass in 'n' also.

        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        D,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertEquals(deployer, tx.getSenderAddress());
        assertEquals(D, tx.getDestinationAddress());

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(summary.getNrgUsed().longValue(), summary.getNrgUsed().longValue());
        nonce = nonce.add(BigInteger.ONE);

        // When we call into contract D we should find its storage is modified so that 'n' is set.
        input = Hex.decode("3e955225");
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        D,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertEquals(deployer, tx.getSenderAddress());
        assertEquals(D, tx.getDestinationAddress());

        context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        exec = getNewExecutor(tx, context.block, repo);
        BigInteger inStore = new BigInteger(exec.execute().get(0).getResult());
        System.err.println("Found in D's storage for n: " + inStore);
        assertEquals(n, inStore);
        nonce = nonce.add(BigInteger.ONE);

        // When we call into contract E we should find its storage is unmodified.
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        E,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertEquals(deployer, tx.getSenderAddress());
        assertEquals(E, tx.getDestinationAddress());

        context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        exec = getNewExecutor(tx, context.block, repo);
        inStore = new BigInteger(exec.execute().get(0).getResult());
        assertEquals(BigInteger.ZERO, inStore);
    }

    @Test
    public void testCallcodeActors() throws IOException {
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        AionAddress D = deployContract(repo, "D", "D.sol", BigInteger.ZERO);
        AionAddress E = deployContract(repo, "E", "D.sol", BigInteger.ZERO);

        // Deployer calls contract D which performs CALLCODE to call contract E. From the
        // perspective
        // of the internal transaction, however, it looks like D calls D.
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger nonce = BigInteger.TWO;
        byte[] input = ByteUtil.merge(Hex.decode("5cce9fc2"), E.toBytes()); // use CALLCODE on E.
        input = ByteUtil.merge(input, new DataWord(0).getData()); // pass in 'n' also.

        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        D,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertEquals(deployer, tx.getSenderAddress());
        assertEquals(D, tx.getDestinationAddress());

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(summary.getNrgUsed().longValue(), summary.getNrgUsed().longValue());

        // We expect that the internal transaction is sent from D to D.
        List<InternalTransactionInterface> internalTxs = summary.getInternalTransactions();
        assertEquals(1, internalTxs.size());
        assertEquals(D, internalTxs.get(0).getSenderAddress());
        assertEquals(D, internalTxs.get(0).getDestinationAddress());
    }

    @Test
    public void testCallcodeValueTransfer() throws IOException {
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        AionAddress D = deployContract(repo, "D", "D.sol", BigInteger.ZERO);
        AionAddress E = deployContract(repo, "E", "D.sol", BigInteger.ZERO);

        BigInteger balanceDeployer = repo.getBalance(deployer);
        BigInteger balanceD = repo.getBalance(D);
        BigInteger balanceE = repo.getBalance(E);

        // Deployer calls contract D which performs CALLCODE to call contract E.
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = new BigInteger("2387653");
        BigInteger nonce = BigInteger.TWO;
        byte[] input = ByteUtil.merge(Hex.decode("5cce9fc2"), E.toBytes()); // use CALLCODE on E.
        input = ByteUtil.merge(input, new DataWord(0).getData()); // pass in 'n' also.

        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), D, value.toByteArray(), input, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertEquals(deployer, tx.getSenderAddress());
        assertEquals(D, tx.getDestinationAddress());

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(summary.getNrgUsed().longValue(), summary.getNrgUsed().longValue());

        // We expect that deployer paid the txCost and sent value. We expect that D received value.
        // We expect E had no value change.
        BigInteger txCost = BigInteger.valueOf(summary.getNrgUsed().longValue() * nrgPrice);
        assertEquals(balanceDeployer.subtract(value).subtract(txCost), repo.getBalance(deployer));
        assertEquals(balanceD.add(value), repo.getBalance(D));
        assertEquals(balanceE, repo.getBalance(E));
    }

    // ===================================== test DELEGATECALL =====================================

    @Test
    public void testDelegateCallStorage() throws IOException {
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        AionAddress D = deployContract(repo, "D", "D.sol", BigInteger.ZERO);
        AionAddress E = deployContract(repo, "E", "D.sol", BigInteger.ZERO);
        BigInteger n = new BigInteger("23786523");

        // Deployer calls contract D which performs DELEGATECALL to call contract E.
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = new BigInteger("4364463");
        BigInteger nonce = BigInteger.TWO;
        byte[] input =
                ByteUtil.merge(Hex.decode("32817e1d"), E.toBytes()); // use DELEGATECALL on E.
        input = ByteUtil.merge(input, new DataWord(n).getData()); // pass in 'n' also.

        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), D, value.toByteArray(), input, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertEquals(deployer, tx.getSenderAddress());
        assertEquals(D, tx.getDestinationAddress());

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(summary.getNrgUsed().longValue(), summary.getNrgUsed().longValue());
        nonce = nonce.add(BigInteger.ONE);

        // When we call into contract D we should find its storage is modified so that 'n' is set.
        input = Hex.decode("3e955225");
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        D,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertEquals(deployer, tx.getSenderAddress());
        assertEquals(D, tx.getDestinationAddress());

        context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        exec = getNewExecutor(tx, context.block, repo);
        BigInteger inStore = new BigInteger(exec.execute().get(0).getResult());
        assertEquals(n, inStore);
        nonce = nonce.add(BigInteger.ONE);

        // When we call into contract E we should find its storage is unmodified.
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        E,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertEquals(deployer, tx.getSenderAddress());
        assertEquals(E, tx.getDestinationAddress());

        context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        exec = getNewExecutor(tx, context.block, repo);
        inStore = new BigInteger(exec.execute().get(0).getResult());
        assertEquals(BigInteger.ZERO, inStore);
    }

    @Test
    public void testDelegateCallActors() throws IOException {
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        AionAddress D = deployContract(repo, "D", "D.sol", BigInteger.ZERO);
        AionAddress E = deployContract(repo, "E", "D.sol", BigInteger.ZERO);
        BigInteger n = new BigInteger("23786523");

        // Deployer calls contract D which performs DELEGATECALL to call contract E.
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = new BigInteger("4364463");
        BigInteger nonce = BigInteger.TWO;
        byte[] input =
                ByteUtil.merge(Hex.decode("32817e1d"), E.toBytes()); // use DELEGATECALL on E.
        input = ByteUtil.merge(input, new DataWord(n).getData()); // pass in 'n' also.

        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), D, value.toByteArray(), input, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertEquals(deployer, tx.getSenderAddress());
        assertEquals(D, tx.getDestinationAddress());

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(summary.getNrgUsed().longValue(), summary.getNrgUsed().longValue());

        // We expect there to be one internal transaction and it should look like deployer sent to
        // D.
        List<InternalTransactionInterface> internalTxs = summary.getInternalTransactions();
        assertEquals(1, internalTxs.size());
        assertEquals(deployer, internalTxs.get(0).getSenderAddress());
        assertEquals(D, internalTxs.get(0).getDestinationAddress());
    }

    @Test
    public void testDelegateCallValueTransfer() throws IOException {
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        AionAddress D = deployContract(repo, "D", "D.sol", BigInteger.ZERO);
        AionAddress E = deployContract(repo, "E", "D.sol", BigInteger.ZERO);
        BigInteger n = new BigInteger("23786523");

        BigInteger balanceDeployer = repo.getBalance(deployer);
        BigInteger balanceD = repo.getBalance(D);
        BigInteger balanceE = repo.getBalance(E);

        // Deployer calls contract D which performs DELEGATECALL to call contract E.
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = new BigInteger("4364463");
        BigInteger nonce = BigInteger.TWO;
        byte[] input =
                ByteUtil.merge(Hex.decode("32817e1d"), E.toBytes()); // use DELEGATECALL on E.
        input = ByteUtil.merge(input, new DataWord(n).getData()); // pass in 'n' also.

        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), D, value.toByteArray(), input, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertEquals(deployer, tx.getSenderAddress());
        assertEquals(D, tx.getDestinationAddress());

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(summary.getNrgUsed().longValue(), summary.getNrgUsed().longValue());

        // We expect that deployer paid the tx cost and sent value. We expect that D received value.
        // We expect that E received nothing.
        BigInteger txCost = BigInteger.valueOf(summary.getNrgUsed().longValue() * nrgPrice);
        assertEquals(balanceDeployer.subtract(value).subtract(txCost), repo.getBalance(deployer));
        assertEquals(balanceD.add(value), repo.getBalance(D));
        assertEquals(balanceE, repo.getBalance(E));
    }

    // ============================= test CALL, CALLCODE, DELEGATECALL =============================

    @Test
    public void testOpcodesActors() throws IOException {
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        AionAddress callerContract = deployContract(repo, "Caller", "Opcodes.sol", BigInteger.ZERO);
        AionAddress calleeContract = deployContract(repo, "Callee", "Opcodes.sol", BigInteger.ZERO);

        System.err.println("Deployer: " + deployer);
        System.err.println("Caller: " + callerContract);
        System.err.println("Callee: " + calleeContract);

        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger nonce = BigInteger.TWO;
        byte[] input = ByteUtil.merge(Hex.decode("fc68521a"), calleeContract.toBytes());
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        callerContract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(summary.getNrgUsed().longValue(), summary.getNrgUsed().longValue());

        // We examine the logs to determine the expected state. We expect to see
        // owner-caller-origin-data as follows for each opcode:
        //
        // CALL             -->     CALLEE-CALLER-DEPLOYER-ZERO
        // CALLCODE         -->     CALLER-CALLER-DEPLOYER-ZERO
        // DELEGATECALL     -->     CALLER-DEPLOYER-DEPLOYER-ZERO
        List<IExecutionLog> logs = summary.getReceipt().getLogInfoList();
        assertEquals(3, logs.size());
        verifyLogData(logs.get(0).getLogData(), calleeContract, callerContract, deployer);
        verifyLogData(logs.get(1).getLogData(), callerContract, callerContract, deployer);
        verifyLogData(logs.get(2).getLogData(), callerContract, deployer, deployer);
    }

    // ======================================= test SUICIDE ========================================

    @Test
    public void testSuicideRecipientExists() throws IOException {
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        BigInteger balance = new BigInteger("32522224");
        AionAddress recipient = new AionAddress(RandomUtils.nextBytes(AionAddress.SIZE));
        repo.createAccount(recipient);

        AionAddress contract = deployContract(repo, "Suicide", "Suicide.sol", BigInteger.ZERO);
        repo.addBalance(contract, balance);

        BigInteger balanceDeployer = repo.getBalance(deployer);
        BigInteger balanceRecipient = repo.getBalance(recipient);
        assertEquals(balance, repo.getBalance(contract));

        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger nonce = BigInteger.ONE;
        byte[] input = ByteUtil.merge(Hex.decode("fc68521a"), recipient.toBytes());
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(summary.getNrgUsed().longValue(), summary.getNrgUsed().longValue());

        // We expect that deployer paid the tx cost. We expect that all of the balance in the
        // contract has been transferred to recipient. We expect that the contract has been deleted.
        BigInteger txCost = BigInteger.valueOf(summary.getNrgUsed().longValue() * nrgPrice);
        assertEquals(balanceDeployer.subtract(txCost), repo.getBalance(deployer));
        assertEquals(balanceRecipient.add(balance), repo.getBalance(recipient));
        assertEquals(BigInteger.ZERO, repo.getBalance(contract));
        assertFalse(repo.hasAccountState(contract));
        assertEquals(1, summary.getDeletedAccounts().size());
        assertEquals(contract, summary.getDeletedAccounts().get(0));
    }

    @Test
    public void testSuicideRecipientNewlyCreated() throws IOException {
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        BigInteger balance = new BigInteger("32522224");
        AionAddress recipient = new AionAddress(RandomUtils.nextBytes(AionAddress.SIZE));

        AionAddress contract = deployContract(repo, "Suicide", "Suicide.sol", BigInteger.ZERO);
        repo.addBalance(contract, balance);

        BigInteger balanceDeployer = repo.getBalance(deployer);
        BigInteger balanceRecipient = BigInteger.ZERO;
        assertEquals(balance, repo.getBalance(contract));

        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger nonce = BigInteger.ONE;
        byte[] input = ByteUtil.merge(Hex.decode("fc68521a"), recipient.toBytes());
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(summary.getNrgUsed().longValue(), summary.getNrgUsed().longValue());

        // We expect that deployer paid the tx cost. We expect that a new account was created and
        // all of the balance in the contract has been transferred to it. We expect that the
        // contract has been deleted.
        BigInteger txCost = BigInteger.valueOf(summary.getNrgUsed().longValue() * nrgPrice);
        assertEquals(balanceDeployer.subtract(txCost), repo.getBalance(deployer));
        assertTrue(repo.hasAccountState(recipient));
        assertEquals(balanceRecipient.add(balance), repo.getBalance(recipient));
        assertEquals(BigInteger.ZERO, repo.getBalance(contract));
        assertFalse(repo.hasAccountState(contract));
        assertEquals(1, summary.getDeletedAccounts().size());
        assertEquals(contract, summary.getDeletedAccounts().get(0));
    }

    // <-------------------------------------------------------------------------------------------->

    /**
     * Deploys the contract named contractName in the file named contractFilename with value value.
     */
    private AionAddress deployContract(
            IRepositoryCache repo, String contractName, String contractFilename, BigInteger value)
            throws IOException {

        byte[] deployCode = ContractUtils.getContractDeployer(contractFilename, contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger nonce = repo.getNonce(deployer);
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);
        AionAddress contract =
                deployContract(
                        repo, tx, contractName, contractFilename, value, nrg, nrgPrice, nonce);
        deployerBalance = repo.getBalance(deployer);
        return contract;
    }

    /**
     * Deploys a contract named contractName in a file named contractFilename and checks the state
     * of the deployed contract and the contract deployer.
     *
     * <p>Returns the address of the newly deployed contract.
     */
    private AionAddress deployContract(
            IRepositoryCache repo,
            AionTransaction tx,
            String contractName,
            String contractFilename,
            BigInteger value,
            long nrg,
            long nrgPrice,
            BigInteger expectedNonce)
            throws IOException {

        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());
        assertEquals(deployerBalance, repo.getBalance(deployer));
        assertEquals(expectedNonce, repo.getNonce(deployer));

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);

        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        AionAddress contract = tx.getContractAddress();
        checkStateOfNewContract(repo, contractName, contractFilename, contract, value);
        checkStateOfDeployer(
                repo,
                deployerBalance,
                summary.getNrgUsed().longValue(),
                nrgPrice,
                value,
                expectedNonce);
        return contract;
    }

    /**
     * Checks that the newly deployed contract in file contractFilename and named contractName is
     * deployed at address contractAddr with the expected body code and a zero nonce and balance
     * equal to whatever value was transferred to it when deployed.
     */
    private void checkStateOfNewContract(
            IRepositoryCache repo,
            String contractName,
            String contractFilename,
            AionAddress contractAddr,
            BigInteger valueTransferred)
            throws IOException {

        byte[] expectedBodyCode = ContractUtils.getContractBody(contractFilename, contractName);
        assertArrayEquals(expectedBodyCode, repo.getCode(contractAddr));
        assertEquals(valueTransferred, repo.getBalance(contractAddr));
        assertEquals(BigInteger.ZERO, repo.getNonce(contractAddr));
    }

    /**
     * Checks the state of the deployer after a successful contract deployment. In this case we
     * expect the deployer's nonce to have incremented by one and their new balance to be equal to
     * the prior balance minus the tx cost and the value transferred.
     */
    private void checkStateOfDeployer(
            IRepositoryCache repo,
            BigInteger priorBalance,
            long nrgUsed,
            long nrgPrice,
            BigInteger value,
            BigInteger priorNonce) {

        assertEquals(priorNonce.add(BigInteger.ONE), repo.getNonce(deployer));
        BigInteger txCost = BigInteger.valueOf(nrgUsed * nrgPrice);
        assertEquals(priorBalance.subtract(txCost).subtract(value), repo.getBalance(deployer));
    }

    /**
     * Verifies the log data for a call to f() in the Caller contract in the file Opcodes.sol is a
     * byte array consisting of the bytes of owner then caller then origin then finally 16 zero
     * bytes.
     */
    private void verifyLogData(
            byte[] data, AionAddress owner, AionAddress caller, AionAddress origin) {
        assertArrayEquals(Arrays.copyOfRange(data, 0, AionAddress.SIZE), owner.toBytes());
        assertArrayEquals(
                Arrays.copyOfRange(data, AionAddress.SIZE, AionAddress.SIZE * 2), caller.toBytes());
        assertArrayEquals(
                Arrays.copyOfRange(data, AionAddress.SIZE * 2, AionAddress.SIZE * 3),
                origin.toBytes());
        assertArrayEquals(
                Arrays.copyOfRange(data, data.length - DataWord.BYTES, data.length),
                DataWord.ZERO.getData());
    }

    private BulkExecutor getNewExecutor(
            AionTransaction tx, IAionBlock block, IRepositoryCache repo) {
        BlockDetails details = new BlockDetails(block, Collections.singletonList(tx));
        return new BulkExecutor(
                details, repo, false, true, block.getNrgLimit(), LOGGER_VM, getPostExecutionWork());
    }

    private PostExecutionWork getPostExecutionWork() {
        return (k, s, t, b) -> {
            return 0L;
        };
    }
}
