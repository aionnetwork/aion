package org.aion.zero.impl.blockchain;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.base.ConstantUtil.EMPTY_TRIE_HASH;
import static org.aion.crypto.HashUtil.EMPTY_DATA_HASH;
import static org.aion.zero.impl.blockchain.AionBlockchainImpl.getPostExecutionWorkForGeneratePreBlock;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.aion.avm.stub.IContractFactory.AvmContract;
import org.aion.base.AccountState;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxExecSummary;
import org.aion.base.TransactionTypeRule;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.fastvm.FastVmResultCode;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.base.InternalVmType;
import org.aion.mcf.db.RepositoryCache;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.blockchain.StandaloneBlockchain.Builder;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.vm.AvmPathManager;
import org.aion.zero.impl.vm.AvmTestConfig;
import org.aion.zero.impl.vm.TestResourceProvider;
import org.aion.zero.impl.vm.common.BlockCachingContext;
import org.aion.zero.impl.vm.common.BulkExecutor;
import org.aion.zero.impl.vm.common.VmFatalException;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

/**
 * Unit tests that verify the expected behaviour for deployments on accounts with non-default state.
 *
 * @author Alexandra Roatis
 */
public class TransactionCreateSpecificationTests {

    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());

    private static final byte[] SENDER_PRIVATE_KEY = Hex.decode("81e071e5bf2c155f641641d88b5956af52c768fbb90968979b20858d65d71f32aa935b67ac46480caaefcdd56dd31862e578694a99083e9fad88cb6df89fc7cb");
    private static final ECKey SENDER_KEY = org.aion.crypto.ECKeyFac.inst().fromPrivate(SENDER_PRIVATE_KEY);
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
    public void deployAvmContractOnTopOfAddressWithBalanceUsingAvmVersion1() throws VmFatalException {
        AvmTestConfig.clearConfigurations();
        AvmTestConfig.supportOnlyAvmVersion1();
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.HELLO_WORLD, resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        // Manipulate the repository to have a non-default balance value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(contract);
        cache.addBalance(contract, BigInteger.TEN);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(contract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployTxAvm, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployTxAvm.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(contract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployAvmContractOnTopOfAddressWithNonceUsingAvmVersion1() throws VmFatalException {
        AvmTestConfig.clearConfigurations();
        AvmTestConfig.supportOnlyAvmVersion1();
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.HELLO_WORLD, resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        // Manipulate the repository to have a non-default nonce value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(contract);
        cache.setNonce(contract, BigInteger.TEN);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(contract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployTxAvm, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployTxAvm.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(contract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployAvmContractOnTopOfAddressWithStorageUsingAvmVersion1() throws VmFatalException {
        AvmTestConfig.clearConfigurations();
        AvmTestConfig.supportOnlyAvmVersion1();
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.HELLO_WORLD, resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        // Manipulate the repository to have a non-default storage value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.addStorageRow(contract, ByteArrayWrapper.wrap(RandomUtils.nextBytes(16)), ByteArrayWrapper.wrap(RandomUtils.nextBytes(16)));
        cache.saveVmType(contract, InternalVmType.AVM);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(contract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
        byte[] oldRoot = contractState.getStateRoot();

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployTxAvm, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployTxAvm.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(contract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getStateRoot()).isNotEqualTo(oldRoot);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployAvmContractOnTopOfAddressWithCodeUsingAvmVersion1() throws VmFatalException {
        AvmTestConfig.clearConfigurations();
        AvmTestConfig.supportOnlyAvmVersion1();
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.HELLO_WORLD, resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        // Manipulate the repository to have a non-default code value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(contract);
        cache.saveCode(contract, new byte[] {1, 2, 3, 4});
        cache.saveVmType(contract, InternalVmType.AVM);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(contract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
        byte[] oldRoot = contractState.getStateRoot();
        byte[] oldCode = contractState.getCodeHash();

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployTxAvm, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployTxAvm.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(contract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getStateRoot()).isNotEqualTo(oldRoot);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
        // Odd behaviour: the code is not overwritten. Not a problem since AVM Version 1 is no longer used for deployments.
        assertThat(contractState.getCodeHash()).isEqualTo(oldCode);
    }

    @Test
    public void deployAvmContractOnTopOfAddressWithBalanceUsingAvmVersion2() throws VmFatalException {
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.HELLO_WORLD, resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        // Manipulate the repository to have a non-default balance value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(contract);
        cache.addBalance(contract, BigInteger.TEN);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(contract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployTxAvm, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployTxAvm.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(contract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployAvmContractOnTopOfAddressWithNonceUsingAvmVersion2() throws VmFatalException {
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.HELLO_WORLD, resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        // Manipulate the repository to have a non-default nonce value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(contract);
        cache.setNonce(contract, BigInteger.TEN);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(contract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployTxAvm, true);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo("Failed: destination address has a non-default state");
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.valueOf(deployTxAvm.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(contract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployAvmContractOnTopOfAddressWithStorageUsingAvmVersion2() throws VmFatalException {
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.HELLO_WORLD, resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        // Manipulate the repository to have a non-default storage value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.addStorageRow(contract, ByteArrayWrapper.wrap(RandomUtils.nextBytes(16)), ByteArrayWrapper.wrap(RandomUtils.nextBytes(16)));
        cache.saveVmType(contract, InternalVmType.AVM);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(contract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
        byte[] oldRoot = contractState.getStateRoot();

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployTxAvm, true);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo("Failed: destination address has a non-default state");
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.valueOf(deployTxAvm.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(contract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(oldRoot);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployAvmContractOnTopOfAddressWithCodeUsingAvmVersion2() throws VmFatalException {
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.HELLO_WORLD, resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        // Manipulate the repository to have a non-default code value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(contract);
        cache.saveCode(contract, new byte[] {1, 2, 3, 4});
        cache.saveVmType(contract, InternalVmType.AVM);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(contract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
        byte[] oldRoot = contractState.getStateRoot();
        byte[] oldCode = contractState.getCodeHash();

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployTxAvm, true);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo("Failed: destination address has a non-default state");
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.valueOf(deployTxAvm.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(contract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(oldRoot);
        assertThat(contractState.getCodeHash()).isEqualTo(oldCode);
    }

    @Test
    public void deployInternalAvmContractOnTopOfAddressWithBalanceUsingAvmVersion1_DeployAndRequireSuccess() throws VmFatalException {
        AvmTestConfig.clearConfigurations();
        AvmTestConfig.supportOnlyAvmVersion1();
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.DEPLOY_INTERNAL, resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        Pair<Block, ImportResult> resultImport = BlockchainTestUtils.addMiningBlock(blockchain, blockchain.getBestBlock(), List.of(deployTxAvm));
        assertThat(resultImport.getRight()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Call AVM contract to deploy new internal AVM contract (version with required success).
        long internalLimit = 1_000_000;
        AionTransaction deployInternal = BlockchainTestUtils.callSimpleAvmContractTransaction(resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ONE, contract, "deployAndRequireSuccess", deployTxAvm.getData(), internalLimit);
        AionAddress internalContract = new AionAddress(Hex.decode("a0268090998a99666b72cc452b9307438a34341047d9e0d7b92c9207bf413655"));
        assertThat(blockchain.getRepository().hasAccountState(internalContract)).isFalse();

        // Manipulate the repository to have a non-default balance value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(internalContract);
        cache.addBalance(internalContract, BigInteger.TEN);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployInternal, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployInternal.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        InternalTransaction itx = result.getInternalTransactions().get(0);
        assertThat(itx.isCreate).isTrue();
        assertThat(TxUtil.calculateContractAddress(itx)).isEqualTo(internalContract);
        assertThat(itx.isRejected).isFalse();
        assertThat(itx.energyLimit).isEqualTo(internalLimit);
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(itx.energyLimit));

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployInternalAvmContractOnTopOfAddressWithBalanceUsingAvmVersion1() throws VmFatalException {
        AvmTestConfig.clearConfigurations();
        AvmTestConfig.supportOnlyAvmVersion1();
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.DEPLOY_INTERNAL, resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        Pair<Block, ImportResult> resultImport = BlockchainTestUtils.addMiningBlock(blockchain, blockchain.getBestBlock(), List.of(deployTxAvm));
        assertThat(resultImport.getRight()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Call AVM contract to deploy new internal AVM contract (version without required success).
        long internalLimit = 1_000_000;
        AionTransaction deployInternal = BlockchainTestUtils.callSimpleAvmContractTransaction(resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ONE, contract, "deploy", deployTxAvm.getData(), internalLimit);
        AionAddress internalContract = new AionAddress(Hex.decode("a0268090998a99666b72cc452b9307438a34341047d9e0d7b92c9207bf413655"));
        assertThat(blockchain.getRepository().hasAccountState(internalContract)).isFalse();

        // Manipulate the repository to have a non-default balance value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(internalContract);
        cache.addBalance(internalContract, BigInteger.TEN);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployInternal, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployInternal.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        InternalTransaction itx = result.getInternalTransactions().get(0);
        assertThat(itx.isCreate).isTrue();
        assertThat(TxUtil.calculateContractAddress(itx)).isEqualTo(internalContract);
        assertThat(itx.isRejected).isFalse();
        assertThat(itx.energyLimit).isEqualTo(internalLimit);
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(itx.energyLimit));

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployInternalAvmContractOnTopOfAddressWithNonceUsingAvmVersion1_DeployAndRequireSuccess() throws VmFatalException {
        AvmTestConfig.clearConfigurations();
        AvmTestConfig.supportOnlyAvmVersion1();
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.DEPLOY_INTERNAL, resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        Pair<Block, ImportResult> resultImport = BlockchainTestUtils.addMiningBlock(blockchain, blockchain.getBestBlock(), List.of(deployTxAvm));
        assertThat(resultImport.getRight()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Call AVM contract to deploy new internal AVM contract (version with required success).
        long internalLimit = 1_000_000;
        AionTransaction deployInternal = BlockchainTestUtils.callSimpleAvmContractTransaction(resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ONE, contract, "deployAndRequireSuccess", deployTxAvm.getData(), internalLimit);
        AionAddress internalContract = new AionAddress(Hex.decode("a0268090998a99666b72cc452b9307438a34341047d9e0d7b92c9207bf413655"));
        assertThat(blockchain.getRepository().hasAccountState(internalContract)).isFalse();

        // Manipulate the repository to have a non-default nonce value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(internalContract);
        cache.setNonce(internalContract, BigInteger.TEN);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployInternal, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployInternal.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        InternalTransaction itx = result.getInternalTransactions().get(0);
        assertThat(itx.isCreate).isTrue();
        assertThat(TxUtil.calculateContractAddress(itx)).isEqualTo(internalContract);
        assertThat(itx.isRejected).isFalse();
        assertThat(itx.energyLimit).isEqualTo(internalLimit);
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(itx.energyLimit));

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployInternalAvmContractOnTopOfAddressWithNonceUsingAvmVersion1() throws VmFatalException {
        AvmTestConfig.clearConfigurations();
        AvmTestConfig.supportOnlyAvmVersion1();
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.DEPLOY_INTERNAL, resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        Pair<Block, ImportResult> resultImport = BlockchainTestUtils.addMiningBlock(blockchain, blockchain.getBestBlock(), List.of(deployTxAvm));
        assertThat(resultImport.getRight()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Call AVM contract to deploy new internal AVM contract (version without required success).
        long internalLimit = 1_000_000;
        AionTransaction deployInternal = BlockchainTestUtils.callSimpleAvmContractTransaction(resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ONE, contract, "deploy", deployTxAvm.getData(), internalLimit);
        AionAddress internalContract = new AionAddress(Hex.decode("a0268090998a99666b72cc452b9307438a34341047d9e0d7b92c9207bf413655"));
        assertThat(blockchain.getRepository().hasAccountState(internalContract)).isFalse();

        // Manipulate the repository to have a non-default nonce value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(internalContract);
        cache.setNonce(internalContract, BigInteger.TEN);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployInternal, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployInternal.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        InternalTransaction itx = result.getInternalTransactions().get(0);
        assertThat(itx.isCreate).isTrue();
        assertThat(TxUtil.calculateContractAddress(itx)).isEqualTo(internalContract);
        assertThat(itx.isRejected).isFalse();
        assertThat(itx.energyLimit).isEqualTo(internalLimit);
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(itx.energyLimit));

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployInternalAvmContractOnTopOfAddressWithStorageUsingAvmVersion1_DeployAndRequireSuccess() throws VmFatalException {
        AvmTestConfig.clearConfigurations();
        AvmTestConfig.supportOnlyAvmVersion1();
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.DEPLOY_INTERNAL, resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        Pair<Block, ImportResult> resultImport = BlockchainTestUtils.addMiningBlock(blockchain, blockchain.getBestBlock(), List.of(deployTxAvm));
        assertThat(resultImport.getRight()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Call AVM contract to deploy new internal AVM contract (version with required success).
        long internalLimit = 1_000_000;
        AionTransaction deployInternal = BlockchainTestUtils.callSimpleAvmContractTransaction(resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ONE, contract, "deployAndRequireSuccess", deployTxAvm.getData(), internalLimit);
        AionAddress internalContract = new AionAddress(Hex.decode("a0268090998a99666b72cc452b9307438a34341047d9e0d7b92c9207bf413655"));
        assertThat(blockchain.getRepository().hasAccountState(internalContract)).isFalse();

        // Manipulate the repository to have a non-default storage value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.addStorageRow(internalContract, ByteArrayWrapper.wrap(RandomUtils.nextBytes(16)), ByteArrayWrapper.wrap(RandomUtils.nextBytes(16)));
        cache.saveVmType(internalContract, InternalVmType.AVM);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
        byte[] oldRoot = contractState.getStateRoot();

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployInternal, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployInternal.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        InternalTransaction itx = result.getInternalTransactions().get(0);
        assertThat(itx.isCreate).isTrue();
        assertThat(TxUtil.calculateContractAddress(itx)).isEqualTo(internalContract);
        assertThat(itx.isRejected).isFalse();
        assertThat(itx.energyLimit).isEqualTo(internalLimit);
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(itx.energyLimit));

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getStateRoot()).isNotEqualTo(oldRoot);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployInternalAvmContractOnTopOfAddressWithStorageUsingAvmVersion1() throws VmFatalException {
        AvmTestConfig.clearConfigurations();
        AvmTestConfig.supportOnlyAvmVersion1();
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.DEPLOY_INTERNAL, resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        Pair<Block, ImportResult> resultImport = BlockchainTestUtils.addMiningBlock(blockchain, blockchain.getBestBlock(), List.of(deployTxAvm));
        assertThat(resultImport.getRight()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Call AVM contract to deploy new internal AVM contract (version without required success).
        long internalLimit = 1_000_000;
        AionTransaction deployInternal = BlockchainTestUtils.callSimpleAvmContractTransaction(resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ONE, contract, "deploy", deployTxAvm.getData(), internalLimit);
        AionAddress internalContract = new AionAddress(Hex.decode("a0268090998a99666b72cc452b9307438a34341047d9e0d7b92c9207bf413655"));
        assertThat(blockchain.getRepository().hasAccountState(internalContract)).isFalse();

        // Manipulate the repository to have a non-default storage value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.addStorageRow(internalContract, ByteArrayWrapper.wrap(RandomUtils.nextBytes(16)), ByteArrayWrapper.wrap(RandomUtils.nextBytes(16)));
        cache.saveVmType(internalContract, InternalVmType.AVM);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
        byte[] oldRoot = contractState.getStateRoot();

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployInternal, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployInternal.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        InternalTransaction itx = result.getInternalTransactions().get(0);
        assertThat(itx.isCreate).isTrue();
        assertThat(TxUtil.calculateContractAddress(itx)).isEqualTo(internalContract);
        assertThat(itx.isRejected).isFalse();
        assertThat(itx.energyLimit).isEqualTo(internalLimit);
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(itx.energyLimit));

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getStateRoot()).isNotEqualTo(oldRoot);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployInternalAvmContractOnTopOfAddressWithCodeUsingAvmVersion1_DeployAndRequireSuccess() throws VmFatalException {
        AvmTestConfig.clearConfigurations();
        AvmTestConfig.supportOnlyAvmVersion1();
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.DEPLOY_INTERNAL, resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        Pair<Block, ImportResult> resultImport = BlockchainTestUtils.addMiningBlock(blockchain, blockchain.getBestBlock(), List.of(deployTxAvm));
        assertThat(resultImport.getRight()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Call AVM contract to deploy new internal AVM contract (version with required success).
        long internalLimit = 1_000_000;
        AionTransaction deployInternal = BlockchainTestUtils.callSimpleAvmContractTransaction(resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ONE, contract, "deployAndRequireSuccess", deployTxAvm.getData(), internalLimit);
        AionAddress internalContract = new AionAddress(Hex.decode("a0268090998a99666b72cc452b9307438a34341047d9e0d7b92c9207bf413655"));
        assertThat(blockchain.getRepository().hasAccountState(internalContract)).isFalse();

        // Manipulate the repository to have a non-default code value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(internalContract);
        cache.saveCode(internalContract, new byte[] {1, 2, 3, 4});
        cache.saveVmType(internalContract, InternalVmType.AVM);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
        byte[] oldRoot = contractState.getStateRoot();
        byte[] oldCode = contractState.getCodeHash();

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployInternal, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployInternal.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        InternalTransaction itx = result.getInternalTransactions().get(0);
        assertThat(itx.isCreate).isTrue();
        assertThat(TxUtil.calculateContractAddress(itx)).isEqualTo(internalContract);
        assertThat(itx.isRejected).isFalse();
        assertThat(itx.energyLimit).isEqualTo(internalLimit);
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(itx.energyLimit));

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getStateRoot()).isNotEqualTo(oldRoot);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
        // Odd behaviour: the code is not overwritten. Not a problem since AVM Version 1 is no longer used for deployments.
        assertThat(contractState.getCodeHash()).isEqualTo(oldCode);
    }

    @Test
    public void deployInternalAvmContractOnTopOfAddressWithCodeUsingAvmVersion1() throws VmFatalException {
        AvmTestConfig.clearConfigurations();
        AvmTestConfig.supportOnlyAvmVersion1();
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.DEPLOY_INTERNAL, resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        Pair<Block, ImportResult> resultImport = BlockchainTestUtils.addMiningBlock(blockchain, blockchain.getBestBlock(), List.of(deployTxAvm));
        assertThat(resultImport.getRight()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Call AVM contract to deploy new internal AVM contract (version without required success).
        long internalLimit = 1_000_000;
        AionTransaction deployInternal = BlockchainTestUtils.callSimpleAvmContractTransaction(resourceProvider.factoryForVersion1, SENDER_KEY, BigInteger.ONE, contract, "deploy", deployTxAvm.getData(), internalLimit);
        AionAddress internalContract = new AionAddress(Hex.decode("a0268090998a99666b72cc452b9307438a34341047d9e0d7b92c9207bf413655"));
        assertThat(blockchain.getRepository().hasAccountState(internalContract)).isFalse();

        // Manipulate the repository to have a non-default code value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(internalContract);
        cache.saveCode(internalContract, new byte[] {1, 2, 3, 4});
        cache.saveVmType(internalContract, InternalVmType.AVM);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
        byte[] oldRoot = contractState.getStateRoot();
        byte[] oldCode = contractState.getCodeHash();

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployInternal, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployInternal.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        InternalTransaction itx = result.getInternalTransactions().get(0);
        assertThat(itx.isCreate).isTrue();
        assertThat(TxUtil.calculateContractAddress(itx)).isEqualTo(internalContract);
        assertThat(itx.isRejected).isFalse();
        assertThat(itx.energyLimit).isEqualTo(internalLimit);
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(itx.energyLimit));

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getStateRoot()).isNotEqualTo(oldRoot);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
        // Odd behaviour: the code is not overwritten. Not a problem since AVM Version 1 is no longer used for deployments.
        assertThat(contractState.getCodeHash()).isEqualTo(oldCode);
    }

    @Test
    public void deployInternalAvmContractOnTopOfAddressWithBalanceUsingAvmVersion2_DeployAndRequireSuccess() throws VmFatalException {
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.DEPLOY_INTERNAL, resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        Pair<Block, ImportResult> resultImport = BlockchainTestUtils.addMiningBlock(blockchain, blockchain.getBestBlock(), List.of(deployTxAvm));
        assertThat(resultImport.getRight()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Call AVM contract to deploy new internal AVM contract (version with required success).
        long internalLimit = 1_000_000;
        AionTransaction deployInternal = BlockchainTestUtils.callSimpleAvmContractTransaction(resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ONE, contract, "deployAndRequireSuccess", deployTxAvm.getData(), internalLimit);
        AionAddress internalContract = new AionAddress(Hex.decode("a0268090998a99666b72cc452b9307438a34341047d9e0d7b92c9207bf413655"));
        assertThat(blockchain.getRepository().hasAccountState(internalContract)).isFalse();

        // Manipulate the repository to have a non-default balance value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(internalContract);
        cache.addBalance(internalContract, BigInteger.TEN);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployInternal, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployInternal.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        InternalTransaction itx = result.getInternalTransactions().get(0);
        assertThat(itx.isCreate).isTrue();
        assertThat(TxUtil.calculateContractAddress(itx)).isEqualTo(internalContract);
        assertThat(itx.isRejected).isFalse();
        assertThat(itx.energyLimit).isEqualTo(internalLimit);
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(itx.energyLimit));

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployInternalAvmContractOnTopOfAddressWithBalanceUsingAvmVersion2() throws VmFatalException {
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.DEPLOY_INTERNAL, resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        Pair<Block, ImportResult> resultImport = BlockchainTestUtils.addMiningBlock(blockchain, blockchain.getBestBlock(), List.of(deployTxAvm));
        assertThat(resultImport.getRight()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Call AVM contract to deploy new internal AVM contract (version without required success).
        long internalLimit = 1_000_000;
        AionTransaction deployInternal = BlockchainTestUtils.callSimpleAvmContractTransaction(resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ONE, contract, "deploy", deployTxAvm.getData(), internalLimit);
        AionAddress internalContract = new AionAddress(Hex.decode("a0268090998a99666b72cc452b9307438a34341047d9e0d7b92c9207bf413655"));
        assertThat(blockchain.getRepository().hasAccountState(internalContract)).isFalse();

        // Manipulate the repository to have a non-default balance value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(internalContract);
        cache.addBalance(internalContract, BigInteger.TEN);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployInternal, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployInternal.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        InternalTransaction itx = result.getInternalTransactions().get(0);
        assertThat(itx.isCreate).isTrue();
        assertThat(TxUtil.calculateContractAddress(itx)).isEqualTo(internalContract);
        assertThat(itx.isRejected).isFalse();
        assertThat(itx.energyLimit).isEqualTo(internalLimit);
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(itx.energyLimit));

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployInternalAvmContractOnTopOfAddressWithNonceUsingAvmVersion2_DeployAndRequireSuccess() throws VmFatalException {
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.DEPLOY_INTERNAL, resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        Pair<Block, ImportResult> resultImport = BlockchainTestUtils.addMiningBlock(blockchain, blockchain.getBestBlock(), List.of(deployTxAvm));
        assertThat(resultImport.getRight()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Call AVM contract to deploy new internal AVM contract (version with required success).
        long internalLimit = 1_000_000;
        AionTransaction deployInternal = BlockchainTestUtils.callSimpleAvmContractTransaction(resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ONE, contract, "deployAndRequireSuccess", deployTxAvm.getData(), internalLimit);
        AionAddress internalContract = new AionAddress(Hex.decode("a0268090998a99666b72cc452b9307438a34341047d9e0d7b92c9207bf413655"));
        assertThat(blockchain.getRepository().hasAccountState(internalContract)).isFalse();

        // Manipulate the repository to have a non-default nonce value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(internalContract);
        cache.setNonce(internalContract, BigInteger.TEN);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployInternal, true);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo("reverted");
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployInternal.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        InternalTransaction itx = result.getInternalTransactions().get(0);
        assertThat(itx.isCreate).isTrue();
        assertThat(TxUtil.calculateContractAddress(itx)).isEqualTo(internalContract);
        assertThat(itx.isRejected).isTrue();
        assertThat(itx.energyLimit).isEqualTo(internalLimit);
        assertThat(result.getNrgUsed()).isGreaterThan(BigInteger.valueOf(itx.energyLimit));

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployInternalAvmContractOnTopOfAddressWithNonceUsingAvmVersion2() throws VmFatalException {
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.DEPLOY_INTERNAL, resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        Pair<Block, ImportResult> resultImport = BlockchainTestUtils.addMiningBlock(blockchain, blockchain.getBestBlock(), List.of(deployTxAvm));
        assertThat(resultImport.getRight()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Call AVM contract to deploy new internal AVM contract (version without required success).
        long internalLimit = 1_000_000;
        AionTransaction deployInternal = BlockchainTestUtils.callSimpleAvmContractTransaction(resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ONE, contract, "deploy", deployTxAvm.getData(), internalLimit);
        AionAddress internalContract = new AionAddress(Hex.decode("a0268090998a99666b72cc452b9307438a34341047d9e0d7b92c9207bf413655"));
        assertThat(blockchain.getRepository().hasAccountState(internalContract)).isFalse();

        // Manipulate the repository to have a non-default nonce value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(internalContract);
        cache.setNonce(internalContract, BigInteger.TEN);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployInternal, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployInternal.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        InternalTransaction itx = result.getInternalTransactions().get(0);
        assertThat(itx.isCreate).isTrue();
        assertThat(TxUtil.calculateContractAddress(itx)).isEqualTo(internalContract);
        assertThat(itx.isRejected).isFalse();
        assertThat(itx.energyLimit).isEqualTo(internalLimit);
        assertThat(result.getNrgUsed()).isGreaterThan(BigInteger.valueOf(itx.energyLimit));

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployInternalAvmContractOnTopOfAddressWithStorageUsingAvmVersion2_DeployAndRequireSuccess() throws VmFatalException {
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.DEPLOY_INTERNAL, resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        Pair<Block, ImportResult> resultImport = BlockchainTestUtils.addMiningBlock(blockchain, blockchain.getBestBlock(), List.of(deployTxAvm));
        assertThat(resultImport.getRight()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Call AVM contract to deploy new internal AVM contract (version with required success).
        long internalLimit = 1_000_000;
        AionTransaction deployInternal = BlockchainTestUtils.callSimpleAvmContractTransaction(resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ONE, contract, "deployAndRequireSuccess", deployTxAvm.getData(), internalLimit);
        AionAddress internalContract = new AionAddress(Hex.decode("a0268090998a99666b72cc452b9307438a34341047d9e0d7b92c9207bf413655"));
        assertThat(blockchain.getRepository().hasAccountState(internalContract)).isFalse();

        // Manipulate the repository to have a non-default storage value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.addStorageRow(internalContract, ByteArrayWrapper.wrap(RandomUtils.nextBytes(16)), ByteArrayWrapper.wrap(RandomUtils.nextBytes(16)));
        cache.saveVmType(internalContract, InternalVmType.AVM);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
        byte[] oldRoot = contractState.getStateRoot();

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployInternal, true);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo("reverted");
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployInternal.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        InternalTransaction itx = result.getInternalTransactions().get(0);
        assertThat(itx.isCreate).isTrue();
        assertThat(TxUtil.calculateContractAddress(itx)).isEqualTo(internalContract);
        assertThat(itx.isRejected).isTrue();
        assertThat(itx.energyLimit).isEqualTo(internalLimit);
        assertThat(result.getNrgUsed()).isGreaterThan(BigInteger.valueOf(itx.energyLimit));

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(oldRoot);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployInternalAvmContractOnTopOfAddressWithStorageUsingAvmVersion2() throws VmFatalException {
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.DEPLOY_INTERNAL, resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        Pair<Block, ImportResult> resultImport = BlockchainTestUtils.addMiningBlock(blockchain, blockchain.getBestBlock(), List.of(deployTxAvm));
        assertThat(resultImport.getRight()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Call AVM contract to deploy new internal AVM contract (version without required success).
        long internalLimit = 1_000_000;
        AionTransaction deployInternal = BlockchainTestUtils.callSimpleAvmContractTransaction(resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ONE, contract, "deploy", deployTxAvm.getData(), internalLimit);
        AionAddress internalContract = new AionAddress(Hex.decode("a0268090998a99666b72cc452b9307438a34341047d9e0d7b92c9207bf413655"));
        assertThat(blockchain.getRepository().hasAccountState(internalContract)).isFalse();

        // Manipulate the repository to have a non-default storage value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.addStorageRow(internalContract, ByteArrayWrapper.wrap(RandomUtils.nextBytes(16)), ByteArrayWrapper.wrap(RandomUtils.nextBytes(16)));
        cache.saveVmType(internalContract, InternalVmType.AVM);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
        byte[] oldRoot = contractState.getStateRoot();

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployInternal, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployInternal.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        InternalTransaction itx = result.getInternalTransactions().get(0);
        assertThat(itx.isCreate).isTrue();
        assertThat(TxUtil.calculateContractAddress(itx)).isEqualTo(internalContract);
        assertThat(itx.isRejected).isFalse();
        assertThat(itx.energyLimit).isEqualTo(internalLimit);
        assertThat(result.getNrgUsed()).isGreaterThan(BigInteger.valueOf(itx.energyLimit));

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(oldRoot);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployInternalAvmContractOnTopOfAddressWithCodeUsingAvmVersion2_DeployAndRequireSuccess() throws VmFatalException {
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.DEPLOY_INTERNAL, resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        Pair<Block, ImportResult> resultImport = BlockchainTestUtils.addMiningBlock(blockchain, blockchain.getBestBlock(), List.of(deployTxAvm));
        assertThat(resultImport.getRight()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Call AVM contract to deploy new internal AVM contract (version with required success).
        long internalLimit = 1_000_000;
        AionTransaction deployInternal = BlockchainTestUtils.callSimpleAvmContractTransaction(resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ONE, contract, "deployAndRequireSuccess", deployTxAvm.getData(), internalLimit);
        AionAddress internalContract = new AionAddress(Hex.decode("a0268090998a99666b72cc452b9307438a34341047d9e0d7b92c9207bf413655"));
        assertThat(blockchain.getRepository().hasAccountState(internalContract)).isFalse();

        // Manipulate the repository to have a non-default code value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(internalContract);
        cache.saveCode(internalContract, new byte[] {1, 2, 3, 4});
        cache.saveVmType(internalContract, InternalVmType.AVM);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
        byte[] oldRoot = contractState.getStateRoot();
        byte[] oldCode = contractState.getCodeHash();

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployInternal, true);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo("reverted");
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployInternal.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        InternalTransaction itx = result.getInternalTransactions().get(0);
        assertThat(itx.isCreate).isTrue();
        assertThat(TxUtil.calculateContractAddress(itx)).isEqualTo(internalContract);
        assertThat(itx.isRejected).isTrue();
        assertThat(itx.energyLimit).isEqualTo(internalLimit);
        assertThat(result.getNrgUsed()).isGreaterThan(BigInteger.valueOf(itx.energyLimit));

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(oldRoot);
        assertThat(contractState.getCodeHash()).isEqualTo(oldCode);
    }

    @Test
    public void deployInternalAvmContractOnTopOfAddressWithCodeUsingAvmVersion2() throws VmFatalException {
        // Deploy AVM contract.
        AionTransaction deployTxAvm = BlockchainTestUtils.deployAvmContractTransaction(AvmContract.DEPLOY_INTERNAL, resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ZERO);
        AionAddress contract = TxUtil.calculateContractAddress(deployTxAvm);

        Pair<Block, ImportResult> resultImport = BlockchainTestUtils.addMiningBlock(blockchain, blockchain.getBestBlock(), List.of(deployTxAvm));
        assertThat(resultImport.getRight()).isEqualTo(ImportResult.IMPORTED_BEST);

        // Call AVM contract to deploy new internal AVM contract (version without required success).
        long internalLimit = 1_000_000;
        AionTransaction deployInternal = BlockchainTestUtils.callSimpleAvmContractTransaction(resourceProvider.factoryForVersion2, SENDER_KEY, BigInteger.ONE, contract, "deploy", deployTxAvm.getData(), internalLimit);
        AionAddress internalContract = new AionAddress(Hex.decode("a0268090998a99666b72cc452b9307438a34341047d9e0d7b92c9207bf413655"));
        assertThat(blockchain.getRepository().hasAccountState(internalContract)).isFalse();

        // Manipulate the repository to have a non-default code value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(internalContract);
        cache.saveCode(internalContract, new byte[] {1, 2, 3, 4});
        cache.saveVmType(internalContract, InternalVmType.AVM);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
        byte[] oldRoot = contractState.getStateRoot();
        byte[] oldCode = contractState.getCodeHash();

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployInternal, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployInternal.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        InternalTransaction itx = result.getInternalTransactions().get(0);
        assertThat(itx.isCreate).isTrue();
        assertThat(TxUtil.calculateContractAddress(itx)).isEqualTo(internalContract);
        assertThat(itx.isRejected).isFalse();
        assertThat(itx.energyLimit).isEqualTo(internalLimit);
        assertThat(result.getNrgUsed()).isGreaterThan(BigInteger.valueOf(itx.energyLimit));

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(internalContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(oldRoot);
        assertThat(contractState.getCodeHash()).isEqualTo(oldCode);
    }

    @Test
    public void deployFvmContractOnTopOfAddressWithBalanceBeforeFork040() throws VmFatalException {
        // Deploy FVM contract.
        String contractCode = "0x605060405234156100105760006000fd5b5b600a600060005081909090555060006000505460016000506000600060005054815260100190815260100160002090506000508190909055506064600260005060000160005081909090555060c8600260005060010160005081909090555060026000506001016000505460016000506000600260005060000160005054815260100190815260100160002090506000508190909055505b6100ae565b610184806100bd6000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680631677b0ff14610049578063209652551461007657806362eb702a146100a057610043565b60006000fd5b34156100555760006000fd5b61007460048080359060100190919080359060100190919050506100c4565b005b34156100825760006000fd5b61008a610111565b6040518082815260100191505060405180910390f35b34156100ac5760006000fd5b6100c26004808035906010019091905050610123565b005b8160026000506000016000508190909055508060026000506001016000508190909055508082016001600050600084815260100190815260100160002090506000508190909055505b5050565b60006000600050549050610120565b90565b806000600050819090905550600181016001600050600083815260100190815260100160002090506000508190909055505b505600a165627a7a723058205b6e690d70d3703337452467437dc7c4e863ee4ad34b24cc516e2afa71e334700029";
        AionTransaction deployTxFVM = AionTransaction.create(SENDER_KEY, BigInteger.ZERO.toByteArray(), null, BigInteger.ZERO.toByteArray(), ByteUtil.hexStringToBytes(contractCode), 5_000_000L, ENERGY_PRICE, TransactionTypes.DEFAULT, null);
        AionAddress fvmContract = TxUtil.calculateContractAddress(deployTxFVM);

        // Manipulate the repository to have a non-default balance value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(fvmContract);
        cache.addBalance(fvmContract, BigInteger.TEN);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(fvmContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);

        // Next, process the deploy transaction with fork040 disabled.
        AionTxExecSummary result = executeTransaction(deployTxFVM, false);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.FAILURE.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.valueOf(deployTxFVM.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(fvmContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployFvmContractOnTopOfAddressWithNonceBeforeFork040() throws VmFatalException {
        // Deploy FVM contract.
        String contractCode = "0x605060405234156100105760006000fd5b5b600a600060005081909090555060006000505460016000506000600060005054815260100190815260100160002090506000508190909055506064600260005060000160005081909090555060c8600260005060010160005081909090555060026000506001016000505460016000506000600260005060000160005054815260100190815260100160002090506000508190909055505b6100ae565b610184806100bd6000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680631677b0ff14610049578063209652551461007657806362eb702a146100a057610043565b60006000fd5b34156100555760006000fd5b61007460048080359060100190919080359060100190919050506100c4565b005b34156100825760006000fd5b61008a610111565b6040518082815260100191505060405180910390f35b34156100ac5760006000fd5b6100c26004808035906010019091905050610123565b005b8160026000506000016000508190909055508060026000506001016000508190909055508082016001600050600084815260100190815260100160002090506000508190909055505b5050565b60006000600050549050610120565b90565b806000600050819090905550600181016001600050600083815260100190815260100160002090506000508190909055505b505600a165627a7a723058205b6e690d70d3703337452467437dc7c4e863ee4ad34b24cc516e2afa71e334700029";
        AionTransaction deployTxFVM = AionTransaction.create(SENDER_KEY, BigInteger.ZERO.toByteArray(), null, BigInteger.ZERO.toByteArray(), ByteUtil.hexStringToBytes(contractCode), 5_000_000L, ENERGY_PRICE, TransactionTypes.DEFAULT, null);
        AionAddress fvmContract = TxUtil.calculateContractAddress(deployTxFVM);

        // Manipulate the repository to have a non-default nonce value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(fvmContract);
        cache.setNonce(fvmContract, BigInteger.TEN);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(fvmContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);

        // Next, process the deploy transaction with fork040 disabled.
        AionTxExecSummary result = executeTransaction(deployTxFVM, false);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.FAILURE.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.valueOf(deployTxFVM.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(fvmContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployFvmContractOnTopOfAddressWithStorageBeforeFork040() throws VmFatalException {
        // Deploy FVM contract.
        String contractCode = "0x605060405234156100105760006000fd5b5b600a600060005081909090555060006000505460016000506000600060005054815260100190815260100160002090506000508190909055506064600260005060000160005081909090555060c8600260005060010160005081909090555060026000506001016000505460016000506000600260005060000160005054815260100190815260100160002090506000508190909055505b6100ae565b610184806100bd6000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680631677b0ff14610049578063209652551461007657806362eb702a146100a057610043565b60006000fd5b34156100555760006000fd5b61007460048080359060100190919080359060100190919050506100c4565b005b34156100825760006000fd5b61008a610111565b6040518082815260100191505060405180910390f35b34156100ac5760006000fd5b6100c26004808035906010019091905050610123565b005b8160026000506000016000508190909055508060026000506001016000508190909055508082016001600050600084815260100190815260100160002090506000508190909055505b5050565b60006000600050549050610120565b90565b806000600050819090905550600181016001600050600083815260100190815260100160002090506000508190909055505b505600a165627a7a723058205b6e690d70d3703337452467437dc7c4e863ee4ad34b24cc516e2afa71e334700029";
        AionTransaction deployTxFVM = AionTransaction.create(SENDER_KEY, BigInteger.ZERO.toByteArray(), null, BigInteger.ZERO.toByteArray(), ByteUtil.hexStringToBytes(contractCode), 5_000_000L, ENERGY_PRICE, TransactionTypes.DEFAULT, null);
        AionAddress fvmContract = TxUtil.calculateContractAddress(deployTxFVM);

        // Manipulate the repository to have a non-default storage value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.addBalance(fvmContract, BigInteger.TEN);
        cache.addStorageRow(fvmContract, ByteArrayWrapper.wrap(RandomUtils.nextBytes(16)), ByteArrayWrapper.wrap(RandomUtils.nextBytes(16)));
        cache.saveVmType(fvmContract, InternalVmType.FVM);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(fvmContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
        byte[] oldRoot = contractState.getStateRoot();

        // Next, process the deploy transaction with fork040 disabled.
        AionTxExecSummary result = executeTransaction(deployTxFVM, false);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.FAILURE.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.valueOf(deployTxFVM.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(fvmContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(oldRoot);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployFvmContractOnTopOfAddressWithCodeBeforeFork040() throws VmFatalException {
        // Deploy FVM contract.
        String contractCode = "0x605060405234156100105760006000fd5b5b600a600060005081909090555060006000505460016000506000600060005054815260100190815260100160002090506000508190909055506064600260005060000160005081909090555060c8600260005060010160005081909090555060026000506001016000505460016000506000600260005060000160005054815260100190815260100160002090506000508190909055505b6100ae565b610184806100bd6000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680631677b0ff14610049578063209652551461007657806362eb702a146100a057610043565b60006000fd5b34156100555760006000fd5b61007460048080359060100190919080359060100190919050506100c4565b005b34156100825760006000fd5b61008a610111565b6040518082815260100191505060405180910390f35b34156100ac5760006000fd5b6100c26004808035906010019091905050610123565b005b8160026000506000016000508190909055508060026000506001016000508190909055508082016001600050600084815260100190815260100160002090506000508190909055505b5050565b60006000600050549050610120565b90565b806000600050819090905550600181016001600050600083815260100190815260100160002090506000508190909055505b505600a165627a7a723058205b6e690d70d3703337452467437dc7c4e863ee4ad34b24cc516e2afa71e334700029";
        AionTransaction deployTxFVM = AionTransaction.create(SENDER_KEY, BigInteger.ZERO.toByteArray(), null, BigInteger.ZERO.toByteArray(), ByteUtil.hexStringToBytes(contractCode), 5_000_000L, ENERGY_PRICE, TransactionTypes.DEFAULT, null);
        AionAddress fvmContract = TxUtil.calculateContractAddress(deployTxFVM);

        // Manipulate the repository to have a non-default code value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(fvmContract);
        cache.saveCode(fvmContract, new byte[] {1, 2, 3, 4});
        cache.saveVmType(fvmContract, InternalVmType.FVM);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(fvmContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
        byte[] oldCode = contractState.getCodeHash();

        // Next, process the deploy transaction with fork040 disabled.
        AionTxExecSummary result = executeTransaction(deployTxFVM, false);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.FAILURE.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.valueOf(deployTxFVM.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(fvmContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(oldCode);
    }

    @Test
    public void deployFvmContractOnTopOfAddressWithBalanceAfterFork040() throws VmFatalException {
        // Deploy FVM contract.
        String contractCode = "0x605060405234156100105760006000fd5b5b600a600060005081909090555060006000505460016000506000600060005054815260100190815260100160002090506000508190909055506064600260005060000160005081909090555060c8600260005060010160005081909090555060026000506001016000505460016000506000600260005060000160005054815260100190815260100160002090506000508190909055505b6100ae565b610184806100bd6000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680631677b0ff14610049578063209652551461007657806362eb702a146100a057610043565b60006000fd5b34156100555760006000fd5b61007460048080359060100190919080359060100190919050506100c4565b005b34156100825760006000fd5b61008a610111565b6040518082815260100191505060405180910390f35b34156100ac5760006000fd5b6100c26004808035906010019091905050610123565b005b8160026000506000016000508190909055508060026000506001016000508190909055508082016001600050600084815260100190815260100160002090506000508190909055505b5050565b60006000600050549050610120565b90565b806000600050819090905550600181016001600050600083815260100190815260100160002090506000508190909055505b505600a165627a7a723058205b6e690d70d3703337452467437dc7c4e863ee4ad34b24cc516e2afa71e334700029";
        AionTransaction deployTxFVM = AionTransaction.create(SENDER_KEY, BigInteger.ZERO.toByteArray(), null, BigInteger.ZERO.toByteArray(), ByteUtil.hexStringToBytes(contractCode), 5_000_000L, ENERGY_PRICE, TransactionTypes.DEFAULT, null);
        AionAddress fvmContract = TxUtil.calculateContractAddress(deployTxFVM);

        // Manipulate the repository to have a non-default balance value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(fvmContract);
        cache.addBalance(fvmContract, BigInteger.TEN);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(fvmContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployTxFVM, true);

        assertThat(result.isFailed()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getReceipt().getError()).isEmpty();
        assertThat(result.getNrgUsed()).isLessThan(BigInteger.valueOf(deployTxFVM.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(fvmContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployFvmContractOnTopOfAddressWithNonceAfterFork040() throws VmFatalException {
        // Deploy FVM contract.
        String contractCode = "0x605060405234156100105760006000fd5b5b600a600060005081909090555060006000505460016000506000600060005054815260100190815260100160002090506000508190909055506064600260005060000160005081909090555060c8600260005060010160005081909090555060026000506001016000505460016000506000600260005060000160005054815260100190815260100160002090506000508190909055505b6100ae565b610184806100bd6000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680631677b0ff14610049578063209652551461007657806362eb702a146100a057610043565b60006000fd5b34156100555760006000fd5b61007460048080359060100190919080359060100190919050506100c4565b005b34156100825760006000fd5b61008a610111565b6040518082815260100191505060405180910390f35b34156100ac5760006000fd5b6100c26004808035906010019091905050610123565b005b8160026000506000016000508190909055508060026000506001016000508190909055508082016001600050600084815260100190815260100160002090506000508190909055505b5050565b60006000600050549050610120565b90565b806000600050819090905550600181016001600050600083815260100190815260100160002090506000508190909055505b505600a165627a7a723058205b6e690d70d3703337452467437dc7c4e863ee4ad34b24cc516e2afa71e334700029";
        AionTransaction deployTxFVM = AionTransaction.create(SENDER_KEY, BigInteger.ZERO.toByteArray(), null, BigInteger.ZERO.toByteArray(), ByteUtil.hexStringToBytes(contractCode), 5_000_000L, ENERGY_PRICE, TransactionTypes.DEFAULT, null);
        AionAddress fvmContract = TxUtil.calculateContractAddress(deployTxFVM);

        // Manipulate the repository to have a non-default nonce value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(fvmContract);
        cache.setNonce(fvmContract, BigInteger.TEN);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(fvmContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployTxFVM, true);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.FAILURE.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.valueOf(deployTxFVM.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(fvmContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployFvmContractOnTopOfAddressWithStorageAfterFork040() throws VmFatalException {
        // Deploy FVM contract.
        String contractCode = "0x605060405234156100105760006000fd5b5b600a600060005081909090555060006000505460016000506000600060005054815260100190815260100160002090506000508190909055506064600260005060000160005081909090555060c8600260005060010160005081909090555060026000506001016000505460016000506000600260005060000160005054815260100190815260100160002090506000508190909055505b6100ae565b610184806100bd6000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680631677b0ff14610049578063209652551461007657806362eb702a146100a057610043565b60006000fd5b34156100555760006000fd5b61007460048080359060100190919080359060100190919050506100c4565b005b34156100825760006000fd5b61008a610111565b6040518082815260100191505060405180910390f35b34156100ac5760006000fd5b6100c26004808035906010019091905050610123565b005b8160026000506000016000508190909055508060026000506001016000508190909055508082016001600050600084815260100190815260100160002090506000508190909055505b5050565b60006000600050549050610120565b90565b806000600050819090905550600181016001600050600083815260100190815260100160002090506000508190909055505b505600a165627a7a723058205b6e690d70d3703337452467437dc7c4e863ee4ad34b24cc516e2afa71e334700029";
        AionTransaction deployTxFVM = AionTransaction.create(SENDER_KEY, BigInteger.ZERO.toByteArray(), null, BigInteger.ZERO.toByteArray(), ByteUtil.hexStringToBytes(contractCode), 5_000_000L, ENERGY_PRICE, TransactionTypes.DEFAULT, null);
        AionAddress fvmContract = TxUtil.calculateContractAddress(deployTxFVM);

        // Manipulate the repository to have a non-default storage value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.addBalance(fvmContract, BigInteger.TEN);
        cache.addStorageRow(fvmContract, ByteArrayWrapper.wrap(RandomUtils.nextBytes(16)), ByteArrayWrapper.wrap(RandomUtils.nextBytes(16)));
        cache.saveVmType(fvmContract, InternalVmType.FVM);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(fvmContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isNotEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
        byte[] oldRoot = contractState.getStateRoot();

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployTxFVM, true);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.FAILURE.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.valueOf(deployTxFVM.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(fvmContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.TEN);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(oldRoot);
        assertThat(contractState.getCodeHash()).isEqualTo(EMPTY_DATA_HASH);
    }

    @Test
    public void deployFvmContractOnTopOfAddressWithCodeAfterFork040() throws VmFatalException {
        // Deploy FVM contract.
        String contractCode = "0x605060405234156100105760006000fd5b5b600a600060005081909090555060006000505460016000506000600060005054815260100190815260100160002090506000508190909055506064600260005060000160005081909090555060c8600260005060010160005081909090555060026000506001016000505460016000506000600260005060000160005054815260100190815260100160002090506000508190909055505b6100ae565b610184806100bd6000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680631677b0ff14610049578063209652551461007657806362eb702a146100a057610043565b60006000fd5b34156100555760006000fd5b61007460048080359060100190919080359060100190919050506100c4565b005b34156100825760006000fd5b61008a610111565b6040518082815260100191505060405180910390f35b34156100ac5760006000fd5b6100c26004808035906010019091905050610123565b005b8160026000506000016000508190909055508060026000506001016000508190909055508082016001600050600084815260100190815260100160002090506000508190909055505b5050565b60006000600050549050610120565b90565b806000600050819090905550600181016001600050600083815260100190815260100160002090506000508190909055505b505600a165627a7a723058205b6e690d70d3703337452467437dc7c4e863ee4ad34b24cc516e2afa71e334700029";
        AionTransaction deployTxFVM = AionTransaction.create(SENDER_KEY, BigInteger.ZERO.toByteArray(), null, BigInteger.ZERO.toByteArray(), ByteUtil.hexStringToBytes(contractCode), 5_000_000L, ENERGY_PRICE, TransactionTypes.DEFAULT, null);
        AionAddress fvmContract = TxUtil.calculateContractAddress(deployTxFVM);

        // Manipulate the repository to have a non-default code value.
        RepositoryCache cache = blockchain.getRepository().startTracking();
        cache.createAccount(fvmContract);
        cache.saveCode(fvmContract, new byte[] {1, 2, 3, 4});
        cache.saveVmType(fvmContract, InternalVmType.FVM);
        cache.flush();

        // Check assumptions about contract state.
        AccountState contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(fvmContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isNotEqualTo(EMPTY_DATA_HASH);
        byte[] oldCode = contractState.getCodeHash();

        // Next, process the deploy transaction with fork040 enabled.
        AionTxExecSummary result = executeTransaction(deployTxFVM, true);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getReceipt().getError()).isEqualTo(FastVmResultCode.FAILURE.toString());
        assertThat(result.getNrgUsed()).isEqualTo(BigInteger.valueOf(deployTxFVM.getEnergyLimit()));
        assertThat(result.getLogs()).isEmpty();

        contractState = (AccountState) blockchain.getRepository().startTracking().getAccountState(fvmContract);
        assertThat(contractState.getBalance()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getNonce()).isEqualTo(BigInteger.ZERO);
        assertThat(contractState.getStateRoot()).isEqualTo(EMPTY_TRIE_HASH);
        assertThat(contractState.getCodeHash()).isEqualTo(oldCode);
    }

    private AionTxExecSummary executeTransaction(AionTransaction transaction, boolean enableFork040) throws VmFatalException {
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
                enableFork040,
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
