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
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.aion.zero.impl.vm.common.VmFatalException;
import org.aion.avm.stub.AvmVersion;
import org.aion.avm.stub.IAvmResourceFactory;
import org.aion.avm.stub.IContractFactory.AvmContract;
import org.aion.base.AionTransaction;
import org.aion.base.Constants;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.ECKey;
import org.aion.crypto.HashUtil;
import org.aion.fastvm.FastVmResultCode;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.blockchain.BlockchainTestUtils;
import org.aion.zero.impl.core.ImportResult;
import org.aion.mcf.db.RepositoryCache;
import org.aion.base.TransactionTypeRule;
import org.aion.util.types.DataWord;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.util.types.AddressUtils;
import org.aion.zero.impl.vm.common.BlockCachingContext;
import org.aion.zero.impl.vm.common.BulkExecutor;
import org.aion.zero.impl.blockchain.StandaloneBlockchain;
import org.aion.zero.impl.blockchain.StandaloneBlockchain.Builder;
import org.aion.zero.impl.db.AionRepositoryCache;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.vm.contracts.ContractUtils;
import org.aion.base.AionTxExecSummary;
import org.aion.base.AionTxReceipt;
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
    private TestResourceProvider resourceProvider;
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey, senderKey;
    private AionAddress deployer, sender;
    private BigInteger deployerBalance, deployerNonce, senderBalance, senderNonce;
    private byte txType;
    private List<ECKey> accounts;
    private long energyPrice = 10_000_000_000L;

    @Parameters
    public static Object[] data() {
        return new Object[] {TransactionTypes.DEFAULT, TransactionTypes.AVM_CREATE_CODE};
    }

    public ContractIntegTest(byte _txType) {
        txType = _txType;
    }

    @Before
    public void setup() throws Exception {
        // reduce default logging levels
        AionLoggerFactory.initAll();

        StandaloneBlockchain.Bundle bundle =
                (new Builder()).withValidatorConfiguration("simple").withDefaultAccounts().build();
        TransactionTypeRule.allowAVMContractTransaction();
        blockchain = bundle.bc;
        deployerKey = bundle.privateKeys.get(0);
        accounts = bundle.privateKeys;
        deployer = new AionAddress(deployerKey.getAddress());
        deployerBalance = Builder.DEFAULT_BALANCE;
        deployerNonce = BigInteger.ZERO;

        senderKey = bundle.privateKeys.get(1);
        sender = new AionAddress(senderKey.getAddress());
        senderBalance = Builder.DEFAULT_BALANCE;
        senderNonce = BigInteger.ZERO;

        resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());

        AvmTestConfig.supportOnlyAvmVersion1();
        blockchain.forkUtility.disableUnityFork();
    }

    @After
    public void tearDown() throws Exception {
        blockchain.forkUtility.disableUnityFork();
        blockchain = null;
        deployerKey = null;
        deployer = null;
        deployerBalance = null;
        deployerNonce = null;
        sender = null;
        senderBalance = null;
        senderKey = null;
        senderNonce = null;

        AvmTestConfig.clearConfigurations();
        resourceProvider.close();
    }

    @Test
    public void testFvmEmptyContract() throws Exception {
        String contractName = "EmptyContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        AionBlock block = makeBlock(tx);
        RepositoryCache repo = blockchain.getRepository().startTracking();

        AionTxExecSummary summary =
                BulkExecutor.executeTransactionWithNoPostExecutionWork(
                        block.getDifficulty(),
                        block.getNumber(),
                        block.getTimestamp(),
                        block.getNrgLimit(),
                        block.getCoinbase(),
                        tx,
                        repo,
                        false,
                        true,
                        false,
                        false,
                        LOGGER_VM,
                        BlockCachingContext.PENDING,
                        block.getNumber() - 1,
                        false);

        if (txType == TransactionTypes.DEFAULT) {
            assertEquals("", summary.getReceipt().getError()); // "" == SUCCESS
            AionAddress contract = TxUtil.calculateContractAddress(tx);
            checkStateOfNewContract(
                    repo,
                    contractName,
                    contract,
                    summary.getResult(),
                    FastVmResultCode.SUCCESS,
                    value);
            nonce = nonce.add(BigInteger.ONE);
            checkStateOfDeployer(repo, summary, nrgPrice, value, nonce);
            assertEquals(226186, summary.getReceipt().getEnergyUsed());

        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertEquals("Failed: invalid data", summary.getReceipt().getError());
            nonce = nonce.add(BigInteger.ONE);
            checkStateOfDeployer(repo, summary, nrgPrice, value, nonce);
            assertEquals(1000000, summary.getReceipt().getEnergyUsed());
        }
    }

    @Test
    public void testContractDeployCodeIsEmpty() throws Exception {
        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        new byte[0],
                        nrg,
                        nrgPrice,
                        txType, null);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        AionBlock block = makeBlock(tx);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        AionTxExecSummary summary = executeTransaction(tx, block, repo);

        if (txType == TransactionTypes.DEFAULT) {
            assertEquals("", summary.getReceipt().getError()); // "" == SUCCESS
            assertEquals(221000, summary.getReceipt().getEnergyUsed());

            AionAddress contract = TxUtil.calculateContractAddress(tx);
            assertArrayEquals(new byte[0], summary.getResult());
            assertArrayEquals(new byte[0], repo.getCode(contract));
            assertEquals(BigInteger.ZERO, repo.getBalance(contract));
            assertEquals(BigInteger.ZERO, repo.getNonce(contract));

            assertEquals(BigInteger.ONE, repo.getNonce(deployer));

        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertEquals("Failed: invalid data", summary.getReceipt().getError());
            nonce = nonce.add(BigInteger.ONE);
            checkStateOfDeployer(repo, summary, nrgPrice, value, nonce);
        }

        BigInteger txCost = summary.getNrgUsed().multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(Builder.DEFAULT_BALANCE.subtract(txCost), repo.getBalance(deployer));
    }

    @Test
    public void testContractDeployCodeIsNonsensical() throws Exception {
        byte[] deployCode = new byte[1];
        deployCode[0] = 0x1;
        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        AionBlock block = makeBlock(tx);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        AionTxExecSummary summary = executeTransaction(tx, block, repo);

        if (txType == TransactionTypes.DEFAULT) {
            assertEquals("OUT_OF_NRG", summary.getReceipt().getError());
            assertEquals(nrg, summary.getNrgUsed().longValue());

            AionAddress contract = TxUtil.calculateContractAddress(tx);
            assertArrayEquals(new byte[0], summary.getResult());
            assertArrayEquals(new byte[0], repo.getCode(contract));
            assertEquals(BigInteger.ZERO, repo.getBalance(contract));
            assertEquals(BigInteger.ZERO, repo.getNonce(contract));

            assertEquals(BigInteger.ONE, repo.getNonce(deployer));

        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertEquals("Failed: invalid data", summary.getReceipt().getError());
            nonce = nonce.add(BigInteger.ONE);
            checkStateOfDeployer(repo, summary, nrgPrice, value, nonce);
        }

        BigInteger txCost = summary.getNrgUsed().multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(Builder.DEFAULT_BALANCE.subtract(txCost), repo.getBalance(deployer));
    }

    @Test
    public void testDeployWithOutCode() throws Exception {

        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ZERO; // attempt to transfer value to new contract.
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        new byte[0],
                        nrg,
                        nrgPrice,
                        txType, null);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        if (txType == TransactionTypes.DEFAULT) {
            AionBlock block = makeBlock(tx);
            RepositoryCache repo = blockchain.getRepository().startTracking();
            AionTxExecSummary summary = executeTransaction(tx, block, repo);

            assertEquals("", summary.getReceipt().getError());
            assertNotEquals(nrg, summary.getNrgUsed().longValue()); // all energy is not used up.

            AionAddress contract = TxUtil.calculateContractAddress(tx);

            checkStateOfDeployer(
                    repo, summary, nrgPrice, BigInteger.ZERO, nonce.add(BigInteger.ONE));
            byte[] code = repo.getCode(contract);
            assertNotNull(code);
        } else {
            blockchain.set040ForkNumber(0);
            AionBlock block = makeBlock(tx);
            RepositoryCache repo = blockchain.getRepository().startTracking();
            AionTxExecSummary summary = executeTransaction(tx, block, repo);

            assertEquals("Failed: invalid data", summary.getReceipt().getError());
        }
    }

    @Test
    public void testFvmTransferValueToNonPayableConstructor() throws Exception {
        String contractName = "EmptyContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ONE; // attempt to transfer value to new contract.
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        AionBlock block = makeBlock(tx);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        AionTxExecSummary summary = executeTransaction(tx, block, repo);
        if (txType == TransactionTypes.DEFAULT) {

            assertEquals("reverted", summary.getReceipt().getError());
            assertNotEquals(nrg, summary.getNrgUsed().longValue()); // all energy is not used up.

            AionAddress contract = TxUtil.calculateContractAddress(tx);
            checkStateOfNewContract(
                    repo,
                    contractName,
                    contract,
                    summary.getResult(),
                    FastVmResultCode.REVERT,
                    BigInteger.ZERO);
            nonce = nonce.add(BigInteger.ONE);
        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertEquals("Failed: invalid data", summary.getReceipt().getError());
            nonce = nonce.add(BigInteger.ONE);
        }

        checkStateOfDeployer(repo, summary, nrgPrice, BigInteger.ZERO, nonce);
    }

    @Test
    public void testTransferValueToPayableConstructor() throws Exception {
        String contractName = "PayableConstructor";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.TWO.pow(10); // attempt to transfer value to new contract.
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        AionBlock block = makeBlock(tx);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        AionTxExecSummary summary = executeTransaction(tx, block, repo);

        if (txType == TransactionTypes.DEFAULT) {
            assertEquals("", summary.getReceipt().getError());
            assertNotEquals(nrg, summary.getNrgUsed().longValue()); // all energy is not used up.

            AionAddress contract = TxUtil.calculateContractAddress(tx);
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
            assertEquals("Failed: invalid data", summary.getReceipt().getError());
            nonce = nonce.add(BigInteger.ONE);
            checkStateOfDeployer(repo, summary, nrgPrice, BigInteger.ZERO, nonce);
        }
    }

    @Test
    public void testTransferValueToPayableConstructorInsufficientFunds() throws Exception {
        String contractName = "PayableConstructor";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = Builder.DEFAULT_BALANCE.add(BigInteger.ONE); // send too much value.
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        AionBlock block = makeBlock(tx);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        AionTxExecSummary summary = executeTransaction(tx, block, repo);

        if (txType == TransactionTypes.DEFAULT) {
            assertEquals("INSUFFICIENT_BALANCE", summary.getReceipt().getError());
            assertEquals(tx.getEnergyLimit(), summary.getReceipt().getEnergyUsed());

            AionAddress contract = TxUtil.calculateContractAddress(tx);
            checkStateOfNewContract(
                    repo,
                    contractName,
                    contract,
                    summary.getResult(),
                    FastVmResultCode.INSUFFICIENT_BALANCE,
                    BigInteger.ZERO);
            checkStateOfDeployerOnBadDeploy(repo);

        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertEquals("Rejected: insufficient balance", summary.getReceipt().getError());
            assertEquals(tx.getEnergyLimit(), summary.getReceipt().getEnergyUsed());
            checkStateOfDeployerOnBadDeploy(repo);
        }
    }

    @Test
    public void testFvmConstructorIsCalledOnCodeDeployment() throws Exception {
        String contractName = "MultiFeatureContract";
        byte[] deployCode =
                ContractUtils.getContractDeployer(
                        "MultiFeatureContract.sol", "MultiFeatureContract");
        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ONE;
        BigInteger nonce = BigInteger.ZERO;

        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);

        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        if (txType == TransactionTypes.DEFAULT) {
            // Now call the contract and check that the constructor message was set.
            String getMsgFunctionHash = "ce6d41de";
            tx =
                    AionTransaction.create(
                            deployerKey,
                            nonce.toByteArray(),
                            contract,
                            BigInteger.ZERO.toByteArray(),
                            Hex.decode(getMsgFunctionHash),
                            nrg,
                            nrgPrice,
                            txType, null);
            assertFalse(tx.isContractCreationTransaction());

            AionBlock block = makeBlock(tx);
            AionTxExecSummary summary = executeTransaction(tx, block, repo);
            assertEquals("", summary.getReceipt().getError());
            assertNotEquals(nrg, summary.getNrgUsed().longValue());

            String expectedMsg = "Im alive!";
            assertEquals(expectedMsg, new String(extractOutput(summary.getResult())));

        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertNull(contract);
        }
    }

    @Test
    public void testFvmCallFunction() throws Exception {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ONE;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertNull(contract);
            return;
        }

        //             ---------- This command will perform addition. ----------
        int num = 53475374;
        byte[] input = ByteUtil.merge(Hex.decode("f601704f"), new DataWord(num).getData());
        input = ByteUtil.merge(input, new DataWord(1).getData());
        tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        AionTxExecSummary summary = executeTransaction(tx, block, repo);
        assertEquals("", summary.getReceipt().getError());
        assertNotEquals(nrg, summary.getNrgUsed().longValue());

        // Since input takes in uint8 we only want the last byte of num. Output size is well-defined
        // at 128 bits, or 16 bytes.
        int expectedResult = 1111 + (num & 0xFF);
        assertEquals(expectedResult, new DataWord(summary.getResult()).intValue());

        //             --------- This command will perform subtraction. ----------
        input = ByteUtil.merge(Hex.decode("f601704f"), new DataWord(num).getData());
        input = ByteUtil.merge(input, new DataWord(0).getData());
        nonce = nonce.add(BigInteger.ONE);
        tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        TransactionTypes.DEFAULT, null);
        assertFalse(tx.isContractCreationTransaction());

        block = makeBlock(tx);
        summary = executeTransaction(tx, block, repo);
        assertEquals("", summary.getReceipt().getError());
        assertNotEquals(nrg, summary.getNrgUsed().longValue());

        // Since input takes in uint8 we only want the last byte of num. Output size is well-defined
        // at 128 bits, or 16 bytes.
        expectedResult = 1111 - (num & 0xFF);
        assertEquals(expectedResult, new DataWord(summary.getResult()).intValue());
    }

    @Test
    public void testFvmOverWithdrawFromContract() throws Exception {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertNull(contract);
            return;
        }

        BigInteger deployerBalance = repo.getBalance(deployer);
        repo.flush();
        repo = blockchain.getRepository().startTracking();

        // Contract has no funds, try to withdraw just 1 coin.
        byte[] input = ByteUtil.merge(Hex.decode("9424bba3"), new DataWord(1).getData());
        tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        AionTxExecSummary summary = executeTransaction(tx, block, repo);
        assertEquals("reverted", summary.getReceipt().getError());
        assertNotEquals(nrg, summary.getNrgUsed().longValue());

        System.out.println("DEP: " + deployerBalance);

        BigInteger txCost =
                BigInteger.valueOf(summary.getNrgUsed().longValue())
                        .multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));
    }

    @Test
    public void testWithdrawFromFvmContract() throws Exception {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.TWO.pow(32);
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertNull(contract);
            return;
        }

        BigInteger deployerBalance = repo.getBalance(deployer);

        // Contract has 2^32 coins, let's withdraw them.
        byte[] input = ByteUtil.merge(Hex.decode("9424bba3"), new DataWord(value).getData());
        tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        AionTxExecSummary summary = executeTransaction(tx, block, repo);
        assertEquals("", summary.getReceipt().getError());
        assertNotEquals(nrg, summary.getNrgUsed().longValue());

        // Check that the deployer did get the requested value sent back.
        BigInteger txCost =
                BigInteger.valueOf(summary.getNrgUsed().longValue())
                        .multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost).add(value), repo.getBalance(deployer));
    }

    @Test
    public void testSendContractFundsToOtherAddress() throws Exception {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.TWO.pow(13);
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertNull(contract);
            return;
        }

        BigInteger deployerBalance = repo.getBalance(deployer);

        // Create a new account to be our fund recipient.
        AionAddress recipient = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        repo.createAccount(recipient);

        // Contract has 2^13 coins, let's withdraw them.
        byte[] input = ByteUtil.merge(Hex.decode("8c50612c"), recipient.toByteArray());
        input = ByteUtil.merge(input, new DataWord(value).getData());
        tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        AionTxExecSummary summary = executeTransaction(tx, block, repo);
        assertEquals("", summary.getReceipt().getError());
        assertNotEquals(nrg, summary.getNrgUsed().longValue());

        BigInteger txCost =
                BigInteger.valueOf(summary.getNrgUsed().longValue())
                        .multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));

        // Check that the recipient received the value.
        assertEquals(value, repo.getBalance(recipient));
    }

    @Test
    public void testSendContractFundsToNonexistentAddress() throws Exception {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.TWO.pow(13);
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertNull(contract);
            return;
        }

        BigInteger deployerBalance = repo.getBalance(deployer);

        // Create a new account to be our fund recipient.
        AionAddress recipient = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));

        // Contract has 2^13 coins, let's withdraw them.
        byte[] input = ByteUtil.merge(Hex.decode("8c50612c"), recipient.toByteArray());
        input = ByteUtil.merge(input, new DataWord(value).getData());
        tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        AionTxExecSummary summary = executeTransaction(tx, block, repo);
        assertEquals("", summary.getReceipt().getError());
        assertNotEquals(nrg, summary.getNrgUsed().longValue());

        BigInteger txCost =
                BigInteger.valueOf(summary.getNrgUsed().longValue())
                        .multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));

        // Check that the recipient received the value.
        assertEquals(value, repo.getBalance(recipient));
    }

    @Test
    public void testCallContractViaAnotherContract() throws Exception {
        // Deploy the MultiFeatureContract.
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.TWO.pow(20);
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress multiFeatureContract =
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
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
        nonce = nonce.add(BigInteger.ONE);
        AionAddress callerContract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);
        AionAddress recipient = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        deployerBalance = repo.getBalance(deployer);
        deployerNonce = repo.getNonce(deployer);

        // Set the MultiFeatureCaller to call the deployed MultiFeatureContract.
        byte[] input = ByteUtil.merge(Hex.decode("8c30ffe6"), multiFeatureContract.toByteArray());
        tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        callerContract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        AionTxExecSummary summary = executeTransaction(tx, block, repo);
        assertEquals("", summary.getReceipt().getError());
        assertNotEquals(nrg, summary.getNrgUsed().longValue());

        BigInteger txCost =
                BigInteger.valueOf(summary.getNrgUsed().longValue())
                        .multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));
        deployerBalance = repo.getBalance(deployer);
        deployerNonce = repo.getNonce(deployer);

        // Now use the MultiFeatureCaller to call the MultiFeatureContract to send funds from that
        // contract to the recipient address.
        assertEquals(BigInteger.ZERO, repo.getBalance(recipient));

        value = BigInteger.TWO.pow(20);
        input = ByteUtil.merge(Hex.decode("57a60e6b"), recipient.toByteArray());
        input = ByteUtil.merge(input, new DataWord(value).getData());
        nonce = nonce.add(BigInteger.ONE);
        tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        callerContract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertFalse(tx.isContractCreationTransaction());

        block = makeBlock(tx);
        summary = executeTransaction(tx, block, repo);
        assertEquals("", summary.getReceipt().getError());
        assertNotEquals(nrg, summary.getNrgUsed().longValue());

        txCost =
                BigInteger.valueOf(summary.getNrgUsed().longValue())
                        .multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));
        assertEquals(value, repo.getBalance(recipient));
    }

    @Test
    public void testRecursiveStackoverflow() throws Exception {
        String contractName = "Recursive";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = Constants.NRG_TRANSACTION_MAX;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce);

        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertNull(contract);
            return;
        }

        deployerBalance = repo.getBalance(deployer);
        deployerNonce = repo.getNonce(deployer);

        // First recurse 1 time less than the max and verify this is ok.
        // Note that 128 == FvmConstants.MAX_CALL_DEPTH
        int numRecurses = 127;
        byte[] input = ByteUtil.merge(Hex.decode("2d7df21a"), contract.toByteArray());
        input = ByteUtil.merge(input, new DataWord(numRecurses + 1).getData());
        tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        AionTxExecSummary summary = executeTransaction(tx, block, repo);
        assertEquals("", summary.getReceipt().getError());
        assertNotEquals(nrg, summary.getNrgUsed().longValue());

        BigInteger txCost =
                BigInteger.valueOf(summary.getNrgUsed().longValue())
                        .multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));

        deployerBalance = repo.getBalance(deployer);
        deployerNonce = repo.getNonce(deployer);
        repo.flush();
        repo = blockchain.getRepository().startTracking();

        // Now recurse the max amount of times and ensure we fail.
        // Note that 128 == FvmConstants.MAX_CALL_DEPTH
        numRecurses = 128;
        input = ByteUtil.merge(Hex.decode("2d7df21a"), contract.toByteArray());
        input = ByteUtil.merge(input, new DataWord(numRecurses + 1).getData());
        nonce = nonce.add(BigInteger.ONE);
        tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertFalse(tx.isContractCreationTransaction());

        block = makeBlock(tx);
        summary = executeTransaction(tx, block, repo);
        assertEquals("reverted", summary.getReceipt().getError());
        assertNotEquals(nrg, summary.getNrgUsed().longValue());

        txCost =
                BigInteger.valueOf(summary.getNrgUsed().longValue())
                        .multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(deployerBalance.subtract(txCost), repo.getBalance(deployer));
    }

    // TODO: find a better way of testing a mocked up Precompiled contract..
    @Test
    @Ignore
    public void testCallPrecompiledContract() {
        //        String tagToSend = "Soo cool!";
        //        long nrg = Constants.NRG_TRANSACTION_MAX;
        //        long nrgPrice = energyPrice;
        //        BigInteger value = BigInteger.ZERO;
        //        BigInteger nonce = BigInteger.ZERO;
        //        AionTransaction tx =
        //                AionTransaction.newAionTransaction(
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
        //                blockchain.createNewMiningBlockContext(
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
    public void testRedeployContractAtExistentContractAddress() throws Exception {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);

        // Mock up the repo so that the contract address already exists.
        AionRepositoryCache repo = mock(AionRepositoryCache.class);
        when(repo.hasAccountState(Mockito.any(AionAddress.class))).thenReturn(true);
        when(repo.getNonce(Mockito.any(AionAddress.class))).thenReturn(nonce);
        when(repo.getBalance(Mockito.any(AionAddress.class))).thenReturn(Builder.DEFAULT_BALANCE);
        when(repo.getCode(Mockito.any(AionAddress.class))).thenReturn(new byte[1]);

        when(repo.startTracking()).thenReturn(repo);

        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        AionBlock block = makeBlock(tx);
        AionTxExecSummary summary = executeTransaction(tx, block, repo);

        if (txType == TransactionTypes.DEFAULT) {
            assertEquals("FAILURE", summary.getReceipt().getError());
            assertEquals(nrg, summary.getNrgUsed().longValue());
        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertEquals("Failed: invalid data", summary.getReceipt().getError());
            assertEquals(nrg, summary.getNrgUsed().longValue());
        }
    }

    @Test
    public void tellFvmContractCallBalanceTransfer() throws Exception {
        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            return;
        }

        String contractName = "InternalCallContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = Constants.NRG_TRANSACTION_MAX;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce, true);
        assertNotNull(contract);

        repo = blockchain.getRepository().startTracking();

        byte[] input =
                Arrays.copyOfRange(HashUtil.keccak256("sendValueToContract()".getBytes()), 0, 4);
        // input = ByteUtil.merge(input, new DataWordImpl(numRecurses + 1).copyOfData());
        tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        contract,
                        BigInteger.TEN.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertFalse(tx.isContractCreationTransaction());

        BigInteger senderBalance = repo.getBalance(deployer);

        AionBlock block = makeBlock(tx);
        AionTxExecSummary summary = executeTransaction(tx, block, repo);
        assertEquals("", summary.getReceipt().getError());
        assertNotEquals(nrg, summary.getNrgUsed().longValue());
        Pair<ImportResult, AionBlockSummary> result = blockchain.tryToConnectAndFetchSummary(block);
        assertTrue(result.getLeft().isSuccessful());

        assertEquals(BigInteger.TEN, blockchain.getRepository().getBalance(contract));
        assertEquals(
                senderBalance
                        .subtract(BigInteger.TEN)
                        .subtract(
                                BigInteger.valueOf(summary.getNrgUsed().longValue())
                                        .multiply(BigInteger.valueOf(nrgPrice))),
                blockchain.getRepository().getBalance(deployer));

        input =
                Arrays.copyOfRange(
                        HashUtil.keccak256("callBalanceTransfer(address)".getBytes()), 0, 4);
        AionAddress receiver =
                AddressUtils.wrapAddress(
                        "0x000000000000000000000000000000000000000000000000000000000000000a");
        input = ByteUtil.merge(input, receiver.toByteArray());
        nonce = nonce.add(BigInteger.ONE);
        tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertFalse(tx.isContractCreationTransaction());

        repo = blockchain.getRepository().startTracking();

        block = makeBlock(tx);
        summary = executeTransaction(tx, block, repo);
        assertEquals("", summary.getReceipt().getError());
        assertNotEquals(nrg, summary.getNrgUsed().longValue());

        result = blockchain.tryToConnectAndFetchSummary(block);
        assertTrue(result.getLeft().isSuccessful());
        assertEquals(1, result.getRight().getSummaries().get(0).getInternalTransactions().size());
        assertEquals(
                BigInteger.TEN.subtract(BigInteger.ONE),
                blockchain.getRepository().getBalance(contract));
        assertEquals(BigInteger.ONE, blockchain.getRepository().getBalance(receiver));
    }

    @Test
    public void tellFvmContractCallWithinDeployingBlock() throws IOException {
        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            return;
        }

        String contractName = "InternalCallContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = Constants.NRG_TRANSACTION_MAX;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);

        assertTrue(tx.isContractCreationTransaction());

        assertEquals(deployerBalance, repo.getBalance(deployer));
        assertEquals(deployerNonce, repo.getNonce(deployer));

        List<AionTransaction> ls = new ArrayList<>();
        ls.add(tx);

        byte[] input =
                Arrays.copyOfRange(HashUtil.keccak256("sendValueToContract()".getBytes()), 0, 4);
        AionTransaction tx2 =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        TxUtil.calculateContractAddress(tx),
                        BigInteger.TEN.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertFalse(tx2.isContractCreationTransaction());
        ls.add(tx2);

        BigInteger senderBalance = repo.getBalance(deployer);

        Block parent = blockchain.getBestBlock();
        AionBlock block = blockchain.createBlock(parent, ls, false, parent.getTimestamp());

        Pair<ImportResult, AionBlockSummary> result = blockchain.tryToConnectAndFetchSummary(block);
        AionBlockSummary summary = result.getRight();
        assertTrue(result.getLeft().isSuccessful());

        AionAddress contractAddress = TxUtil.calculateContractAddress(tx);
        assertEquals(BigInteger.TEN, blockchain.getRepository().getBalance(contractAddress));
        assertEquals(
                senderBalance
                        .subtract(BigInteger.TEN)
                        .subtract(
                                BigInteger.valueOf(summary.getReceipts().get(0).getEnergyUsed())
                                        .multiply(BigInteger.valueOf(nrgPrice)))
                        .subtract(
                                BigInteger.valueOf(summary.getReceipts().get(1).getEnergyUsed())
                                        .multiply(BigInteger.valueOf(nrgPrice))),
                blockchain.getRepository().getBalance(deployer));

        repo = blockchain.getRepository().startTracking();
        assertEquals(BigInteger.TWO, repo.getNonce(deployer));
    }

    @Test
    public void tellFvmContractCallAvmContract() throws Exception {
        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            return;
        }

        String contractName = "InternalCallContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = Constants.NRG_TRANSACTION_MAX;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;
        AionTransaction tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        value.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
        RepositoryCache repo = blockchain.getRepository().startTracking();
        nonce = nonce.add(BigInteger.ONE);
        AionAddress contract =
                deployContract(repo, tx, contractName, null, value, nrg, nrgPrice, nonce, true);

        assertNotNull(contract);
        repo = blockchain.getRepository().startTracking();

        AionAddress avmAddress = deployAvmContract(AvmVersion.VERSION_1, nonce);
        assertNotNull(avmAddress);

        nonce = nonce.add(BigInteger.ONE);
        byte[] input = Arrays.copyOfRange(HashUtil.keccak256("callAVM(address)".getBytes()), 0, 4);
        input = ByteUtil.merge(input, avmAddress.toByteArray());
        tx =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        contract,
                        BigInteger.ZERO.toByteArray(),
                        input,
                        nrg,
                        nrgPrice,
                        txType, null);
        assertFalse(tx.isContractCreationTransaction());

        AionBlock block = makeBlock(tx);
        AionTxExecSummary summary = executeTransaction(tx, block, repo);
        // The evmjit only return the the transaction success or failed when performing the function
        // call.
        assertEquals("reverted", summary.getReceipt().getError());
        assertNotEquals(nrg, summary.getNrgUsed().longValue());
        Pair<ImportResult, AionBlockSummary> result = blockchain.tryToConnectAndFetchSummary(block);
        assertTrue(result.getLeft().isSuccessful());
        assertTrue(result.getRight().getSummaries().get(0).isFailed());
    }

    @Test
    public void testDeployFvmContractToAnExistedAccountBefore040Fork() throws IOException {
        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            return;
        }

        blockchain.set040ForkNumber(1000);
        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ONE;

        AionAddress destinationAddr =
                TxUtil.calculateContractAddress(deployer.toByteArray(), deployerNonce);

        // create a tx the sender send some balance to the account the deployer will deploy in the
        // feature.
        AionTransaction tx =
                AionTransaction.create(
                        senderKey,
                        senderNonce.toByteArray(),
                        destinationAddr,
                        value.toByteArray(),
                        new byte[0],
                        nrg,
                        nrgPrice,
                        txType, null);
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
                AionTransaction.create(
                        deployerKey,
                        deployerNonce.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
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
        assertEquals(1000000, summary.getReceipt().getEnergyUsed());
    }

    @Test
    public void testDeployFvmContractToAnExistedAccountWith040Fork() throws IOException {
        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            return;
        }

        blockchain.set040ForkNumber(0);
        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ONE;

        AionAddress destinationAddr =
                TxUtil.calculateContractAddress(deployer.toByteArray(), deployerNonce);

        // create a tx the sender send some balance to the account the deployer will deploy in the
        // feature.
        AionTransaction tx =
                AionTransaction.create(
                        senderKey,
                        senderNonce.toByteArray(),
                        destinationAddr,
                        value.toByteArray(),
                        new byte[0],
                        nrg,
                        nrgPrice,
                        txType, null);
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
                AionTransaction.create(
                        deployerKey,
                        deployerNonce.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        deployCode,
                        nrg,
                        nrgPrice,
                        txType, null);
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
            AionAddress contract = TxUtil.calculateContractAddress(tx);
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
        assertEquals(225787, summary.getReceipt().getEnergyUsed());
    }

    @Test
    public void testDeployAvmContractToAnExistedAccount() {
        AvmVersion version = AvmVersion.VERSION_1;

        if (txType == TransactionTypes.DEFAULT) {
            return;
        }

        long nrg = 1_000_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ONE;

        AionAddress avmAddress =
                TxUtil.calculateContractAddress(deployer.toByteArray(), deployerNonce);

        // create a tx the sender send some balance to the account the deployer will deploy in the
        // feature.
        AionTransaction tx =
                AionTransaction.create(
                        senderKey,
                        senderNonce.toByteArray(),
                        avmAddress,
                        value.toByteArray(),
                        new byte[0],
                        nrg,
                        nrgPrice,
                        TransactionTypes.DEFAULT, null);
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

        AionAddress avmDeployedAddress = deployAvmContract(version, deployerNonce);
        assertNotNull(avmAddress);
        assertEquals(avmAddress, avmDeployedAddress);

        repo = blockchain.getRepository().startTracking();
        IAvmResourceFactory factory = (version == AvmVersion.VERSION_1) ? this.resourceProvider.factoryForVersion1 : this.resourceProvider.factoryForVersion2;
        byte[] avmCode = factory.newContractFactory().getJarBytes(AvmContract.HELLO_WORLD);
        assertArrayEquals(avmCode, repo.getCode(avmAddress));

        assertEquals(BigInteger.ONE, repo.getBalance(avmAddress));

        byte[] call = getCallArguments(version);
        tx =
                AionTransaction.create(
                        deployerKey,
                        deployerNonce.add(BigInteger.ONE).toByteArray(),
                        avmAddress,
                        BigInteger.ZERO.toByteArray(),
                        call,
                        2_000_000,
                        nrgPrice,
                        TransactionTypes.DEFAULT, null);

        block =
                this.blockchain.createNewMiningBlock(
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
        //        long nrgPrice = energyPrice;
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
        //                blockchain.createNewMiningBlockContext(
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
    private AionAddress deployContract(
            RepositoryCache repo,
            AionTransaction tx,
            String contractName,
            String contractFilename,
            BigInteger value,
            long nrg,
            long nrgPrice,
            BigInteger nonce)
            throws IOException, VmFatalException {

        return deployContract(
                repo, tx, contractName, contractFilename, value, nrg, nrgPrice, nonce, false);
    }

    private AionAddress deployContract(
            RepositoryCache repo,
            AionTransaction tx,
            String contractName,
            String contractFilename,
            BigInteger value,
            long nrg,
            long nrgPrice,
            BigInteger nonce,
            boolean addToBlockChain)
            throws IOException, VmFatalException {

        assertTrue(tx.isContractCreationTransaction());

        assertEquals(deployerBalance, repo.getBalance(deployer));
        assertEquals(deployerNonce, repo.getNonce(deployer));

        AionBlock block = makeBlock(tx);
        AionTxExecSummary summary = executeTransaction(tx, block, repo);

        if (!summary.getReceipt().getError().equals("")) {
            return null;
        }
        assertNotEquals(nrg, summary.getNrgUsed().longValue());

        AionAddress contract = TxUtil.calculateContractAddress(tx);
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
            AionAddress contractAddr,
            byte[] output,
            FastVmResultCode result,
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
            AionAddress contractAddr,
            byte[] output,
            FastVmResultCode result,
            BigInteger value) throws IOException {

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
                new DataWord(Arrays.copyOfRange(rawOutput, 0, DataWord.BYTES)).intValue();
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

    private AionTxExecSummary executeTransaction(
            AionTransaction tx, Block block, RepositoryCache repo) throws VmFatalException {
        return BulkExecutor.executeTransactionWithNoPostExecutionWork(
                block.getDifficulty(),
                block.getNumber(),
                block.getTimestamp(),
                block.getNrgLimit(),
                block.getCoinbase(),
                tx,
                repo,
                false,
                true,
                true,
                false,
                LOGGER_VM,
                BlockCachingContext.PENDING,
                block.getNumber() - 1,
                false);
    }

    private AionBlock makeBlock(AionTransaction tx) {
        Block parent = blockchain.getBestBlock();
        return blockchain.createBlock(
                parent, Collections.singletonList(tx), false, parent.getTimestamp());
    }

    private byte[] getCallArguments(AvmVersion version) {
        IAvmResourceFactory factory = (version == AvmVersion.VERSION_1) ? this.resourceProvider.factoryForVersion1 : this.resourceProvider.factoryForVersion2;
        return factory.newStreamingEncoder().encodeOneString("sayHello").getEncoding();
    }

    private byte[] getJarBytes(AvmVersion version) {
        IAvmResourceFactory factory = (version == AvmVersion.VERSION_1) ? this.resourceProvider.factoryForVersion1 : this.resourceProvider.factoryForVersion2;
        return factory.newContractFactory().getDeploymentBytes(AvmContract.HELLO_WORLD);
    }

    private AionAddress deployAvmContract(AvmVersion version, BigInteger nonce) {
        byte[] jar = getJarBytes(version);
        AionTransaction transaction =
                AionTransaction.create(
                        deployerKey,
                        nonce.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        jar,
                        5_000_000L,
                        energyPrice,
                        TransactionTypes.AVM_CREATE_CODE, null);

        AionBlock block =
                this.blockchain.createNewMiningBlock(
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
        AionAddress contractAddress = TxUtil.calculateContractAddress(transaction);
        assertThat(contractAddress.toByteArray()).isEqualTo(receipt.getTransactionOutput());

        return contractAddress;
    }

    @Test
    public void testFvmEmptyContractWith200KEnrygyBeforeUnity() throws IOException, VmFatalException {
        String contractName = "EmptyContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 200_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
            AionTransaction.create(
                deployerKey,
                nonce.toByteArray(),
                null,
                value.toByteArray(),
                deployCode,
                nrg,
                nrgPrice,
                txType, null);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        AionBlock block = makeBlock(tx);
        RepositoryCache repo = blockchain.getRepository().startTracking();

        AionTxExecSummary summary =
            BulkExecutor.executeTransactionWithNoPostExecutionWork(
                block.getDifficulty(),
                block.getNumber(),
                block.getTimestamp(),
                block.getNrgLimit(),
                block.getCoinbase(),
                tx,
                repo,
                false,
                true,
                false,
                false,
                LOGGER_VM,
                BlockCachingContext.PENDING,
                block.getNumber() - 1,
                false);

        if (txType == TransactionTypes.DEFAULT) {
            assertEquals("OUT_OF_NRG", summary.getReceipt().getError());

            checkStateOfDeployer(repo, summary, nrgPrice, value, nonce.add(BigInteger.ONE));
            assertEquals(nrg, summary.getReceipt().getEnergyUsed());
        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertEquals("Failed: invalid data", summary.getReceipt().getError());
            nonce = nonce.add(BigInteger.ONE);
            checkStateOfDeployer(repo, summary, nrgPrice, value, nonce);
            assertEquals(nrg, summary.getReceipt().getEnergyUsed());
        }
    }

    @Test (expected = InvalidParameterException.class)
    public void testFvmEmptyContractWith200KEnergyAfterUnity() throws IOException {
        long unityForkBlock = 2;
        blockchain.forkUtility.enableUnityFork(unityForkBlock);
        ECKey stakingRegistryOwner = accounts.get(1);
        List<ECKey> stakers = new ArrayList<>(accounts);
        stakers.remove(deployerKey);
        stakers.remove(stakingRegistryOwner);
        assertThat(stakers.size()).isEqualTo(8);

        // the default configuration does not apply to this test case
        AvmTestConfig.clearConfigurations();
        AvmTestConfig.supportBothAvmVersions(0, unityForkBlock, 0);

        // populating the chain to be above the Unity for point
        BlockchainTestUtils.generateRandomUnityChain(blockchain, resourceProvider, unityForkBlock + 1, 1, stakers, stakingRegistryOwner, 10);

        String contractName = "EmptyContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 200_000;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
            AionTransaction.create(
                deployerKey,
                nonce.toByteArray(),
                null,
                value.toByteArray(),
                deployCode,
                nrg,
                nrgPrice,
                txType, null);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        // The transaction has invalid energylimit settings
        makeBlock(tx);
    }

    @Test
    public void testFvmEmptyContractWith226160EnergyAfterUnity() throws IOException, VmFatalException {
        long unityForkBlock = 2;
        blockchain.forkUtility.enableUnityFork(unityForkBlock);
        ECKey stakingRegistryOwner = accounts.get(1);
        List<ECKey> stakers = new ArrayList<>(accounts);
        stakers.remove(deployerKey);
        stakers.remove(stakingRegistryOwner);
        assertThat(stakers.size()).isEqualTo(8);

        // the default configuration does not apply to this test case
        AvmTestConfig.clearConfigurations();
        AvmTestConfig.supportBothAvmVersions(0, unityForkBlock, 0);

        // populating the chain to be above the Unity for point
        BlockchainTestUtils.generateRandomUnityChain(blockchain, resourceProvider, unityForkBlock + 1, 1, stakers, stakingRegistryOwner, 10);

        String contractName = "EmptyContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 226160;
        long nrgPrice = energyPrice;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx =
            AionTransaction.create(
                deployerKey,
                nonce.toByteArray(),
                null,
                value.toByteArray(),
                deployCode,
                nrg,
                nrgPrice,
                txType, null);
        assertTrue(tx.isContractCreationTransaction());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        // The transaction has invalid energylimit settings
        AionBlock block = makeBlock(tx);
        RepositoryCache repo = blockchain.getRepository().startTracking();

        AionTxExecSummary summary =
            BulkExecutor.executeTransactionWithNoPostExecutionWork(
                block.getDifficulty(),
                block.getNumber(),
                block.getTimestamp(),
                block.getNrgLimit(),
                block.getCoinbase(),
                tx,
                repo,
                false,
                true,
                false,
                false,
                LOGGER_VM,
                BlockCachingContext.PENDING,
                block.getNumber() - 1,
                false);

        if (txType == TransactionTypes.DEFAULT) {
            assertEquals("OUT_OF_NRG", summary.getReceipt().getError());
            checkStateOfDeployer(repo, summary, nrgPrice, value, nonce.add(BigInteger.ONE));
            assertEquals(nrg, summary.getReceipt().getEnergyUsed());
        } else if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertEquals("Failed: invalid data", summary.getReceipt().getError());
            nonce = nonce.add(BigInteger.ONE);
            checkStateOfDeployer(repo, summary, nrgPrice, value, nonce);
            assertEquals(nrg, summary.getReceipt().getEnergyUsed());
        }
    }
}
