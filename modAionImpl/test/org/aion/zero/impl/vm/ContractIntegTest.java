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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.userlib.abi.ABIEncoder;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.ECKey;
import org.aion.crypto.HashUtil;
import org.aion.fastvm.FastVmResultCode;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.tx.TransactionTypes;
import org.aion.mcf.valid.TransactionTypeRule;
import org.aion.mcf.vm.Constants;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.types.Address;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.vm.BulkExecutor;
import org.aion.vm.ExecutionBatch;
import org.aion.vm.PostExecutionWork;
import org.aion.vm.VirtualMachineProvider;
import org.aion.vm.api.interfaces.ResultCode;
import org.aion.vm.exception.VMException;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.StandaloneBlockchain.Builder;
import org.aion.zero.impl.db.AionRepositoryCache;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.contracts.AvmHelloWorld;
import org.aion.zero.impl.vm.contracts.ContractUtils;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;
import org.slf4j.Logger;

/**
 * Tests the opcall CREATE for deploying new smart contracts as well as CALL to call a deployed
 * contract.
 */
@RunWith(Parameterized.class)
public class ContractIntegTest {
    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey, senderKey;
    private Address deployer, sender;
    private BigInteger deployerBalance, deployerNonce, senderBalance, senderNonce;
    private byte txType;

    @Parameters
    public static Object[] data() {
        return new Object[] {TransactionTypes.DEFAULT, TransactionTypes.AVM_CREATE_CODE};
    }

    public ContractIntegTest(byte _txType) {
        txType = _txType;
    }

    @Before
    public void setup() {
        StandaloneBlockchain.Bundle bundle =
                (new Builder()).withValidatorConfiguration("simple").withDefaultAccounts().build();
        TransactionTypeRule.allowAVMContractTransaction();
        blockchain = bundle.bc;
        deployerKey = bundle.privateKeys.get(0);
        deployer = new Address(deployerKey.getAddress());
        deployerBalance = Builder.DEFAULT_BALANCE;
        deployerNonce = BigInteger.ZERO;

        senderKey = bundle.privateKeys.get(1);
        sender = new Address(senderKey.getAddress());
        senderBalance = Builder.DEFAULT_BALANCE;
        senderNonce = BigInteger.ZERO;

        if (VirtualMachineProvider.isMachinesAreLive()) {
            return;
        }
        VirtualMachineProvider.initializeAllVirtualMachines();
    }

    @After
    public void tearDown() {
        blockchain = null;
        deployerKey = null;
        deployer = null;
        deployerBalance = null;
        deployerNonce = null;
        sender = null;
        senderBalance = null;
        senderKey = null;
        senderNonce = null;

        if (VirtualMachineProvider.isMachinesAreLive()) {
            VirtualMachineProvider.shutdownAllVirtualMachines();
        }
    }

    @Test
    public void testFvmEmptyContract() throws IOException, VMException {
        String contractName = "EmptyContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        AionBlock block = makeBlock(tx);
        RepositoryCache repo = blockchain.getRepository().startTracking();

        ExecutionBatch details = new ExecutionBatch(block, Collections.singletonList(tx));
        BulkExecutor exec =
                new BulkExecutor(
                        details,
                        repo,
                        false,
                        true,
                        block.getNrgLimit(),
                        LOGGER_VM,
                        getPostExecutionWork());
        AionTxExecSummary summary = exec.execute().get(0);
        if (txType == TransactionTypes.DEFAULT) {
            assertEquals("", summary.getReceipt().getError()); // "" == SUCCESS
            Address contract = tx.getContractAddress();
            checkStateOfNewContract(
                    repo,
                    contractName,
                    contract,
                    summary.getResult(),
                    FastVmResultCode.SUCCESS,
                    value);
            nonce = nonce.add(BigInteger.ONE);
            checkStateOfDeployer(repo, summary, nrgPrice, value, nonce);

        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertEquals("FAILED_INVALID_DATA", summary.getReceipt().getError());
            nonce = nonce.add(BigInteger.ONE);
            checkStateOfDeployer(repo, summary, nrgPrice, value, nonce);
        }
        assertEquals(tx.getNrgConsume(), summary.getReceipt().getEnergyUsed());
    }

    @Test
    public void testContractDeployCodeIsEmpty() throws VMException {
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        new byte[0],
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        AionBlock block = makeBlock(tx);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        BulkExecutor exec = getNewExecutor(tx, block, repo);
        AionTxExecSummary summary = exec.execute().get(0);

        if (txType == TransactionTypes.DEFAULT) {
            assertEquals("", summary.getReceipt().getError()); // "" == SUCCESS
            assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());

            Address contract = tx.getContractAddress();
            assertArrayEquals(new byte[0], summary.getResult());
            assertArrayEquals(new byte[0], repo.getCode(contract));
            assertEquals(BigInteger.ZERO, repo.getBalance(contract));
            assertEquals(BigInteger.ZERO, repo.getNonce(contract));

            assertEquals(BigInteger.ONE, repo.getNonce(deployer));

        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertEquals("FAILED_INVALID_DATA", summary.getReceipt().getError());
            nonce = nonce.add(BigInteger.ONE);
            checkStateOfDeployer(repo, summary, nrgPrice, value, nonce);
        }

        BigInteger txCost = summary.getNrgUsed().multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(Builder.DEFAULT_BALANCE.subtract(txCost), repo.getBalance(deployer));
    }

    @Test
    public void testContractDeployCodeIsNonsensical() throws VMException {
        byte[] deployCode = new byte[1];
        deployCode[0] = 0x1;
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        AionBlock block = makeBlock(tx);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        BulkExecutor exec = getNewExecutor(tx, block, repo);
        AionTxExecSummary summary = exec.execute().get(0);

        if (txType == TransactionTypes.DEFAULT) {
            assertEquals("OUT_OF_NRG", summary.getReceipt().getError());
            assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
            assertEquals(nrg, tx.getNrgConsume());

            Address contract = tx.getContractAddress();
            assertArrayEquals(new byte[0], summary.getResult());
            assertArrayEquals(new byte[0], repo.getCode(contract));
            assertEquals(BigInteger.ZERO, repo.getBalance(contract));
            assertEquals(BigInteger.ZERO, repo.getNonce(contract));

            assertEquals(BigInteger.ONE, repo.getNonce(deployer));

        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertEquals("FAILED_INVALID_DATA", summary.getReceipt().getError());
            nonce = nonce.add(BigInteger.ONE);
            checkStateOfDeployer(repo, summary, nrgPrice, value, nonce);
        }

        BigInteger txCost = summary.getNrgUsed().multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(Builder.DEFAULT_BALANCE.subtract(txCost), repo.getBalance(deployer));
    }

    @Test
    public void testDeployWithOutCode() throws VMException {

        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO; // attempt to transfer value to new contract.
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        new byte[0],
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        if (txType == TransactionTypes.DEFAULT) {
            AionBlock block = makeBlock(tx);
            RepositoryCache repo = blockchain.getRepository().startTracking();
            BulkExecutor exec = getNewExecutor(tx, block, repo);
            AionTxExecSummary summary = exec.execute().get(0);

            assertEquals("", summary.getReceipt().getError());
            assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
            assertNotEquals(nrg, tx.getNrgConsume()); // all energy is not used up.

            Address contract = tx.getContractAddress();

            checkStateOfDeployer(
                    repo, summary, nrgPrice, BigInteger.ZERO, nonce.add(BigInteger.ONE));
            byte[] code = repo.getCode(contract);
            assertNotNull(code);
        } else {
            blockchain.set040ForkNumber(0);
            AionBlock block = makeBlock(tx);
            RepositoryCache repo = blockchain.getRepository().startTracking();
            BulkExecutor exec = getNewExecutor(tx, block, repo);
            AionTxExecSummary summary = exec.execute().get(0);

            assertEquals("FAILED_INVALID_DATA", summary.getReceipt().getError());
        }
    }

    @Test
    public void testFvmTransferValueToNonPayableConstructor() throws IOException, VMException {
        String contractName = "EmptyContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ONE; // attempt to transfer value to new contract.
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        AionBlock block = makeBlock(tx);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        BulkExecutor exec = getNewExecutor(tx, block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        if (txType == TransactionTypes.DEFAULT) {

            assertEquals("REVERT", summary.getReceipt().getError());
            assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
            assertNotEquals(nrg, tx.getNrgConsume()); // all energy is not used up.

            Address contract = tx.getContractAddress();
            checkStateOfNewContract(
                    repo,
                    contractName,
                    contract,
                    summary.getResult(),
                    FastVmResultCode.REVERT,
                    BigInteger.ZERO);
            nonce = nonce.add(BigInteger.ONE);
        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertEquals("FAILED_INVALID_DATA", summary.getReceipt().getError());
            nonce = nonce.add(BigInteger.ONE);
        }

        checkStateOfDeployer(repo, summary, nrgPrice, BigInteger.ZERO, nonce);
    }

    @Test
    public void testTransferValueToPayableConstructor() throws IOException, VMException {
        String contractName = "PayableConstructor";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.TWO.pow(10); // attempt to transfer value to new contract.
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        AionBlock block = makeBlock(tx);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        BulkExecutor exec = getNewExecutor(tx, block, repo);
        AionTxExecSummary summary = exec.execute().get(0);

        if (txType == TransactionTypes.DEFAULT) {
            assertEquals("", summary.getReceipt().getError());
            assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
            assertNotEquals(nrg, tx.getNrgConsume()); // all energy is not used up.

            Address contract = tx.getContractAddress();
            checkStateOfNewContract(
                    repo,
                    contractName,
                    contract,
                    summary.getResult(),
                    FastVmResultCode.SUCCESS,
                    value);
            nonce = nonce.add(BigInteger.ONE);
            checkStateOfDeployer(repo, summary, nrgPrice, value, nonce);

        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertEquals("FAILED_INVALID_DATA", summary.getReceipt().getError());
            nonce = nonce.add(BigInteger.ONE);
            checkStateOfDeployer(repo, summary, nrgPrice, BigInteger.ZERO, nonce);
        }
    }

    @Test
    public void testTransferValueToPayableConstructorInsufficientFunds()
            throws IOException, VMException {
        String contractName = "PayableConstructor";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = Builder.DEFAULT_BALANCE.add(BigInteger.ONE); // send too much value.
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        AionBlock block = makeBlock(tx);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        BulkExecutor exec = getNewExecutor(tx, block, repo);
        AionTxExecSummary summary = exec.execute().get(0);

        if (txType == TransactionTypes.DEFAULT) {
            assertEquals("INSUFFICIENT_BALANCE", summary.getReceipt().getError());
            assertEquals(0, tx.getNrgConsume());

            Address contract = tx.getContractAddress();
            checkStateOfNewContract(
                    repo,
                    contractName,
                    contract,
                    summary.getResult(),
                    FastVmResultCode.INSUFFICIENT_BALANCE,
                    BigInteger.ZERO);
            checkStateOfDeployerOnBadDeploy(repo);

        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertEquals("REJECTED_INSUFFICIENT_BALANCE", summary.getReceipt().getError());
            checkStateOfDeployerOnBadDeploy(repo);
        }
    }

    @Test
    public void testFvmConstructorIsCalledOnCodeDeployment() throws IOException, VMException {
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
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);

        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        Address contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        if (txType == TransactionTypes.DEFAULT) {
            // Now call the contract and check that the constructor message was set.
            String getMsgFunctionHash = "ce6d41de";
            tx =
                    new AionTransaction(
                            nonce.toByteArray(),
                            contract,
                            BigInteger.ZERO.toByteArray(),
                            Hex.decode(getMsgFunctionHash),
                            nrg,
                            nrgPrice,
                            txType);
            tx.sign(deployerKey);
            assertFalse(tx.isContractCreationTransaction());

            AionBlock block = makeBlock(tx);
            BulkExecutor exec = getNewExecutor(tx, block, repo);
            AionTxExecSummary summary = exec.execute().get(0);
            assertEquals("", summary.getReceipt().getError());
            assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
            assertNotEquals(nrg, tx.getNrgConsume());

            String expectedMsg = "Im alive!";
            assertEquals(expectedMsg, new String(extractOutput(summary.getResult())));

        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertNull(contract);
        }
    }

    @Test
    public void testFvmCallFunction() throws IOException, VMException {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ONE;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        Address contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertNull(contract);
            return;
        }

        //             ---------- This command will perform addition. ----------
        int num = 53475374;
        byte[] input = ByteUtil.merge(Hex.decode("f601704f"), new DataWordImpl(num).getData());
        input = ByteUtil.merge(input, new DataWordImpl(1).getData());
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        BulkExecutor exec = getNewExecutor(tx, block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        // Since input takes in uint8 we only want the last byte of num. Output size is well-defined
        // at 128 bits, or 16 bytes.
        int expectedResult = 1111 + (num & 0xFF);
        assertEquals(expectedResult, new DataWordImpl(summary.getResult()).intValue());

        //             --------- This command will perform subtraction. ----------
        input = ByteUtil.merge(Hex.decode("f601704f"), new DataWordImpl(num).getData());
        input = ByteUtil.merge(input, new DataWordImpl(0).getData());
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

        block = makeBlock(tx);
        exec = getNewExecutor(tx, block, repo);
        summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        // Since input takes in uint8 we only want the last byte of num. Output size is well-defined
        // at 128 bits, or 16 bytes.
        expectedResult = 1111 - (num & 0xFF);
        assertEquals(expectedResult, new DataWordImpl(summary.getResult()).intValue());
    }

    @Test
    public void testFvmOverWithdrawFromContract() throws IOException, VMException {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        Address contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertNull(contract);
            return;
        }

        BigInteger deployerBalance = repo.getBalance(deployer);
        repo.flush();
        repo = blockchain.getRepository().startTracking();

        // Contract has no funds, try to withdraw just 1 coin.
        byte[] input = ByteUtil.merge(Hex.decode("9424bba3"), new DataWordImpl(1).getData());
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        BulkExecutor exec = getNewExecutor(tx, block, repo);
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
    public void testWithdrawFromFvmContract() throws IOException, VMException {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.TWO.pow(32);
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        Address contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertNull(contract);
            return;
        }

        BigInteger deployerBalance = repo.getBalance(deployer);

        // Contract has 2^32 coins, let's withdraw them.
        byte[] input = ByteUtil.merge(Hex.decode("9424bba3"), new DataWordImpl(value).getData());
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        BulkExecutor exec = getNewExecutor(tx, block, repo);
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
    public void testSendContractFundsToOtherAddress() throws IOException, VMException {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.TWO.pow(13);
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        Address contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertNull(contract);
            return;
        }

        BigInteger deployerBalance = repo.getBalance(deployer);

        // Create a new account to be our fund recipient.
        Address recipient = new Address(RandomUtils.nextBytes(Address.SIZE));
        repo.createAccount(recipient);

        // Contract has 2^13 coins, let's withdraw them.
        byte[] input = ByteUtil.merge(Hex.decode("8c50612c"), recipient.toBytes());
        input = ByteUtil.merge(input, new DataWordImpl(value).getData());
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        BulkExecutor exec = getNewExecutor(tx, block, repo);
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
    public void testSendContractFundsToNonexistentAddress() throws IOException, VMException {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.TWO.pow(13);
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        Address contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertNull(contract);
            return;
        }

        BigInteger deployerBalance = repo.getBalance(deployer);

        // Create a new account to be our fund recipient.
        Address recipient = new Address(RandomUtils.nextBytes(Address.SIZE));

        // Contract has 2^13 coins, let's withdraw them.
        byte[] input = ByteUtil.merge(Hex.decode("8c50612c"), recipient.toBytes());
        input = ByteUtil.merge(input, new DataWordImpl(value).getData());
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        BulkExecutor exec = getNewExecutor(tx, block, repo);
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
    public void testCallContractViaAnotherContract() throws IOException, VMException {
        // Deploy the MultiFeatureContract.
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.TWO.pow(20);
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        Address multiFeatureContract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertNull(multiFeatureContract);
            return;
        }

        deployerBalance = repo.getBalance(deployer);
        deployerNonce = repo.getNonce(deployer);

        // Deploy the MultiFeatureCaller contract.
        contractName = "MultiFeatureCaller";
        deployCode = getDeployCode(contractName);
        value = BigInteger.ZERO;
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        nonce = nonce.add(BigInteger.ONE);
        Address callerContract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);
        Address recipient = Address.wrap(RandomUtils.nextBytes(Address.SIZE));
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
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        BulkExecutor exec = getNewExecutor(tx, block, repo);
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
        input = ByteUtil.merge(input, new DataWordImpl(value).getData());
        nonce = nonce.add(BigInteger.ONE);
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        callerContract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        block = makeBlock(tx);
        exec = getNewExecutor(tx, block, repo);
        summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        txCost = BigInteger.valueOf(tx.getNrgConsume()).multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));
        assertEquals(value, repo.getBalance(recipient));
    }

    @Test
    public void testRecursiveStackoverflow() throws IOException, VMException {
        String contractName = "Recursive";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = Constants.NRG_TRANSACTION_MAX;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        Address contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertNull(contract);
            return;
        }

        deployerBalance = repo.getBalance(deployer);
        deployerNonce = repo.getNonce(deployer);

        // First recurse 1 time less than the max and verify this is ok.
        int numRecurses = Constants.MAX_CALL_DEPTH - 1;
        byte[] input = ByteUtil.merge(Hex.decode("2d7df21a"), contract.toBytes());
        input = ByteUtil.merge(input, new DataWordImpl(numRecurses + 1).getData());
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        BulkExecutor exec = getNewExecutor(tx, block, repo);
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
        input = ByteUtil.merge(input, new DataWordImpl(numRecurses + 1).getData());
        nonce = nonce.add(BigInteger.ONE);
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        block = makeBlock(tx);
        exec = getNewExecutor(tx, block, repo);
        summary = exec.execute().get(0);
        assertEquals("REVERT", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        txCost = BigInteger.valueOf(tx.getNrgConsume()).multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));
    }

    // TODO: find a better way of testing a mocked up Precompiled contract..
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
        //                        Address.wrap(ContractFactoryMock.CALL_ME),
        //                        value.toByteArray(),
        //                        tagToSend.getBytes(),
        //                        nrg,
        //                        nrgPrice);
        //        RepositoryCache repo = blockchain.getRepository().startTracking();
        //
        //        tx.sign(deployerKey);
        //        assertFalse(tx.isContractCreationTransaction());
        //
        //        assertEquals(Builder.DEFAULT_BALANCE,
        // blockchain.getRepository().getBalance(deployer));
        //        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));
        //
        //        assertFalse(CallMePrecompiledContract.youCalledMe);
        //        BlockContext context =
        //                blockchain.createNewBlockContext(
        //                        blockchain.getBestBlock(), Collections.singletonList(tx), false);
        //
        //        TransactionExecutor exec = new TransactionExecutor(tx, context.block, repo,
        // LOGGER_VM);
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
    public void testRedeployContractAtExistentContractAddress() throws IOException, VMException {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);

        // Mock up the repo so that the contract address already exists.
        AionRepositoryCache repo = mock(AionRepositoryCache.class);
        when(repo.hasAccountState(Mockito.any(Address.class))).thenReturn(true);
        when(repo.getNonce(Mockito.any(Address.class))).thenReturn(nonce);
        when(repo.getBalance(Mockito.any(Address.class))).thenReturn(Builder.DEFAULT_BALANCE);
        when(repo.getCode(Mockito.any(Address.class))).thenReturn(new byte[1]);

        when(repo.startTracking()).thenReturn(repo);

        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        AionBlock block = makeBlock(tx);
        BulkExecutor exec = getNewExecutor(tx, block, repo);
        AionTxExecSummary summary = exec.execute().get(0);

        if (txType == TransactionTypes.DEFAULT) {
            assertEquals("FAILURE", summary.getReceipt().getError());
            assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
            assertEquals(nrg, tx.getNrgConsume());
        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertEquals("FAILED_INVALID_DATA", summary.getReceipt().getError());
            assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
            assertEquals(nrg, tx.getNrgConsume());
        }
    }

    @Test
    public void tellFvmContractCallBalanceTransfer() throws IOException, VMException {
        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            return;
        }

        String contractName = "InternalCallContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = Constants.NRG_TRANSACTION_MAX;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        Address contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce, true);
        assertNotNull(contract);

        repo = blockchain.getRepository().startTracking();

        byte[] input =
                Arrays.copyOfRange(HashUtil.keccak256("sendValueToContract()".getBytes()), 0, 4);
        // input = ByteUtil.merge(input, new DataWordImpl(numRecurses + 1).getData());
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.TEN.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        BigInteger senderBalance = repo.getBalance(deployer);

        AionBlock block = makeBlock(tx);
        BulkExecutor exec = getNewExecutor(tx, block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());
        Pair<ImportResult, AionBlockSummary> result = blockchain.tryToConnectAndFetchSummary(block);
        assertTrue(result.getLeft().isSuccessful());

        assertEquals(BigInteger.TEN, blockchain.getRepository().getBalance(contract));
        assertEquals(
                senderBalance
                        .subtract(BigInteger.TEN)
                        .subtract(
                                BigInteger.valueOf(tx.getNrgConsume())
                                        .multiply(BigInteger.valueOf(nrgPrice))),
                blockchain.getRepository().getBalance(deployer));

        input =
                Arrays.copyOfRange(
                        HashUtil.keccak256("callBalanceTransfer(address)".getBytes()), 0, 4);
        Address receiver =
                new Address("0x000000000000000000000000000000000000000000000000000000000000000a");
        input = ByteUtil.merge(input, receiver.toBytes());
        nonce = nonce.add(BigInteger.ONE);
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        repo = blockchain.getRepository().startTracking();

        block = makeBlock(tx);
        exec = getNewExecutor(tx, block, repo);
        summary = exec.execute().get(0);
        assertEquals("", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        result = blockchain.tryToConnectAndFetchSummary(block);
        assertTrue(result.getLeft().isSuccessful());
        assertEquals(1, result.getRight().getSummaries().get(0).getInternalTransactions().size());
        assertEquals(
                TransactionTypes.DEFAULT,
                result.getRight()
                        .getSummaries()
                        .get(0)
                        .getInternalTransactions()
                        .get(0)
                        .getTargetVM());

        assertEquals(
                BigInteger.TEN.subtract(BigInteger.ONE),
                blockchain.getRepository().getBalance(contract));
        assertEquals(BigInteger.ONE, blockchain.getRepository().getBalance(receiver));
    }

    @Test
    public void tellFvmContractCallWithinDeployingBlock() throws IOException, VMException {
        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            return;
        }

        String contractName = "InternalCallContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = Constants.NRG_TRANSACTION_MAX;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);

        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(deployerBalance, repo.getBalance(deployer));
        assertEquals(deployerNonce, repo.getNonce(deployer));

        List<AionTransaction> ls = new ArrayList<>();
        ls.add(tx);

        byte[] input =
                Arrays.copyOfRange(HashUtil.keccak256("sendValueToContract()".getBytes()), 0, 4);
        AionTransaction tx2 =
                new AionTransaction(
                        nonce.toByteArray(),
                        tx.getContractAddress(),
                        BigInteger.TEN.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType);
        tx2.sign(deployerKey);
        assertFalse(tx2.isContractCreationTransaction());
        ls.add(tx2);

        BigInteger senderBalance = repo.getBalance(deployer);

        AionBlock parent = blockchain.getBestBlock();
        AionBlock block = blockchain.createBlock(parent, ls, false, parent.getTimestamp());

        Pair<ImportResult, AionBlockSummary> result = blockchain.tryToConnectAndFetchSummary(block);
        assertTrue(result.getLeft().isSuccessful());

        assertEquals(
                BigInteger.TEN, blockchain.getRepository().getBalance(tx.getContractAddress()));
        assertEquals(
                senderBalance
                        .subtract(BigInteger.TEN)
                        .subtract(
                                BigInteger.valueOf(tx.getNrgConsume())
                                        .multiply(BigInteger.valueOf(nrgPrice)))
                        .subtract(
                                BigInteger.valueOf(tx2.getNrgConsume())
                                        .multiply(BigInteger.valueOf(nrgPrice))),
                blockchain.getRepository().getBalance(deployer));

        repo = blockchain.getRepository().startTracking();
        assertEquals(BigInteger.TWO, repo.getNonce(deployer));
    }

    @Test
    public void tellFvmContractCallAvmContract() throws IOException, VMException {
        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            return;
        }

        String contractName = "InternalCallContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = Constants.NRG_TRANSACTION_MAX;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        Address contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce, true);

        assertNotNull(contract);
        repo = blockchain.getRepository().startTracking();

        Address avmAddress = deployAvmContract(nonce);
        assertNotNull(avmAddress);

        nonce = nonce.add(BigInteger.ONE);
        byte[] input = Arrays.copyOfRange(HashUtil.keccak256("callAVM(address)".getBytes()), 0, 4);
        input = ByteUtil.merge(input, avmAddress.toBytes());
        tx =
                new AionTransaction(
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        BulkExecutor exec = getNewExecutor(tx, block, repo);
        AionTxExecSummary summary = exec.execute().get(0);
        // The evmjit only return the the transaction success or failed when performing the function
        // call.
        assertEquals("REVERT", summary.getReceipt().getError());
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());
        Pair<ImportResult, AionBlockSummary> result = blockchain.tryToConnectAndFetchSummary(block);
        assertTrue(result.getLeft().isSuccessful());
        assertTrue(result.getRight().getSummaries().get(0).isFailed());
    }

    @Test
    public void testDeployFvmContractToAnExistedAccountBefore040Fork() throws IOException {
        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            return;
        }

        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ONE;

        Address destinationAddr =
                Address.wrap(HashUtil.calcNewAddr(deployer.toBytes(), deployerNonce.toByteArray()));

        // create a tx the sender send some balance to the account the deployer will deploy in the
        // feature.
        AionTransaction tx =
                new AionTransaction(
                        senderNonce.toByteArray(),
                        destinationAddr,
                        value.toByteArray(),
                        new byte[0],
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(senderKey);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        assertEquals(1, block.getTransactionsList().size());

        Pair<ImportResult, AionBlockSummary> result = blockchain.tryToConnectAndFetchSummary(block);
        assertTrue(result.getLeft().isSuccessful());

        RepositoryCache repo = blockchain.getRepository().startTracking();
        assertEquals(BigInteger.ONE, repo.getBalance(destinationAddr));
        BigInteger txCost =
                BigInteger.valueOf(nrgPrice)
                        .multiply(
                                BigInteger.valueOf(
                                        result.getRight().getReceipts().get(0).getEnergyUsed()));
        assertEquals(
                senderBalance.subtract(BigInteger.ONE).subtract(txCost), repo.getBalance(sender));

        senderNonce = senderNonce.add(BigInteger.ONE);

        String contractName = "PayableConstructor";
        byte[] deployCode = getDeployCode(contractName);

        // to == null  signals that this is contract creation.
        tx =
                new AionTransaction(
                        deployerNonce.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        block = makeBlock(tx);
        assertEquals(1, block.getTransactionsList().size());

        result = blockchain.tryToConnectAndFetchSummary(block);
        assertTrue(result.getLeft().isSuccessful());

        repo = blockchain.getRepository().startTracking();

        AionTxExecSummary summary = result.getRight().getSummaries().get(0);

        if (txType == TransactionTypes.DEFAULT) {
            assertEquals("FAILURE", summary.getReceipt().getError()); // "" == SUCCESS
            deployerNonce = deployerNonce.add(BigInteger.ONE);
            checkStateOfDeployer(repo, summary, nrgPrice, BigInteger.ZERO, deployerNonce);
        }

        assertEquals(tx.getNrgConsume(), summary.getReceipt().getEnergyUsed());
    }

    @Test
    public void testDeployFvmContractToAnExistedAccountWith040Fork() throws IOException {
        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            return;
        }

        blockchain.set040ForkNumber(0);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ONE;

        Address destinationAddr =
                Address.wrap(HashUtil.calcNewAddr(deployer.toBytes(), deployerNonce.toByteArray()));

        // create a tx the sender send some balance to the account the deployer will deploy in the
        // feature.
        AionTransaction tx =
                new AionTransaction(
                        senderNonce.toByteArray(),
                        destinationAddr,
                        value.toByteArray(),
                        new byte[0],
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(senderKey);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        assertEquals(1, block.getTransactionsList().size());

        Pair<ImportResult, AionBlockSummary> result = blockchain.tryToConnectAndFetchSummary(block);
        assertTrue(result.getLeft().isSuccessful());

        RepositoryCache repo = blockchain.getRepository().startTracking();
        assertEquals(BigInteger.ONE, repo.getBalance(destinationAddr));
        BigInteger txCost =
                BigInteger.valueOf(nrgPrice)
                        .multiply(
                                BigInteger.valueOf(
                                        result.getRight().getReceipts().get(0).getEnergyUsed()));
        assertEquals(
                senderBalance.subtract(BigInteger.ONE).subtract(txCost), repo.getBalance(sender));

        senderNonce = senderNonce.add(BigInteger.ONE);

        String contractName = "PayableConstructor";
        byte[] deployCode = getDeployCode(contractName);

        // to == null  signals that this is contract creation.
        tx =
                new AionTransaction(
                        deployerNonce.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        block = makeBlock(tx);
        assertEquals(1, block.getTransactionsList().size());

        result = blockchain.tryToConnectAndFetchSummary(block);
        assertTrue(result.getLeft().isSuccessful());

        repo = blockchain.getRepository().startTracking();

        AionTxExecSummary summary = result.getRight().getSummaries().get(0);

        if (txType == TransactionTypes.DEFAULT) {
            assertEquals("", summary.getReceipt().getError()); // "" == SUCCESS
            Address contract = tx.getContractAddress();
            checkStateOfNewContract(
                    repo,
                    contractName,
                    contract,
                    summary.getResult(),
                    FastVmResultCode.SUCCESS,
                    value);
            deployerNonce = deployerNonce.add(BigInteger.ONE);
            checkStateOfDeployer(repo, summary, nrgPrice, BigInteger.ZERO, deployerNonce);
        }

        assertEquals(tx.getNrgConsume(), summary.getReceipt().getEnergyUsed());
    }

    @Test
    public void testDeployAvmContractToAnExistedAccount() {
        if (txType == TransactionTypes.DEFAULT) {
            return;
        }

        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ONE;

        Address avmAddress =
                Address.wrap(HashUtil.calcNewAddr(deployer.toBytes(), deployerNonce.toByteArray()));

        // create a tx the sender send some balance to the account the deployer will deploy in the
        // feature.
        AionTransaction tx =
                new AionTransaction(
                        senderNonce.toByteArray(),
                        avmAddress,
                        value.toByteArray(),
                        new byte[0],
                        nrg,
                        nrgPrice,
                        TransactionTypes.DEFAULT);
        tx.sign(senderKey);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        assertEquals(1, block.getTransactionsList().size());

        Pair<ImportResult, AionBlockSummary> result = blockchain.tryToConnectAndFetchSummary(block);
        assertTrue(result.getLeft().isSuccessful());

        RepositoryCache repo = blockchain.getRepository().startTracking();
        assertEquals(BigInteger.ONE, repo.getBalance(avmAddress));
        BigInteger txCost =
                BigInteger.valueOf(nrgPrice)
                        .multiply(
                                BigInteger.valueOf(
                                        result.getRight().getReceipts().get(0).getEnergyUsed()));
        assertEquals(
                senderBalance.subtract(BigInteger.ONE).subtract(txCost), repo.getBalance(sender));

        senderNonce = senderNonce.add(BigInteger.ONE);

        Address avmDeployedAddress = deployAvmContract(deployerNonce);
        assertNotNull(avmAddress);
        assertEquals(avmAddress, avmDeployedAddress);

        repo = blockchain.getRepository().startTracking();
        byte[] avmCode = JarBuilder.buildJarForMainAndClassesAndUserlib(AvmHelloWorld.class);
        assertEquals(avmCode.length, repo.getCode(avmAddress).length);
        // TODO: find a way to check the deploy code is consistent with the code get from the repo.
        // assertArrayEquals(avmCode, repo.getCode(avmAddress));

        assertEquals(BigInteger.ONE, repo.getBalance(avmAddress));

        byte[] call = getCallArguments();
        tx =
                new AionTransaction(
                        deployerNonce.add(BigInteger.ONE).toByteArray(),
                        avmAddress,
                        BigInteger.ZERO.toByteArray(),
                        call,
                        2_000_000,
                        nrgPrice,
                        TransactionTypes.DEFAULT);
        tx.sign(this.deployerKey);

        block =
                this.blockchain.createNewBlock(
                        this.blockchain.getBestBlock(), Collections.singletonList(tx), false);
        Pair<ImportResult, AionBlockSummary> connectResult =
                this.blockchain.tryToConnectAndFetchSummary(block);
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);

        // Check the block was imported and the transaction was successful.
        assertThat(connectResult.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(receipt.isSuccessful()).isTrue();
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
        //                        nonce.toByteArray(), null, value.toByteArray(), deployCode, nrg,
        // nrgPrice);
        //        RepositoryCache repo = blockchain.getRepository().startTracking();
        //        nonce = nonce.add(BigInteger.ONE);
        //        Address contract =
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
        //                        Address.wrap(ContractFactoryMock.CALL_ME).toBytes());
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
        //        TransactionExecutor exec = new TransactionExecutor(tx, context.block, repo,
        // LOGGER_VM);
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
        //
        // BigInteger.valueOf(tx.getNrgConsume()).multiply(BigInteger.valueOf(nrgPrice));
        //        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));
        //        assertTrue(CallMePrecompiledContract.youCalledMe);
    }

    // <------------------------------------------HELPERS------------------------------------------->

    /**
     * Deploys a contract named contractName and checks the state of the deployed contract and the
     * contract deployer and returns the address of the contract once finished.
     */
    private Address deployContract(
            RepositoryCache repo,
            AionTransaction tx,
            String contractName,
            String contractFilename,
            BigInteger value,
            long nrg,
            long nrgPrice,
            BigInteger nonce)
            throws IOException, VMException {

        return deployContract(
                repo, tx, contractName, contractFilename, value, nrg, nrgPrice, nonce, false);
    }

    private Address deployContract(
            RepositoryCache repo,
            AionTransaction tx,
            String contractName,
            String contractFilename,
            BigInteger value,
            long nrg,
            long nrgPrice,
            BigInteger nonce,
            boolean addToBlockChain)
            throws IOException, VMException {

        tx.sign(deployerKey);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(deployerBalance, repo.getBalance(deployer));
        assertEquals(deployerNonce, repo.getNonce(deployer));

        AionBlock block = makeBlock(tx);
        BulkExecutor exec = getNewExecutor(tx, block, repo);
        AionTxExecSummary summary = exec.execute().get(0);

        if (!summary.getReceipt().getError().equals("")) {
            return null;
        }
        assertEquals(tx.getNrgConsume(), summary.getNrgUsed().longValue());
        assertNotEquals(nrg, tx.getNrgConsume());

        Address contract = tx.getContractAddress();
        if (contractFilename == null) {
            checkStateOfNewContract(
                    repo,
                    contractName,
                    contract,
                    summary.getResult(),
                    FastVmResultCode.SUCCESS,
                    value);
        } else {
            checkStateOfNewContract(
                    repo,
                    contractName,
                    contractFilename,
                    contract,
                    summary.getResult(),
                    FastVmResultCode.SUCCESS,
                    value);
        }
        checkStateOfDeployer(repo, summary, nrgPrice, value, nonce);

        if (addToBlockChain) {
            Pair<ImportResult, AionBlockSummary> result =
                    blockchain.tryToConnectAndFetchSummary(block);
            assertTrue(result.getLeft().isSuccessful());
        }
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
            RepositoryCache repo,
            String contractName,
            Address contractAddr,
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
            RepositoryCache repo,
            String contractName,
            String contractFilename,
            Address contractAddr,
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
            RepositoryCache repo,
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
    private void checkStateOfDeployerOnBadDeploy(RepositoryCache repo) {

        assertEquals(BigInteger.ZERO, repo.getNonce(deployer));
        assertEquals(Builder.DEFAULT_BALANCE, repo.getBalance(deployer));
    }

    /**
     * Extracts output from rawOutput under the assumption that rawOutput is the result output of a
     * call to the fastVM and this output is of variable length not predefined length.
     */
    private byte[] extractOutput(byte[] rawOutput) {
        int headerLen =
                new DataWordImpl(Arrays.copyOfRange(rawOutput, 0, DataWordImpl.BYTES)).intValue();
        int outputLen =
                new DataWordImpl(
                                Arrays.copyOfRange(
                                        rawOutput,
                                        (DataWordImpl.BYTES * 2) - headerLen,
                                        DataWordImpl.BYTES * 2))
                        .intValue();
        byte[] output = new byte[outputLen];
        System.arraycopy(rawOutput, DataWordImpl.BYTES * 2, output, 0, outputLen);
        return output;
    }

    private BulkExecutor getNewExecutor(
            AionTransaction tx, IAionBlock block, RepositoryCache repo) {
        ExecutionBatch details = new ExecutionBatch(block, Collections.singletonList(tx));
        return new BulkExecutor(
                details,
                repo,
                false,
                true,
                block.getNrgLimit(),
                true,
                LOGGER_VM,
                getPostExecutionWork());
    }

    private PostExecutionWork getPostExecutionWork() {
        return (r, c, s, t, b) -> {
            return 0L;
        };
    }

    private AionBlock makeBlock(AionTransaction tx) {
        AionBlock parent = blockchain.getBestBlock();
        return blockchain.createBlock(
                parent, Collections.singletonList(tx), false, parent.getTimestamp());
    }

    private byte[] getCallArguments() {
        return ABIEncoder.encodeOneString("sayHello");
    }

    private byte[] getJarBytes() {
        return new CodeAndArguments(
                        JarBuilder.buildJarForMainAndClassesAndUserlib(AvmHelloWorld.class),
                        new byte[0])
                .encodeToBytes();
    }

    private Address deployAvmContract(BigInteger nonce) {
        byte[] jar = getJarBytes();
        AionTransaction transaction =
                new AionTransaction(
                        nonce.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        jar,
                        5_000_000L,
                        1,
                        TransactionTypes.AVM_CREATE_CODE);

        transaction.sign(this.deployerKey);

        AionBlock block =
                this.blockchain.createNewBlock(
                        this.blockchain.getBestBlock(),
                        Collections.singletonList(transaction),
                        false);
        Pair<ImportResult, AionBlockSummary> connectResult =
                this.blockchain.tryToConnectAndFetchSummary(block);
        AionTxReceipt receipt = connectResult.getRight().getReceipts().get(0);

        // Check the block was imported, the contract has the Avm prefix, and deployment succeeded.
        assertThat(connectResult.getLeft()).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(receipt.getTransactionOutput()[0]).isEqualTo(AddressSpecs.A0_IDENTIFIER);
        assertThat(receipt.isSuccessful()).isTrue();

        // verify that the output is indeed the contract address
        assertThat(transaction.getContractAddress().toBytes())
                .isEqualTo(receipt.getTransactionOutput());

        return transaction.getContractAddress();
    }
}
