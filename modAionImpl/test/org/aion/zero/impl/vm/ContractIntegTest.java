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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import org.aion.base.type.AionAddress;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.crypto.ECKey;
import org.aion.fastvm.FastVmResultCode;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.vm.Constants;
import org.aion.mcf.vm.types.DataWord;
import org.aion.vm.BlockDetails;
import org.aion.vm.BulkExecutor;
import org.aion.vm.PostExecutionWork;
import org.aion.vm.api.interfaces.ResultCode;
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
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

/**
 * Tests the opcall CREATE for deploying new smart contracts as well as CALL to call a deployed
 * contract.
 */
public class ContractIntegTest {
    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;
    private AionAddress deployer;
    private BigInteger deployerBalance, deployerNonce;

    @Before
    public void setup() {
        StandaloneBlockchain.Bundle bundle =
                (new Builder()).withValidatorConfiguration("simple").withDefaultAccounts().build();
        blockchain = bundle.bc;
        deployerKey = bundle.privateKeys.get(0);
        deployer = new AionAddress(deployerKey.getAddress());
        deployerBalance = Builder.DEFAULT_BALANCE;
        deployerNonce = BigInteger.ZERO;
    }

    @After
    public void tearDown() {
        blockchain = null;
        deployerKey = null;
        deployer = null;
        deployerBalance = null;
        deployerNonce = null;
    }

    @Test
    public void testEmptyContract() throws IOException {
        String contractName = "EmptyContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        IRepositoryCache repo = blockchain.getRepository().startTracking();

        BlockDetails details = new BlockDetails(context.block, Collections.singletonList(tx));
        BulkExecutor exec = new BulkExecutor(details, repo, false, true, context.block.getNrgLimit(), LOGGER_VM, getPostExecutionWork());
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError()); // "" == SUCCESS
        assertEquals(tx.getNrgConsume(), summary.getReceipt().getEnergyUsed());

        AionAddress contract = tx.getContractAddress();
        checkStateOfNewContract(repo, contractName, contract, summary.getResult(), FastVmResultCode.SUCCESS, value);
        nonce = nonce.add(BigInteger.ONE);
        checkStateOfDeployer(repo, summary, nrgPrice, value, nonce);
    }

    @Test
    public void testContractDeployCodeIsEmpty() {
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), new byte[0], nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError()); // "" == SUCCESS
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());

        AionAddress contract = tx.getContractAddress();
        assertArrayEquals(new byte[0], summary.getResult());
        assertArrayEquals(new byte[0], repo.getCode(contract));
        assertEquals(BigInteger.ZERO, repo.getBalance(contract));
        assertEquals(BigInteger.ZERO, repo.getNonce(contract));

        assertEquals(BigInteger.ONE, repo.getNonce(deployer));
        BigInteger txCost = summary.getNrgUsed().multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(Builder.DEFAULT_BALANCE.subtract(txCost), repo.getBalance(deployer));
    }

    @Test
    public void testContractDeployCodeIsNonsensical() {
        byte[] deployCode = new byte[1];
        deployCode[0] = 0x1;
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("OUT_OF_NRG", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertEquals(nrg, tx.getNrgConsume());

        AionAddress contract = tx.getContractAddress();
        assertArrayEquals(new byte[0], summary.getResult());
        assertArrayEquals(new byte[0], repo.getCode(contract));
        assertEquals(BigInteger.ZERO, repo.getBalance(contract));
        assertEquals(BigInteger.ZERO, repo.getNonce(contract));

        assertEquals(BigInteger.ONE, repo.getNonce(deployer));
        BigInteger txCost = summary.getNrgUsed().multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(Builder.DEFAULT_BALANCE.subtract(txCost), repo.getBalance(deployer));
    }

    @Test
    public void testTransferValueToNonPayableConstructor() throws IOException {
        String contractName = "EmptyContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ONE; // attempt to transfer value to new contract.
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("REVERT", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume()); // all energy is not used up.

        AionAddress contract = tx.getContractAddress();
        checkStateOfNewContract(repo, contractName, contract, summary.getResult(), FastVmResultCode.REVERT, BigInteger.ZERO);
        nonce = nonce.add(BigInteger.ONE);
        checkStateOfDeployer(repo, summary, nrgPrice, BigInteger.ZERO, nonce);
    }

    @Test
    public void testTransferValueToPayableConstructor() throws IOException {
        String contractName = "PayableConstructor";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.TWO.pow(10); // attempt to transfer value to new contract.
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume()); // all energy is not used up.

        AionAddress contract = tx.getContractAddress();
        checkStateOfNewContract(repo, contractName, contract, summary.getResult(), FastVmResultCode.SUCCESS, value);
        nonce = nonce.add(BigInteger.ONE);
        checkStateOfDeployer(repo, summary, nrgPrice, value, nonce);
    }

    @Test
    public void testTransferValueToPayableConstructorInsufficientFunds() throws IOException {
        String contractName = "PayableConstructor";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = Builder.DEFAULT_BALANCE.add(BigInteger.ONE); // send too much value.
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("INSUFFICIENT_BALANCE", summary.getReceipt().getError());
        assertEquals(0, tx.getNrgConsume());
//        assertEquals(0, summary.getNrgUsed().longValue());    //FIXME: confirm above

        AionAddress contract = tx.getContractAddress();
        checkStateOfNewContract(repo, contractName, contract, summary.getResult(), FastVmResultCode.INSUFFICIENT_BALANCE, BigInteger.ZERO);
        checkStateOfDeployerOnBadDeploy(repo);
    }

    @Test
    public void testConstructorIsCalledOnCodeDeployment() throws IOException {
        String contractName = "MultiFeatureContract";
        byte[] deployCode =
                ContractUtils.getContractDeployer(
                        "MultiFeatureContract.sol", "MultiFeatureContract");
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ONE;
        BigInteger nonce = BigInteger.ZERO;

        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);

        IRepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        // Now call the contract and check that the constructor message was set.
        String getMsgFunctionHash = "ce6d41de";
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        Hex.decode(getMsgFunctionHash),
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        String expectedMsg = "Im alive!";
        assertEquals(expectedMsg, new String(extractOutput(summary.getResult())));
    }

    @Test
    public void testCallFunction() throws IOException {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ONE;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        //             ---------- This command will perform addition. ----------
        int num = 53475374;
        byte[] input = ByteUtil.merge(Hex.decode("f601704f"), new DataWord(num).getData());
        input = ByteUtil.merge(input, new DataWord(1).getData());
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        // Since input takes in uint8 we only want the last byte of num. Output size is well-defined
        // at 128 bits, or 16 bytes.
        int expectedResult = 1111 + (num & 0xFF);
        assertEquals(expectedResult, new DataWord(summary.getResult()).intValue());

        //             --------- This command will perform subtraction. ----------
        input = ByteUtil.merge(Hex.decode("f601704f"), new DataWord(num).getData());
        input = ByteUtil.merge(input, new DataWord(0).getData());
        nonce = nonce.add(BigInteger.ONE);
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        exec = getNewExecutor(tx, context.block, repo);
        summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        // Since input takes in uint8 we only want the last byte of num. Output size is well-defined
        // at 128 bits, or 16 bytes.
        expectedResult = 1111 - (num & 0xFF);
        assertEquals(expectedResult, new DataWord(summary.getResult()).intValue());
    }

    @Test
    public void testOverWithdrawFromContract() throws IOException {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        BigInteger deployerBalance = repo.getBalance(deployer);
        repo.flush();
        repo = blockchain.getRepository().startTracking();

        // Contract has no funds, try to withdraw just 1 coin.
        byte[] input = ByteUtil.merge(Hex.decode("9424bba3"), new DataWord(1).getData());
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("REVERT", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        System.out.println("DEP: " + deployerBalance);

        BigInteger txCost =
                BigInteger.valueOf(tx.getNrgConsume()).multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));
    }

    @Test
    public void testWithdrawFromContract() throws IOException {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.TWO.pow(32);
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        BigInteger deployerBalance = repo.getBalance(deployer);

        // Contract has 2^32 coins, let's withdraw them.
        byte[] input = ByteUtil.merge(Hex.decode("9424bba3"), new DataWord(value).getData());
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        // Check that the deployer did get the requested value sent back.
        BigInteger txCost =
                BigInteger.valueOf(tx.getNrgConsume()).multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost).add(value), repo.getBalance(deployer));
    }

    @Test
    public void testSendContractFundsToOtherAddress() throws IOException {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.TWO.pow(13);
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        BigInteger deployerBalance = repo.getBalance(deployer);

        // Create a new account to be our fund recipient.
        AionAddress recipient = new AionAddress(RandomUtils.nextBytes(AionAddress.SIZE));
        repo.createAccount(recipient);

        // Contract has 2^13 coins, let's withdraw them.
        byte[] input = ByteUtil.merge(Hex.decode("8c50612c"), recipient.toBytes());
        input = ByteUtil.merge(input, new DataWord(value).getData());
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        BigInteger txCost =
                BigInteger.valueOf(tx.getNrgConsume()).multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));

        // Check that the recipient received the value.
        assertEquals(value, repo.getBalance(recipient));
    }

    @Test
    public void testSendContractFundsToNonexistentAddress() throws IOException {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.TWO.pow(13);
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        BigInteger deployerBalance = repo.getBalance(deployer);

        // Create a new account to be our fund recipient.
        AionAddress recipient = new AionAddress(RandomUtils.nextBytes(AionAddress.SIZE));

        // Contract has 2^13 coins, let's withdraw them.
        byte[] input = ByteUtil.merge(Hex.decode("8c50612c"), recipient.toBytes());
        input = ByteUtil.merge(input, new DataWord(value).getData());
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        BigInteger txCost =
                BigInteger.valueOf(tx.getNrgConsume()).multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));

        // Check that the recipient received the value.
        assertEquals(value, repo.getBalance(recipient));
    }

    @Test
    public void testCallContractViaAnotherContract() throws IOException {
        // Deploy the MultiFeatureContract.
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.TWO.pow(20);
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress multiFeatureContract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        deployerBalance = repo.getBalance(deployer);
        deployerNonce = repo.getNonce(deployer);

        // Deploy the MultiFeatureCaller contract.
        contractName = "MultiFeatureCaller";
        deployCode = getDeployCode(contractName);
        value = BigInteger.ZERO;
        tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);
        nonce = nonce.add(BigInteger.ONE);
        AionAddress callerContract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);
        AionAddress recipient = new AionAddress(RandomUtils.nextBytes(AionAddress.SIZE));
        deployerBalance = repo.getBalance(deployer);
        deployerNonce = repo.getNonce(deployer);

        // Set the MultiFeatureCaller to call the deployed MultiFeatureContract.
        byte[] input = ByteUtil.merge(Hex.decode("8c30ffe6"), multiFeatureContract.toBytes());
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        callerContract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        BigInteger txCost =
                BigInteger.valueOf(tx.getNrgConsume()).multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));
        deployerBalance = repo.getBalance(deployer);
        deployerNonce = repo.getNonce(deployer);

        // Now use the MultiFeatureCaller to call the MultiFeatureContract to send funds from that
        // contract to the recipient address.
        assertEquals(BigInteger.ZERO, repo.getBalance(recipient));

        value = BigInteger.TWO.pow(20);
        input = ByteUtil.merge(Hex.decode("57a60e6b"), recipient.toBytes());
        input = ByteUtil.merge(input, new DataWord(value).getData());
        nonce = nonce.add(BigInteger.ONE);
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        callerContract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        exec = getNewExecutor(tx, context.block, repo);
        summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        txCost = BigInteger.valueOf(tx.getNrgConsume()).multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));
        assertEquals(value, repo.getBalance(recipient));
    }

    @Test
    public void testRecursiveStackoverflow() throws IOException {
        String contractName = "Recursive";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = Constants.NRG_TRANSACTION_MAX;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        deployerBalance = repo.getBalance(deployer);
        deployerNonce = repo.getNonce(deployer);

        // First recurse 1 time less than the max and verify this is ok.
        int numRecurses = Constants.MAX_CALL_DEPTH - 1;
        byte[] input = ByteUtil.merge(Hex.decode("2d7df21a"), contract.toBytes());
        input = ByteUtil.merge(input, new DataWord(numRecurses + 1).getData());
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        BigInteger txCost =
                BigInteger.valueOf(tx.getNrgConsume()).multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));

        deployerBalance = repo.getBalance(deployer);
        deployerNonce = repo.getNonce(deployer);
        repo.flush();
        repo = blockchain.getRepository().startTracking();

        // Now recurse the max amount of times and ensure we fail.
        numRecurses = Constants.MAX_CALL_DEPTH;
        input = ByteUtil.merge(Hex.decode("2d7df21a"), contract.toBytes());
        input = ByteUtil.merge(input, new DataWord(numRecurses + 1).getData());
        nonce = nonce.add(BigInteger.ONE);
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        exec = getNewExecutor(tx, context.block, repo);
        summary = exec.execute().get(0);
        assertEquals("REVERT", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        txCost = BigInteger.valueOf(tx.getNrgConsume()).multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));
    }

    //TODO: find a better way of testing a mocked up Precompiled contract..
    @Test
    @Ignore
    public void testCallPrecompiledContract() {
//        String tagToSend = "Soo cool!";
//        long nrg = Constants.NRG_TRANSACTION_MAX;
//        long nrgPrice = 1;
//        BigInteger value = BigInteger.ZERO;
//        BigInteger nonce = BigInteger.ZERO;
//        AionTransaction tx =
//                new AionTransaction(
//                        nonce.toByteArray(),
//                        AionAddress.wrap(ContractFactoryMock.CALL_ME),
//                        value.toByteArray(),
//                        tagToSend.getBytes(),
//                        nrg,
//                        nrgPrice);
//        IRepositoryCache repo = blockchain.getRepository().startTracking();
//
//        tx.sign(deployerKey);
//        assertFalse(tx.isContractCreationTransaction());
//
//        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
//        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));
//
//        assertFalse(CallMePrecompiledContract.youCalledMe);
//        BlockContext context =
//                blockchain.createNewBlockContext(
//                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
//
//        TransactionExecutor exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
////        ExecutorProvider provider = new TestVMProvider();
////        ((TestVMProvider) provider).setFactory(new ContractFactoryMock());
//        exec.execute();
//        FastVmTransactionResult result = (FastVmTransactionResult) exec.getResult();
//        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
//        assertEquals(nrg - tx.getNrgConsume(), result.getEnergyRemaining());
//        assertNotEquals(nrg, tx.getNrgConsume());
//
//        // Check that we actually did call the contract and received its output.
//        String expectedMsg = CallMePrecompiledContract.head + tagToSend;
//        byte[] output = result.getOutput();
//        assertArrayEquals(expectedMsg.getBytes(), output);
//        assertTrue(CallMePrecompiledContract.youCalledMe);
    }

    @Test
    public void testRedeployContractAtExistentContractAddress() throws IOException {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);

        // Mock up the repo so that the contract address already exists.
        IRepositoryCache repo = mock(IRepositoryCache.class);
        when(repo.hasAccountState(Mockito.any(AionAddress.class))).thenReturn(true);
        when(repo.getNonce(Mockito.any(AionAddress.class))).thenReturn(nonce);
        when(repo.getBalance(Mockito.any(AionAddress.class))).thenReturn(Builder.DEFAULT_BALANCE);
        when(repo.startTracking()).thenReturn(repo);

        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);

        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("FAILURE", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertEquals(nrg, tx.getNrgConsume());
    }

    @Test
    @Ignore
    public void testCallPrecompiledViaSmartContract() throws IOException {
//        // First deploy the contract we will use to call the precompiled contract.
//        String contractName = "MultiFeatureCaller";
//        byte[] deployCode = getDeployCode(contractName);
//        long nrg = 1_000_000;
//        long nrgPrice = 1;
//        BigInteger value = BigInteger.ZERO;
//        BigInteger nonce = BigInteger.ZERO;
//        AionTransaction tx =
//                new AionTransaction(
//                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg, nrgPrice);
//        IRepositoryCache repo = blockchain.getRepository().startTracking();
//        nonce = nonce.add(BigInteger.ONE);
//        AionAddress contract =
//                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);
//
//        deployerBalance = repo.getBalance(deployer);
//        deployerNonce = repo.getNonce(deployer);
//        byte[] body = getBodyCode("MultiFeatureCaller");
//        assertArrayEquals(body, repo.getCode(contract));
//
//        // Now call the deployed smart contract to call the precompiled contract.
//        assertFalse(CallMePrecompiledContract.youCalledMe);
//        String msg = "I called the contract!";
//        byte[] input =
//                ByteUtil.merge(
//                        Hex.decode("783efb98"),
//                        AionAddress.wrap(ContractFactoryMock.CALL_ME).toBytes());
//        input = ByteUtil.merge(input, msg.getBytes());
//
//        tx =
//                new AionTransaction(
//                        nonce.toByteArray(),
//                        contract,
//                        BigInteger.ZERO.toByteArray(),
//                        input,
//                        nrg,
//                        nrgPrice);
//        tx.sign(deployerKey);
//        assertFalse(tx.isContractCreationTransaction());
//
//        BlockContext context =
//                blockchain.createNewBlockContext(
//                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
//
//        TransactionExecutor exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
////        ExecutorProvider provider = new TestVMProvider();
////        ((TestVMProvider) provider).setFactory(new ContractFactoryMock());
//        exec.execute();
//        FastVmTransactionResult result = (FastVmTransactionResult) exec.getResult();
//
//        assertEquals(FastVmResultCode.SUCCESS, result.getResultCode());
//        assertEquals(nrg - tx.getNrgConsume(), result.getEnergyRemaining());
//        assertNotEquals(nrg, tx.getNrgConsume());
//
//        BigInteger txCost =
//                BigInteger.valueOf(tx.getNrgConsume()).multiply(BigInteger.valueOf(nrgPrice));
//        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));
//        assertTrue(CallMePrecompiledContract.youCalledMe);
    }

    // <------------------------------------------HELPERS------------------------------------------->

    /**
     * Deploys a contract named contractName and checks the state of the deployed contract and the
     * contract deployer and returns the address of the contract once finished.
     */
    private AionAddress deployContract(
            IRepositoryCache repo,
            AionTransaction tx,
            String contractName,
            String contractFilename,
            BigInteger value,
            long nrg,
            long nrgPrice,
            BigInteger nonce)
            throws IOException {

        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(deployerBalance, repo.getBalance(deployer));
        assertEquals(deployerNonce, repo.getNonce(deployer));

        BlockContext context =
                blockchain.createNewBlockContext(
                        blockchain.getBestBlock(), Collections.singletonList(tx), false);

        BulkExecutor exec = getNewExecutor(tx, context.block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        AionAddress contract = tx.getContractAddress();
        if (contractFilename == null) {
            checkStateOfNewContract(repo, contractName, contract, summary.getResult(), FastVmResultCode.SUCCESS, value);
        } else {
            checkStateOfNewContract(repo, contractName, contractFilename, contract, summary.getResult(), FastVmResultCode.SUCCESS, value);
        }
        checkStateOfDeployer(repo, summary, nrgPrice, value, nonce);
        return contract;
    }

    /**
     * Returns the deployment code to create the contract whose name is contractName and whose file
     * name is contractName.sol.
     */
    private byte[] getDeployCode(String contractName) throws IOException {
        String fileName = contractName + ".sol";
        return ContractUtils.getContractDeployer(fileName, contractName);
    }

    /**
     * Returns the code body of the contract whose name is contractName and whose file name is
     * contractName.sol.
     */
    private byte[] getBodyCode(String contractName) throws IOException {
        String filename = contractName + ".sol";
        return ContractUtils.getContractBody(filename, contractName);
    }

    /**
     * Checks that the newly deployed contract at address contractAddr is in the expected state
     * after the contract whose name is contractName is deployed to it.
     */
    private void checkStateOfNewContract(
            IRepositoryCache repo,
            String contractName,
            AionAddress contractAddr,
            byte[] output,
            ResultCode result,
            BigInteger value)
            throws IOException {

        byte[] body = getBodyCode(contractName);
        if (result.isSuccess()) {
            assertArrayEquals(body, output);
            assertArrayEquals(body, repo.getCode(contractAddr));
        } else {
            assertArrayEquals(new byte[0], output);
            assertArrayEquals(new byte[0], repo.getCode(contractAddr));
        }
        assertEquals(value, repo.getBalance(contractAddr));
        assertEquals(BigInteger.ZERO, repo.getNonce(contractAddr));
    }

    /**
     * Checks that the newly deployed contract at address contractAddr is in the expected state
     * after the contract whose name is contractName (inside the file named contractFilename) is
     * deployed to it.
     */
    private void checkStateOfNewContract(
            IRepositoryCache repo,
            String contractName,
            String contractFilename,
            AionAddress contractAddr,
            byte[] output,
            FastVmResultCode result,
            BigInteger value)
            throws IOException {

        byte[] body = ContractUtils.getContractBody(contractFilename, contractName);
        if (result.isSuccess()) {
            assertArrayEquals(body, output);
            assertArrayEquals(body, repo.getCode(contractAddr));
        } else {
            assertArrayEquals(new byte[0], output);
            assertArrayEquals(new byte[0], repo.getCode(contractAddr));
        }
        assertEquals(value, repo.getBalance(contractAddr));
        assertEquals(BigInteger.ZERO, repo.getNonce(contractAddr));
    }

    /**
     * Checks the state of the deployer after a successful contract deployment. In this case we
     * expect the deployer's nonce to have incremented to one and their new balance to be equal to:
     * D - UP - V
     *
     * <p>D is default starting amount U is energy used P is energy price V is value transferred
     */
    private void checkStateOfDeployer(
            IRepositoryCache repo,
            AionTxExecSummary summary,
            long nrgPrice,
            BigInteger value,
            BigInteger nonce) {

        assertEquals(nonce, repo.getNonce(deployer));
        BigInteger txCost = summary.getNrgUsed().multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost).subtract(value), repo.getBalance(deployer));
    }

    /**
     * Checks the state of the deployer after a failed attempt to deploy a contract. In this case we
     * expect the deployer's nonce to still be zero and their balance still default and unchanged.
     */
    private void checkStateOfDeployerOnBadDeploy(IRepositoryCache repo) {

        assertEquals(BigInteger.ZERO, repo.getNonce(deployer));
        assertEquals(Builder.DEFAULT_BALANCE, repo.getBalance(deployer));
    }

    /**
     * Extracts output from rawOutput under the assumption that rawOutput is the result output of a
     * call to the fastVM and this output is of variable length not predefined length.
     */
    private byte[] extractOutput(byte[] rawOutput) {
        int headerLen = new DataWord(Arrays.copyOfRange(rawOutput, 0, DataWord.BYTES)).intValue();
        int outputLen =
                new DataWord(
                                Arrays.copyOfRange(
                                        rawOutput,
                                        (DataWord.BYTES * 2) - headerLen,
                                        DataWord.BYTES * 2))
                        .intValue();
        byte[] output = new byte[outputLen];
        System.arraycopy(rawOutput, DataWord.BYTES * 2, output, 0, outputLen);
        return output;
    }

    private BulkExecutor getNewExecutor(AionTransaction tx, IAionBlock block, IRepositoryCache repo) {
        BlockDetails details = new BlockDetails(block, Collections.singletonList(tx));
        return new BulkExecutor(details, repo, false, true, block.getNrgLimit(), LOGGER_VM, getPostExecutionWork());
    }

    private PostExecutionWork getPostExecutionWork() {
        return (k, s, t, b) -> {
            return 0L;
        };
    }
}
