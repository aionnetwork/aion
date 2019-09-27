package org.aion.zero.impl.vm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.aion.vm.avm.schedule.AvmVersionSchedule;
import org.aion.vm.avm.AvmConfigurations;
import org.aion.vm.common.VmFatalException;
import org.aion.avm.stub.IEnergyRules;
import org.aion.avm.stub.IEnergyRules.TransactionType;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.base.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.db.InternalVmType;
import org.aion.mcf.db.Repository;
import org.aion.mcf.db.RepositoryCache;
import org.aion.util.types.DataWord;
import org.aion.solidity.CompilationResult;
import org.aion.solidity.Compiler;
import org.aion.solidity.Compiler.Options;
import org.aion.types.AionAddress;
import org.aion.util.conversions.Hex;
import org.aion.util.types.AddressUtils;
import org.aion.vm.common.BlockCachingContext;
import org.aion.vm.common.BulkExecutor;
import org.aion.vm.common.TxNrgRule;
import org.aion.zero.impl.blockchain.StandaloneBlockchain;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.vm.contracts.ContractUtils;
import org.aion.base.AionTxExecSummary;
import org.aion.base.AionTxReceipt;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;

/**
 * These were the tests previous in TransactionExecutorUnitTest but that class is now strictly unit
 * testing. These tests are more on the integration test level. Once there's a place to fit these
 * they will be moved.
 */
public class OldTxExecutorTest {
    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;

    @BeforeClass
    public static void beforeClass() {
        AionLoggerFactory.initAll();
    }

    @Before
    public void setup() {
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .build();
        blockchain = bundle.bc;
        deployerKey = bundle.privateKeys.get(0);

        // Configure the avm if it has not already been configured.
        AvmVersionSchedule schedule = AvmVersionSchedule.newScheduleForOnlySingleVersionSupport(0, 0);
        String projectRoot = AvmPathManager.getPathOfProjectRootDirectory();
        IEnergyRules energyRules = (t, l) -> {
            if (t == TransactionType.CREATE) {
                return TxNrgRule.isValidNrgContractCreate(l);
            } else {
                return TxNrgRule.isValidNrgTx(l);
            }
        };

        AvmConfigurations.initializeConfigurationsAsReadAndWriteable(schedule, projectRoot, energyRules);
    }

    @After
    public void tearDown() {
        blockchain = null;
        AvmConfigurations.clear();
    }

    @Test
    public void testCallTransaction() throws Exception {
        Compiler.Result r =
                Compiler.getInstance()
                        .compile(
                                ContractUtils.readContract("Ticker.sol"), Options.ABI, Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get("Ticker").bin; // deployer
        String contract = deployer.substring(deployer.indexOf("60506040", 1)); // contract

        byte[] txNonce = BigInteger.ZERO.toByteArray();
        AionAddress to =
                AddressUtils.wrapAddress(
                        "2222222222222222222222222222222222222222222222222222222222222222");
        byte[] value = BigInteger.ZERO.toByteArray();
        byte[] data = Hex.decode("c0004213");
        long nrg = new DataWord(100000L).longValue();
        long nrgPrice = DataWord.ONE.longValue();
        AionTransaction tx = AionTransaction.create(
                deployerKey,
                txNonce,
                to,
                value,
                data,
                nrg,
                nrgPrice,
                TransactionTypes.DEFAULT, null);

        AionBlock block = createDummyBlock();

        AionRepositoryImpl repo = blockchain.getRepository();
        RepositoryCache cache = repo.startTracking();
        cache.addBalance(
                tx.getSenderAddress(),
                BigInteger.valueOf(100_000).multiply(BigInteger.valueOf(tx.getEnergyPrice())));
        cache.createAccount(to);
        cache.saveCode(to, Hex.decode(contract));
        cache.saveVmType(to, InternalVmType.FVM);

        cache.flush();

        AionTxReceipt receipt = executeTransaction(repo, block, tx).getReceipt();
        System.out.println(receipt);

        assertArrayEquals(
                Hex.decode("00000000000000000000000000000000"), receipt.getTransactionOutput());
    }

    @Test
    public void testCreateTransaction() throws Exception {
        Compiler.Result r =
                Compiler.getInstance()
                        .compile(
                                ContractUtils.readContract("Ticker.sol"), Options.ABI, Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get("Ticker").bin;
        System.out.println(deployer);

        byte[] txNonce = BigInteger.ZERO.toByteArray();
        AionAddress to = null;
        byte[] value = BigInteger.ZERO.toByteArray();
        byte[] data = Hex.decode(deployer);
        long nrg = 500_000L;
        long nrgPrice = 1;
        AionTransaction tx = AionTransaction.create(
                deployerKey,
                txNonce,
                to,
                value,
                data,
                nrg,
                nrgPrice,
                TransactionTypes.DEFAULT, null);

        AionBlock block = createDummyBlock();

        AionRepositoryImpl repoTop = blockchain.getRepository();
        RepositoryCache<AccountState, IBlockStoreBase> repo = repoTop.startTracking();
        repo.addBalance(tx.getSenderAddress(), BigInteger.valueOf(500_000L).multiply(BigInteger.valueOf(tx.getEnergyPrice())));

        AionTxReceipt receipt = executeTransaction(repo, block, tx).getReceipt();
        System.out.println(receipt);

        assertArrayEquals(
                Hex.decode(deployer.substring(deployer.indexOf("60506040", 1))),
                receipt.getTransactionOutput());
    }

    @Test
    public void testPerformance() throws Exception {
        Compiler.Result r =
                Compiler.getInstance()
                        .compile(
                                ContractUtils.readContract("Ticker.sol"), Options.ABI, Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get("Ticker").bin; // deployer
        String contract = deployer.substring(deployer.indexOf("60506040", 1)); // contract

        byte[] txNonce = BigInteger.ZERO.toByteArray();
        AionAddress to =
                AddressUtils.wrapAddress(
                        "2222222222222222222222222222222222222222222222222222222222222222");
        byte[] value = BigInteger.ZERO.toByteArray();
        byte[] data = Hex.decode("c0004213");
        long nrg = new DataWord(100000L).longValue();
        long nrgPrice = DataWord.ONE.longValue();
        ECKey key = ECKeyFac.inst().create();
        AionTransaction tx =
                AionTransaction.create(
                        key,
                        txNonce,
                        to,
                        value,
                        data,
                        nrg,
                        nrgPrice,
                        TransactionTypes.DEFAULT, null);

        AionBlock block = createDummyBlock();

        AionRepositoryImpl repoTop = blockchain.getRepository();
        RepositoryCache repo = repoTop.startTracking();
        repo.addBalance(
                tx.getSenderAddress(),
                BigInteger.valueOf(100_000).multiply(BigInteger.valueOf(tx.getEnergyPrice())));
        repo.createAccount(to);
        repo.saveCode(to, Hex.decode(contract));
        repo.saveVmType(to, InternalVmType.FVM);
        repo.flush();

        long t1 = System.nanoTime();
        long repeat = 1000;
        for (int i = 0; i < repeat; i++) {
            executeTransaction(repo, block, tx);
        }
        long t2 = System.nanoTime();
        System.out.println((t2 - t1) / repeat);
    }

    @Test
    public void testBasicTransactionCost() throws Exception {
        byte[] txNonce = BigInteger.ZERO.toByteArray();
        AionAddress to =
                AddressUtils.wrapAddress(
                        "2222222222222222222222222222222222222222222222222222222222222222");
        byte[] value = BigInteger.ONE.toByteArray();
        byte[] data = new byte[0];
        long nrg = new DataWord(100000L).longValue();
        long nrgPrice = DataWord.ONE.longValue();
        ECKey key = ECKeyFac.inst().create();
        AionTransaction tx =
                AionTransaction.create(
                        key,
                        txNonce,
                        to,
                        value,
                        data,
                        nrg,
                        nrgPrice,
                        TransactionTypes.DEFAULT, null);

        AionBlock block = createDummyBlock();

        AionRepositoryImpl repoTop = blockchain.getRepository();
        RepositoryCache repo = repoTop.startTracking();
        repo.addBalance(tx.getSenderAddress(), BigInteger.valueOf(1_000_000_000L));
        repo.flush();

        AionTxReceipt receipt = executeTransaction(repo, block, tx).getReceipt();
        System.out.println(receipt);

        assertEquals(TxUtil.calculateTransactionCost(tx), receipt.getEnergyUsed());
    }

    private static AionBlock createDummyBlock() {
        byte[] parentHash = new byte[32];
        byte[] coinbase = RandomUtils.nextBytes(AionAddress.LENGTH);
        byte[] logsBloom = new byte[256];
        byte[] difficulty = new DataWord(0x1000000L).getData();
        long number = 1;
        long timestamp = System.currentTimeMillis() / 1000;
        byte[] extraData = new byte[0];
        byte[] nonce = new byte[32];
        byte[] receiptsRoot = new byte[32];
        byte[] transactionsRoot = new byte[32];
        byte[] stateRoot = new byte[32];
        List<AionTransaction> transactionsList = Collections.emptyList();
        byte[] solutions = new byte[1408];

        // TODO: set a dummy limit of 5000000 for now
        return new AionBlock(
                parentHash,
                new AionAddress(coinbase),
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

    private AionTxExecSummary executeTransaction(
            Repository repo, Block block, AionTransaction transaction) throws VmFatalException {
        return BulkExecutor.executeTransactionWithNoPostExecutionWork(
                block.getDifficulty(),
                block.getNumber(),
                block.getTimestamp(),
                block.getNrgLimit(),
                block.getCoinbase(),
                transaction,
                repo.startTracking(),
                false,
                true,
                false,
                false,
                LOGGER_VM,
                BlockCachingContext.PENDING,
                block.getNumber() - 1);
    }
}
