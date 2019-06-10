package org.aion.precompiled.contracts.ATB;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.precompiled.contracts.ATB.BridgeTestUtils.dummyContext;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;
import java.util.Properties;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.interfaces.db.ContractDetails;
import org.aion.interfaces.db.PruneConfig;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.interfaces.db.RepositoryConfig;
import org.aion.mcf.config.CfgPrune;
import org.aion.vm.api.types.Address;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.fastvm.ExecutionContext;
import org.aion.precompiled.PrecompiledUtilities;

import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.zero.impl.db.AionRepositoryCache;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.junit.Before;
import org.junit.Test;

public class BridgeTransferTest {

    private RepositoryCache repo;
    private BridgeStorageConnector connector;
    private BridgeController controller;
    private TokenBridgeContract contract;
    private ExecutionContext context;

    private static final Address CONTRACT_ADDR =
            new Address(HashUtil.h256("contractAddress".getBytes()));
    private static final Address OWNER_ADDR = new Address(HashUtil.h256("ownerAddress".getBytes()));

    private static final ECKey members[] =
            new ECKey[] {
                ECKeyFac.inst().create(),
                ECKeyFac.inst().create(),
                ECKeyFac.inst().create(),
                ECKeyFac.inst().create(),
                ECKeyFac.inst().create()
            };

    private static byte[][] getMemberAddress(ECKey[] members) {
        final byte[][] memberList = new byte[members.length][];
        for (int i = 0; i < members.length; i++) {
            memberList[i] = members[i].getAddress();
        }
        return memberList;
    }

    @Before
    public void beforeEach() {
        RepositoryConfig repoConfig =
                new RepositoryConfig() {
                    @Override
                    public String getDbPath() {
                        return "";
                    }

                    @Override
                    public PruneConfig getPruneConfig() {
                        return new CfgPrune(false);
                    }

                    @Override
                    public ContractDetails contractDetailsImpl() {
                        return ContractDetailsAion.createForTesting(0, 1000000).getDetails();
                    }

                    @Override
                    public Properties getDatabaseConfig(String db_name) {
                        Properties props = new Properties();
                        props.setProperty(DatabaseFactory.Props.DB_TYPE, DBVendor.MOCKDB.toValue());
                        props.setProperty(DatabaseFactory.Props.ENABLE_HEAP_CACHE, "false");
                        return props;
                    }
                };
        repo = new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
        resetContext();
    }

    private void resetContext() {
        this.context = dummyContext();
        this.contract = new TokenBridgeContract(context, repo, OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();
        this.controller = this.contract.getController();
        this.controller.initialize();

        byte[][] memberList = new byte[members.length][];
        for (int i = 0; i < members.length; i++) {
            memberList[i] = members[i].getAddress();
        }
        this.controller.ringInitialize(OWNER_ADDR.toBytes(), memberList);
    }

    @Test
    public void testBridgeTransferOne() {
        final byte[] senderAddress = this.members[0].getAddress();
        final byte[] blockHash = HashUtil.h256("blockHash".getBytes());
        final byte[] recipient = HashUtil.h256("recipient".getBytes());
        final byte[] sourceTransactionHash = HashUtil.h256("transaction".getBytes());
        final byte[] aionTransactionHash = HashUtil.h256("aionTransactionHash".getBytes());

        // ensure we have enough balance
        this.repo.addBalance(CONTRACT_ADDR, BigInteger.ONE);

        BridgeTransfer bundle =
                BridgeTransfer.getInstance(BigInteger.valueOf(1), recipient, sourceTransactionHash);
        byte[] bundleHash =
                BridgeUtilities.computeBundleHash(blockHash, new BridgeTransfer[] {bundle});

        byte[][] signatures = new byte[members.length][];
        for (int i = 0; i < members.length; i++) {
            signatures[i] = members[i].sign(bundleHash).toBytes();
        }

        ErrCode code = this.controller.setRelayer(OWNER_ADDR.toBytes(), senderAddress);
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);

        BridgeController.ProcessedResults results =
                this.controller.processBundles(
                        senderAddress,
                        aionTransactionHash,
                        blockHash,
                        new BridgeTransfer[] {bundle},
                        signatures);
        assertThat(results).isNotNull();
        assertThat(results.controllerResult).isEqualTo(ErrCode.NO_ERROR);
        assertThat(this.repo.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ZERO);
    }

    @Test
    public void testBridgeNotEnoughSignatures() {
        final byte[] senderAddress = members[0].getAddress();
        final byte[] blockHash = HashUtil.h256("blockHash".getBytes());
        final byte[] recipient = HashUtil.h256("recipient".getBytes());
        final byte[] sourceTransactionHash = HashUtil.h256("transaction".getBytes());

        // ATB-4, this is the hash of the aion transaction which submitted
        // this bundle, this is different from the source transaction hash
        // which is transferred from another blockchain network.
        final byte[] aionTransactionHash = HashUtil.h256("aionTransaction".getBytes());

        // ensure we have enough balance
        this.repo.addBalance(CONTRACT_ADDR, BigInteger.ONE);

        BridgeTransfer bundle =
                BridgeTransfer.getInstance(BigInteger.valueOf(1), recipient, sourceTransactionHash);
        byte[] bundleHash =
                BridgeUtilities.computeBundleHash(blockHash, new BridgeTransfer[] {bundle});

        byte[][] signatures = new byte[2][];
        for (int i = 0; i < 2; i++) {
            signatures[i] = members[i].sign(bundleHash).toBytes();
        }

        ErrCode code = this.controller.setRelayer(OWNER_ADDR.toBytes(), senderAddress);
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);

        BridgeController.ProcessedResults results =
                this.controller.processBundles(
                        senderAddress,
                        aionTransactionHash,
                        blockHash,
                        new BridgeTransfer[] {bundle},
                        signatures);

        assertThat(results).isNotNull();
        assertThat(results.controllerResult).isEqualTo(ErrCode.INVALID_SIGNATURE_BOUNDS);
        assertThat(this.repo.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ONE);
        assertThat(this.repo.getBalance(new Address(recipient))).isEqualTo(BigInteger.ZERO);
    }

    @Test
    public void testLowerBoundSignature() {
        final byte[] senderAddress = members[0].getAddress();
        final byte[] blockHash = HashUtil.h256("blockHash".getBytes());
        final byte[] recipient = HashUtil.h256("recipient".getBytes());
        final byte[] sourceTransactionHash = HashUtil.h256("transaction".getBytes());
        final byte[] aionTransactionHash = HashUtil.h256("aionTransaction".getBytes());

        // ensure we have enough balance
        this.repo.addBalance(CONTRACT_ADDR, BigInteger.ONE);

        BridgeTransfer bundle =
                BridgeTransfer.getInstance(BigInteger.valueOf(1), recipient, sourceTransactionHash);
        byte[] bundleHash =
                BridgeUtilities.computeBundleHash(blockHash, new BridgeTransfer[] {bundle});

        byte[][] signatures = new byte[3][];
        for (int i = 0; i < 3; i++) {
            signatures[i] = members[i].sign(bundleHash).toBytes();
        }

        ErrCode code = this.controller.setRelayer(OWNER_ADDR.toBytes(), senderAddress);
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);

        BridgeController.ProcessedResults results =
                this.controller.processBundles(
                        senderAddress,
                        aionTransactionHash,
                        blockHash,
                        new BridgeTransfer[] {bundle},
                        signatures);

        assertThat(results).isNotNull();
        assertThat(results.controllerResult).isEqualTo(ErrCode.NO_ERROR);
        assertThat(this.repo.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ZERO);
        assertThat(this.repo.getBalance(new Address(recipient))).isEqualTo(BigInteger.ONE);
    }

    @Test
    public void testBeyondUpperBoundSignatures() {
        final byte[] senderAddress = members[0].getAddress();
        final byte[] blockHash = HashUtil.h256("blockHash".getBytes());
        final byte[] recipient = HashUtil.h256("recipient".getBytes());
        final byte[] sourceTransactionHash = HashUtil.h256("transaction".getBytes());
        final byte[] aionTransactionHash = HashUtil.h256("aionTransaction".getBytes());

        // ensure we have enough balance
        this.repo.addBalance(CONTRACT_ADDR, BigInteger.ONE);

        BridgeTransfer bundle =
                BridgeTransfer.getInstance(BigInteger.valueOf(1), recipient, sourceTransactionHash);
        byte[] bundleHash =
                BridgeUtilities.computeBundleHash(blockHash, new BridgeTransfer[] {bundle});

        byte[][] signatures = new byte[6][];
        for (int i = 0; i < 5; i++) {
            signatures[i] = members[i].sign(bundleHash).toBytes();
        }

        ErrCode code = this.controller.setRelayer(OWNER_ADDR.toBytes(), senderAddress);
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);

        BridgeController.ProcessedResults results =
                this.controller.processBundles(
                        senderAddress,
                        aionTransactionHash,
                        blockHash,
                        new BridgeTransfer[] {bundle},
                        signatures);

        assertThat(results).isNotNull();
        assertThat(results.controllerResult).isEqualTo(ErrCode.INVALID_SIGNATURE_BOUNDS);
        assertThat(this.repo.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ONE);
        assertThat(this.repo.getBalance(new Address(recipient))).isEqualTo(BigInteger.ZERO);
    }

    @Test
    public void testTransferZeroBundle() {
        final byte[] senderAddress = members[0].getAddress();
        final byte[] blockHash = HashUtil.h256("blockHash".getBytes());
        final byte[] recipient = HashUtil.h256("recipient".getBytes());
        final byte[] sourceTransactionHash = HashUtil.h256("transaction".getBytes());
        final byte[] aionTransactionHash = HashUtil.h256("aionTransaction".getBytes());

        this.repo.addBalance(CONTRACT_ADDR, BigInteger.ONE);
        BridgeTransfer bundle =
                BridgeTransfer.getInstance(BigInteger.ZERO, recipient, sourceTransactionHash);
        byte[] bundleHash =
                BridgeUtilities.computeBundleHash(blockHash, new BridgeTransfer[] {bundle});

        byte[][] signatures = new byte[5][];
        for (int i = 0; i < 5; i++) {
            signatures[i] = members[i].sign(bundleHash).toBytes();
        }

        ErrCode code = this.controller.setRelayer(OWNER_ADDR.toBytes(), senderAddress);
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);

        BridgeController.ProcessedResults results =
                this.controller.processBundles(
                        senderAddress,
                        aionTransactionHash,
                        blockHash,
                        new BridgeTransfer[] {bundle},
                        signatures);

        assertThat(results).isNotNull();
        assertThat(results.controllerResult).isEqualTo(ErrCode.INVALID_TRANSFER);
        assertThat(this.repo.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ONE);
        assertThat(this.repo.getBalance(new Address(recipient))).isEqualTo(BigInteger.ZERO);
    }

    static class ResultHashTuple {
        byte[] bundleHash;
        BridgeController.ProcessedResults results;
    }

    private ResultHashTuple executeSignController(
            byte[] senderAddress,
            byte[] blockHash,
            byte[] aionTransactionHash,
            BridgeTransfer[] transfers) {

        resetContext();
        byte[] bundleHash = BridgeUtilities.computeBundleHash(blockHash, transfers);
        byte[][] signatures = new byte[members.length][];
        for (int i = 0; i < members.length; i++) {
            signatures[i] = members[i].sign(bundleHash).toBytes();
        }

        ErrCode code = this.controller.setRelayer(OWNER_ADDR.toBytes(), senderAddress);
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);

        BridgeController.ProcessedResults results =
                this.controller.processBundles(
                        senderAddress, aionTransactionHash, blockHash, transfers, signatures);
        ResultHashTuple tuple = new ResultHashTuple();
        tuple.bundleHash = bundleHash;
        tuple.results = results;
        return tuple;
    }

    @Test
    public void testDoubleBundleSend() {
        final byte[] senderAddress = this.members[0].getAddress();
        final byte[] blockHash = HashUtil.h256("blockHash".getBytes());
        final byte[] recipient = HashUtil.h256("recipient".getBytes());
        final byte[] sourceTransactionHash = HashUtil.h256("transaction".getBytes());
        final byte[] aionTransactionHash = HashUtil.h256("aionTransactionHash".getBytes());

        // ensure we have enough balance
        this.repo.addBalance(CONTRACT_ADDR, BigInteger.TWO);

        BridgeTransfer transfer =
                BridgeTransfer.getInstance(BigInteger.ONE, recipient, sourceTransactionHash);
        executeSignController(
                senderAddress, blockHash, aionTransactionHash, new BridgeTransfer[] {transfer});

        final byte[] secondAionTransactionHash =
                HashUtil.h256("secondAionTransactionHash".getBytes());

        // try second time, should still be succesful
        ResultHashTuple tuple =
                executeSignController(
                        senderAddress,
                        blockHash,
                        secondAionTransactionHash,
                        new BridgeTransfer[] {transfer});

        // check status of result
        assertThat(tuple.results.controllerResult).isEqualTo(ErrCode.NO_ERROR);
        assertThat(this.context.getSideEffects().getExecutionLogs().size()).isEqualTo(1);
        assertThat(this.context.getSideEffects().getExecutionLogs().get(0).getTopics().get(0))
                .isEqualTo(BridgeEventSig.SUCCESSFUL_TXHASH.getHashed());
        assertThat(this.context.getSideEffects().getExecutionLogs().get(0).getTopics().get(1))
                .isEqualTo(aionTransactionHash);

        // one transfer should have gone through, second shouldn't
        assertThat(this.repo.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ONE);
        assertThat(this.repo.getBalance(new Address(recipient))).isEqualTo(BigInteger.ONE);
    }

    // Transfer 511 times, ONE per transfer
    // Also thoroughly checks event signatures
    @Test
    public void testBundleMultipleTransferSameRecipient() {
        final byte[] senderAddress = this.members[0].getAddress();
        final byte[] blockHash = HashUtil.h256("blockHash".getBytes());
        final byte[] recipient = HashUtil.h256("recipient".getBytes());
        final byte[] aionTransactionHash = HashUtil.h256("aionTransactionHash".getBytes());

        int transferTotal = 511;
        final BigInteger transferTotalBigInteger = BigInteger.valueOf(transferTotal);

        this.repo.addBalance(CONTRACT_ADDR, transferTotalBigInteger);

        BridgeTransfer[] transfers = new BridgeTransfer[transferTotal];
        for (int i = 0; i < transferTotal; i++) {
            byte[] randomBytes = new byte[32];
            new SecureRandom().nextBytes(randomBytes);
            transfers[i] = BridgeTransfer.getInstance(BigInteger.ONE, recipient, randomBytes);
        }

        ResultHashTuple tuple =
                executeSignController(senderAddress, blockHash, aionTransactionHash, transfers);

        assertThat(tuple.results.controllerResult).isEqualTo(ErrCode.NO_ERROR);
        assertThat(this.repo.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ZERO);
        assertThat(this.repo.getBalance(Address.wrap(recipient)))
                .isEqualTo(transferTotalBigInteger);

        // 511 transfer events + 1 distributed event
        assertThat(this.context.getSideEffects().getExecutionLogs().size()).isEqualTo(512);

        List<IExecutionLog> logs = this.context.getSideEffects().getExecutionLogs();
        for (int i = 0; i < 511; i++) {
            List<byte[]> topics = logs.get(i).getTopics();
            assertThat(topics.get(0)).isEqualTo(BridgeEventSig.DISTRIBUTED.getHashed());
            assertThat(topics.get(1)).isEqualTo(transfers[i].getSourceTransactionHash());
            assertThat(topics.get(2)).isEqualTo(transfers[i].getRecipient());
            assertThat(topics.get(3))
                    .isEqualTo(
                            PrecompiledUtilities.pad(transfers[i].getTransferValueByteArray(), 32));
        }

        // for the last element
        {
            List<byte[]> topics = logs.get(511).getTopics();
            assertThat(topics.get(0)).isEqualTo(BridgeEventSig.PROCESSED_BUNDLE.getHashed());
            assertThat(topics.get(1)).isEqualTo(blockHash);
            assertThat(topics.get(2)).isEqualTo(tuple.bundleHash);
        }
    }
}
