package org.aion.zero.impl.blockchain;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.blockchain.AionBlockchainImpl.getPostExecutionWorkForGeneratePreBlock;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.aion.avm.stub.IContractFactory.AvmContract;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxExecSummary;
import org.aion.base.TransactionTypeRule;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.fastvm.FastVmResultCode;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.zero.impl.types.Block;
import org.aion.precompiled.ContractInfo;
import org.aion.types.AionAddress;
import org.aion.util.conversions.Hex;
import org.aion.util.types.AddressUtils;
import org.aion.zero.impl.blockchain.StandaloneBlockchain.Builder;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.vm.AvmPathManager;
import org.aion.zero.impl.vm.AvmTestConfig;
import org.aion.zero.impl.vm.TestResourceProvider;
import org.aion.zero.impl.vm.common.BlockCachingContext;
import org.aion.zero.impl.vm.common.BulkExecutor;
import org.aion.zero.impl.vm.common.VmFatalException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

/**
 * Unit tests that verify that the output of transaction rejection is as defined by the specification documents.
 *
 * @author Alexandra Roatis
 */
public class TransactionRejectionSpecificationTests {

    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());

    private static final byte[] SENDER_PRIVATE_KEY = Hex.decode("81e071e5bf2c155f641641d88b5956af52c768fbb90968979b20858d65d71f32aa935b67ac46480caaefcdd56dd31862e578694a99083e9fad88cb6df89fc7cb");
    private static final ECKey SENDER_KEY = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_PRIVATE_KEY);
    private static final AionAddress SENDER_ADDR = new AionAddress(Hex.decode("a00a4175a89a6ffbfdc45782771fba3f5e9da36baa69444f8f95e325430463e7"));
    private static final BigInteger SENDER_BALANCE = new BigInteger("1000000000000000000000000");
    private static final byte[] MINER = Hex.decode("0000000000000000000000000000000000000000000000000000000000000000");
    private static final long ENERGY_PRICE = 10_123_456_789L;

    private StandaloneBlockchain blockchain;
    private TestResourceProvider resourceProvider;

    @Before
    public void setup() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        // reduce default logging levels
        AionLoggerFactory.initAll();

        this.blockchain = new Builder().withDefaultAccounts(Collections.singletonList(SENDER_KEY)).withValidatorConfiguration("simple").withAvmEnabled().build().bc;

        // Allow AVM contract deployments.
        TransactionTypeRule.allowAVMContractTransaction();
        AvmTestConfig.supportBothAvmVersions(0, 1, 0);
        resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());
    }

    @After
    public void tearDown() {
        this.blockchain = null;
        AvmTestConfig.clearConfigurations();
    }

    @Test
    public void testBalanceTransferToPrecompiledContract_withIncorrectNonce() throws VmFatalException {
        BigInteger amount = SENDER_BALANCE.divide(BigInteger.TEN);
        BigInteger initialBalance = blockchain.getRepository().getBalance(SENDER_ADDR);
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toByteArray()).isEqualTo(MINER);

        // ensure bridge is viewed as precompiled contract
        AionAddress bridge = AddressUtils.wrapAddress("0000000000000000000000000000000000000000000000000000000000000200");
        assertThat(ContractInfo.isPrecompiledContract(bridge)).isTrue();

        // Make balance transfer transaction to precompiled contract.
        AionTransaction transaction = AionTransaction.create(SENDER_KEY, BigInteger.ONE.toByteArray(), bridge, amount.toByteArray(), new byte[] {}, 2_000_000, ENERGY_PRICE, TransactionTypes.DEFAULT, null);

        // Process the transaction.
        AionTxExecSummary result = executeTransaction(transaction);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.INVALID_NONCE.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.ZERO);
        assertThat(result.getLogs()).isEmpty();
    }

    @Test
    public void testBalanceTransferToPrecompiledContract_withInsufficientBalance() throws VmFatalException {
        BigInteger amount = SENDER_BALANCE.add(BigInteger.ONE);
        BigInteger initialBalance = blockchain.getRepository().getBalance(SENDER_ADDR);
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toByteArray()).isEqualTo(MINER);

        // ensure bridge is viewed as precompiled contract
        AionAddress bridge = AddressUtils.wrapAddress("0000000000000000000000000000000000000000000000000000000000000200");
        assertThat(ContractInfo.isPrecompiledContract(bridge)).isTrue();

        // Make balance transfer transaction to precompiled contract.
        AionTransaction transaction = AionTransaction.create(SENDER_KEY, BigInteger.ZERO.toByteArray(), bridge, amount.toByteArray(), new byte[] {}, 2_000_000, ENERGY_PRICE, TransactionTypes.DEFAULT, null);

        // Process the transaction.
        AionTxExecSummary result = executeTransaction(transaction);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.INSUFFICIENT_BALANCE.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.ZERO);
        assertThat(result.getLogs()).isEmpty();
    }

    @Test
    public void testBalanceTransferToPrecompiledContract_withInvalidEnergyLimit() throws VmFatalException {
        BigInteger amount = SENDER_BALANCE.divide(BigInteger.TEN);
        BigInteger initialBalance = blockchain.getRepository().getBalance(SENDER_ADDR);
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toByteArray()).isEqualTo(MINER);

        // ensure bridge is viewed as precompiled contract
        AionAddress bridge = AddressUtils.wrapAddress("0000000000000000000000000000000000000000000000000000000000000200");
        assertThat(ContractInfo.isPrecompiledContract(bridge)).isTrue();

        // Make balance transfer transaction to precompiled contract with large energy limit.
        AionTransaction transaction = AionTransaction.create(SENDER_KEY, BigInteger.ZERO.toByteArray(), bridge, amount.toByteArray(), new byte[] {}, 2_000_001, ENERGY_PRICE, TransactionTypes.DEFAULT, null);

        // Process the transaction.
        AionTxExecSummary result = executeTransaction(transaction);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.INVALID_NRG_LIMIT.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.ZERO);
        assertThat(result.getLogs()).isEmpty();

        // Make balance transfer transaction to precompiled contract with small energy limit.
        transaction = AionTransaction.create(SENDER_KEY, BigInteger.ZERO.toByteArray(), bridge, amount.toByteArray(), new byte[] {}, 20_999, ENERGY_PRICE, TransactionTypes.DEFAULT, null);

        // Process the transaction.
        result = executeTransaction(transaction);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.INVALID_NRG_LIMIT.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.ZERO);
        assertThat(result.getLogs()).isEmpty();
    }

    @Test
    public void testBalanceTransferToFvmContract_withIncorrectNonce() throws VmFatalException, IOException {
        BigInteger amount = SENDER_BALANCE.divide(BigInteger.TEN);
        BigInteger initialBalance = blockchain.getRepository().getBalance(SENDER_ADDR);
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toByteArray()).isEqualTo(MINER);

        // Deploy an FVM contract.
        AionTransaction deploy = BlockchainTestUtils.deployFvmTickerContractTransaction(SENDER_KEY, BigInteger.ZERO);
        ImportResult importResult = BlockchainTestUtils.addMiningBlock(this.blockchain, blockchain.getBestBlock(), List.of(deploy)).getRight();
        assertThat(importResult).isEqualTo(ImportResult.IMPORTED_BEST);

        AionAddress contract = TxUtil.calculateContractAddress(deploy);
        assertThat(blockchain.getRepository().hasAccountState(contract)).isTrue();

        // Make balance transfer transaction to deployed contract.
        AionTransaction transaction = AionTransaction.create(SENDER_KEY, BigInteger.TWO.toByteArray(), contract, amount.toByteArray(), new byte[] {}, 2_000_000, ENERGY_PRICE, TransactionTypes.DEFAULT, null);

        // Process the transaction.
        AionTxExecSummary result = executeTransaction(transaction);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.INVALID_NONCE.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.ZERO);
        assertThat(result.getLogs()).isEmpty();
    }

    @Test
    public void testBalanceTransferToFvmContract_withInsufficientBalance() throws VmFatalException, IOException {
        BigInteger amount = SENDER_BALANCE.add(BigInteger.ONE);
        BigInteger initialBalance = blockchain.getRepository().getBalance(SENDER_ADDR);
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toByteArray()).isEqualTo(MINER);

        // Deploy an FVM contract.
        AionTransaction deploy = BlockchainTestUtils.deployFvmTickerContractTransaction(SENDER_KEY, BigInteger.ZERO);
        ImportResult importResult = BlockchainTestUtils.addMiningBlock(this.blockchain, blockchain.getBestBlock(), List.of(deploy)).getRight();
        assertThat(importResult).isEqualTo(ImportResult.IMPORTED_BEST);

        AionAddress contract = TxUtil.calculateContractAddress(deploy);
        assertThat(blockchain.getRepository().hasAccountState(contract)).isTrue();

        // Make balance transfer transaction to deployed contract.
        AionTransaction transaction = AionTransaction.create(SENDER_KEY, BigInteger.ONE.toByteArray(), contract, amount.toByteArray(), new byte[] {}, 2_000_000, ENERGY_PRICE, TransactionTypes.DEFAULT, null);

        // Process the transaction.
        AionTxExecSummary result = executeTransaction(transaction);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.INSUFFICIENT_BALANCE.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.ZERO);
        assertThat(result.getLogs()).isEmpty();
    }

    @Test
    public void testBalanceTransferToFvmContract_withInvalidEnergyLimit() throws VmFatalException, IOException {
        BigInteger amount = SENDER_BALANCE.divide(BigInteger.TEN);
        BigInteger initialBalance = blockchain.getRepository().getBalance(SENDER_ADDR);
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toByteArray()).isEqualTo(MINER);

        // Deploy an FVM contract.
        AionTransaction deploy = BlockchainTestUtils.deployFvmTickerContractTransaction(SENDER_KEY, BigInteger.ZERO);
        ImportResult importResult = BlockchainTestUtils.addMiningBlock(this.blockchain, blockchain.getBestBlock(), List.of(deploy)).getRight();
        assertThat(importResult).isEqualTo(ImportResult.IMPORTED_BEST);

        AionAddress contract = TxUtil.calculateContractAddress(deploy);
        assertThat(blockchain.getRepository().hasAccountState(contract)).isTrue();

        // Make balance transfer transaction to deployed contract with large energy limit.
        AionTransaction transaction = AionTransaction.create(SENDER_KEY, BigInteger.ONE.toByteArray(), contract, amount.toByteArray(), new byte[] {}, 2_000_001, ENERGY_PRICE, TransactionTypes.DEFAULT, null);

        // Process the transaction.
        AionTxExecSummary result = executeTransaction(transaction);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.INVALID_NRG_LIMIT.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.ZERO);
        assertThat(result.getLogs()).isEmpty();

        // Make balance transfer transaction to deployed contract with small energy limit.
        transaction = AionTransaction.create(SENDER_KEY, BigInteger.ONE.toByteArray(), contract, amount.toByteArray(), new byte[] {}, 20_999, ENERGY_PRICE, TransactionTypes.DEFAULT, null);

        // Process the transaction.
        result = executeTransaction(transaction);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.INVALID_NRG_LIMIT.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.ZERO);
        assertThat(result.getLogs()).isEmpty();
    }

    @Test
    public void testDeployFvmContract_withInvalidEnergyLimit() throws VmFatalException, IOException {
        BigInteger initialBalance = blockchain.getRepository().getBalance(SENDER_ADDR);
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toByteArray()).isEqualTo(MINER);

        // Deploy an FVM contract.
        AionTransaction deploy = BlockchainTestUtils.deployFvmTickerContractTransaction(SENDER_KEY, BigInteger.ZERO);
        ImportResult importResult = BlockchainTestUtils.addMiningBlock(this.blockchain, blockchain.getBestBlock(), List.of(deploy)).getRight();
        assertThat(importResult).isEqualTo(ImportResult.IMPORTED_BEST);

        AionAddress contract = TxUtil.calculateContractAddress(deploy);
        assertThat(blockchain.getRepository().hasAccountState(contract)).isTrue();

        // Make balance transfer transaction to deployed contract with large energy limit.
        AionTransaction transaction = AionTransaction.create(SENDER_KEY, BigInteger.ONE.toByteArray(), null, BigInteger.ZERO.toByteArray(), deploy.getData(), 5_000_001, ENERGY_PRICE, TransactionTypes.DEFAULT, null);

        // Process the transaction.
        AionTxExecSummary result = executeTransaction(transaction);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.INVALID_NRG_LIMIT.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.ZERO);
        assertThat(result.getLogs()).isEmpty();

        // Make balance transfer transaction to deployed contract with small energy limit.
        transaction = AionTransaction.create(SENDER_KEY, BigInteger.ONE.toByteArray(), null, BigInteger.ZERO.toByteArray(), deploy.getData(), 199_999, ENERGY_PRICE, TransactionTypes.DEFAULT, null);

        // Process the transaction.
        result = executeTransaction(transaction);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.INVALID_NRG_LIMIT.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.ZERO);
        assertThat(result.getLogs()).isEmpty();
    }

    @Test
    public void testBalanceTransferToAvmContract_withIncorrectNonce() throws VmFatalException {
        BigInteger amount = SENDER_BALANCE.divide(BigInteger.TEN);
        BigInteger initialBalance = blockchain.getRepository().getBalance(SENDER_ADDR);
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toByteArray()).isEqualTo(MINER);

        // Deploy an AVM contract.
        AionTransaction deploy = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.HELLO_WORLD, resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ZERO);
        ImportResult importResult = BlockchainTestUtils.addMiningBlock(this.blockchain, blockchain.getBestBlock(), List.of(deploy)).getRight();
        assertThat(importResult).isEqualTo(ImportResult.IMPORTED_BEST);

        AionAddress contract = TxUtil.calculateContractAddress(deploy);
        assertThat(blockchain.getRepository().hasAccountState(contract)).isTrue();

        // Make balance transfer transaction to deployed contract.
        AionTransaction transaction = AionTransaction.create(SENDER_KEY, BigInteger.TWO.toByteArray(), contract, amount.toByteArray(), new byte[] {}, 2_000_000, ENERGY_PRICE, TransactionTypes.DEFAULT, null);

        // Process the transaction.
        AionTxExecSummary result = executeTransaction(transaction);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo("Rejected: invalid nonce");
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.ZERO);
        assertThat(result.getLogs()).isEmpty();
    }

    @Test
    public void testBalanceTransferToAvmContract_withInsufficientBalance() throws VmFatalException {
        BigInteger amount = SENDER_BALANCE.add(BigInteger.ONE);
        BigInteger initialBalance = blockchain.getRepository().getBalance(SENDER_ADDR);
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toByteArray()).isEqualTo(MINER);

        // Deploy an AVM contract.
        AionTransaction deploy = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.HELLO_WORLD, resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ZERO);
        ImportResult importResult = BlockchainTestUtils.addMiningBlock(this.blockchain, blockchain.getBestBlock(), List.of(deploy)).getRight();
        assertThat(importResult).isEqualTo(ImportResult.IMPORTED_BEST);

        AionAddress contract = TxUtil.calculateContractAddress(deploy);
        assertThat(blockchain.getRepository().hasAccountState(contract)).isTrue();

        // Make balance transfer transaction to deployed contract.
        AionTransaction transaction = AionTransaction.create(SENDER_KEY, BigInteger.ONE.toByteArray(), contract, amount.toByteArray(), new byte[] {}, 2_000_000, ENERGY_PRICE, TransactionTypes.DEFAULT, null);

        // Process the transaction.
        AionTxExecSummary result = executeTransaction(transaction);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo("Rejected: insufficient balance");
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.ZERO);
        assertThat(result.getLogs()).isEmpty();
    }

    @Test
    public void testBalanceTransferToAvmContract_withInvalidEnergyLimit() throws VmFatalException {
        BigInteger amount = SENDER_BALANCE.divide(BigInteger.TEN);
        BigInteger initialBalance = blockchain.getRepository().getBalance(SENDER_ADDR);
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toByteArray()).isEqualTo(MINER);

        // Deploy an AVM contract.
        AionTransaction deploy = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.HELLO_WORLD, resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ZERO);
        ImportResult importResult = BlockchainTestUtils.addMiningBlock(this.blockchain, blockchain.getBestBlock(), List.of(deploy)).getRight();
        assertThat(importResult).isEqualTo(ImportResult.IMPORTED_BEST);

        AionAddress contract = TxUtil.calculateContractAddress(deploy);
        assertThat(blockchain.getRepository().hasAccountState(contract)).isTrue();

        // Make balance transfer transaction to deployed contract with large energy limit.
        AionTransaction transaction = AionTransaction.create(SENDER_KEY, BigInteger.ONE.toByteArray(), contract, amount.toByteArray(), new byte[] {}, 2_000_001, ENERGY_PRICE, TransactionTypes.DEFAULT, null);

        // Process the transaction.
        AionTxExecSummary result = executeTransaction(transaction);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo("Rejected: invalid energy limit");
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.ZERO);
        assertThat(result.getLogs()).isEmpty();

        // Make balance transfer transaction to deployed contract with small energy limit.
        transaction = AionTransaction.create(SENDER_KEY, BigInteger.ONE.toByteArray(), contract, amount.toByteArray(), new byte[] {}, 20_999, ENERGY_PRICE, TransactionTypes.DEFAULT, null);

        // Process the transaction.
        result = executeTransaction(transaction);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo("Rejected: invalid energy limit");
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.ZERO);
        assertThat(result.getLogs()).isEmpty();
    }

    @Test
    public void testDeployAvmContract_withInvalidEnergyLimit() throws VmFatalException, IOException {
        BigInteger initialBalance = blockchain.getRepository().getBalance(SENDER_ADDR);
        assertThat(initialBalance).isEqualTo(SENDER_BALANCE);
        assertThat(this.blockchain.getMinerCoinbase().toByteArray()).isEqualTo(MINER);

        // Deploy an AVM contract.
        AionTransaction deploy = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.HELLO_WORLD, resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ZERO);
        ImportResult importResult = BlockchainTestUtils.addMiningBlock(this.blockchain, blockchain.getBestBlock(), List.of(deploy)).getRight();
        assertThat(importResult).isEqualTo(ImportResult.IMPORTED_BEST);

        AionAddress contract = TxUtil.calculateContractAddress(deploy);
        assertThat(blockchain.getRepository().hasAccountState(contract)).isTrue();

        // Make balance transfer transaction to deployed contract with large energy limit.
        AionTransaction transaction = AionTransaction.create(SENDER_KEY, BigInteger.ONE.toByteArray(), null, BigInteger.ZERO.toByteArray(), deploy.getData(), 5_000_001, ENERGY_PRICE, TransactionTypes.AVM_CREATE_CODE, null);

        // Process the transaction.
        AionTxExecSummary result = executeTransaction(transaction);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo("Rejected: invalid energy limit");
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.ZERO);
        assertThat(result.getLogs()).isEmpty();

        // Make balance transfer transaction to deployed contract with small energy limit.
        transaction = AionTransaction.create(SENDER_KEY, BigInteger.ONE.toByteArray(), null, BigInteger.ZERO.toByteArray(), deploy.getData(), 199_999, ENERGY_PRICE, TransactionTypes.AVM_CREATE_CODE, null);

        // Process the transaction.
        result = executeTransaction(transaction);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo("Rejected: invalid energy limit");
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.ZERO);
        assertThat(result.getLogs()).isEmpty();
    }

    private AionTxExecSummary executeTransaction(AionTransaction transaction) throws VmFatalException {
        // Create a next block as context for the execution.
        // Do not include the transaction in the block since it is expected to fail.
        Block block = BlockchainTestUtils.generateNextMiningBlock(blockchain, blockchain.getBestBlock(), Collections.emptyList());

        // TODO: refactor AionBlockchainImpl to contain a method for this call
        List<AionTxExecSummary> list = BulkExecutor.executeAllTransactionsInBlock(
                block.getDifficulty(),
                block.getNumber() + 1,
                block.getTimestamp(),
                block.getNrgLimit(),
                block.getCoinbase(),
                List.of(transaction),
                this.blockchain.getRepository().startTracking(),
                false,
                true,
                false,
                true,
                LOGGER_VM,
                getPostExecutionWorkForGeneratePreBlock(blockchain.getRepository()),
                BlockCachingContext.PENDING,
                block.getNumber(),
                false,
                false);

        return list.get(0);
    }
}
