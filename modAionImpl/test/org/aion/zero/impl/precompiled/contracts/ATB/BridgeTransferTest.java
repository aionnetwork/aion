package org.aion.zero.impl.precompiled.contracts.ATB;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.precompiled.contracts.ATB.BridgeTestUtils.dummyContext;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.precompiled.PrecompiledUtilities;
import org.aion.precompiled.contracts.ATB.BridgeController;
import org.aion.precompiled.contracts.ATB.BridgeEventSig;
import org.aion.precompiled.contracts.ATB.BridgeTransfer;
import org.aion.precompiled.contracts.ATB.BridgeUtilities;
import org.aion.precompiled.contracts.ATB.ErrCode;
import org.aion.precompiled.contracts.ATB.TokenBridgeContract;
import org.aion.precompiled.type.CapabilitiesProvider;
import org.aion.precompiled.type.PrecompiledTransactionContext;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.zero.impl.precompiled.ExternalStateForTests;
import org.aion.zero.impl.vm.precompiled.ExternalCapabilitiesForPrecompiled;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class BridgeTransferTest {

    private ExternalStateForTests state;
    private BridgeController controller;
    private TokenBridgeContract contract;
    private PrecompiledTransactionContext context;

    private static final AionAddress CONTRACT_ADDR =
            new AionAddress(HashUtil.h256("contractAddress".getBytes()));
    private static final AionAddress OWNER_ADDR =
            new AionAddress(HashUtil.h256("ownerAddress".getBytes()));

    private static final ECKey members[] =
            new ECKey[] {
                ECKeyFac.inst().create(),
                ECKeyFac.inst().create(),
                ECKeyFac.inst().create(),
                ECKeyFac.inst().create(),
                ECKeyFac.inst().create()
            };

    @BeforeClass
    public static void setupCapabilities() {
        CapabilitiesProvider.installExternalCapabilities(new ExternalCapabilitiesForPrecompiled());
    }

    @AfterClass
    public static void teardownCapabilities() {
        CapabilitiesProvider.removeExternalCapabilities();
    }

    @Before
    public void beforeEach() {
        state = ExternalStateForTests.usingDefaultRepository();
        resetContext();
    }

    private void resetContext() {
        this.context = dummyContext();
        this.contract = new TokenBridgeContract(context, state, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.controller.initialize();

        byte[][] memberList = new byte[members.length][];
        for (int i = 0; i < members.length; i++) {
            memberList[i] = members[i].getAddress();
        }
        this.controller.ringInitialize(OWNER_ADDR.toByteArray(), memberList);
    }

    @Test
    public void testBridgeTransferOne() {
        final byte[] senderAddress = members[0].getAddress();
        final byte[] blockHash = HashUtil.h256("blockHash".getBytes());
        final byte[] recipient = HashUtil.h256("recipient".getBytes());
        final byte[] sourceTransactionHash = HashUtil.h256("transaction".getBytes());
        final byte[] aionTransactionHash = HashUtil.h256("aionTransactionHash".getBytes());

        // ensure we have enough balance
        state.addBalance(CONTRACT_ADDR, BigInteger.ONE);

        BridgeTransfer bundle =
                BridgeTransfer.getInstance(BigInteger.valueOf(1), recipient, sourceTransactionHash);
        byte[] bundleHash =
                BridgeUtilities.computeBundleHash(blockHash, new BridgeTransfer[] {bundle});

        byte[][] signatures = new byte[members.length][];
        for (int i = 0; i < members.length; i++) {
            signatures[i] = members[i].sign(bundleHash).toBytes();
        }

        ErrCode code = this.controller.setRelayer(OWNER_ADDR.toByteArray(), senderAddress);
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
        assertThat(state.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ZERO);
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
        state.addBalance(CONTRACT_ADDR, BigInteger.ONE);

        BridgeTransfer bundle =
                BridgeTransfer.getInstance(BigInteger.valueOf(1), recipient, sourceTransactionHash);
        byte[] bundleHash =
                BridgeUtilities.computeBundleHash(blockHash, new BridgeTransfer[] {bundle});

        byte[][] signatures = new byte[2][];
        for (int i = 0; i < 2; i++) {
            signatures[i] = members[i].sign(bundleHash).toBytes();
        }

        ErrCode code = this.controller.setRelayer(OWNER_ADDR.toByteArray(), senderAddress);
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
        assertThat(state.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ONE);
        assertThat(state.getBalance(new AionAddress(recipient))).isEqualTo(BigInteger.ZERO);
    }

    @Test
    public void testLowerBoundSignature() {
        final byte[] senderAddress = members[0].getAddress();
        final byte[] blockHash = HashUtil.h256("blockHash".getBytes());
        final byte[] recipient = HashUtil.h256("recipient".getBytes());
        final byte[] sourceTransactionHash = HashUtil.h256("transaction".getBytes());
        final byte[] aionTransactionHash = HashUtil.h256("aionTransaction".getBytes());

        // ensure we have enough balance
        state.addBalance(CONTRACT_ADDR, BigInteger.ONE);

        BridgeTransfer bundle =
                BridgeTransfer.getInstance(BigInteger.valueOf(1), recipient, sourceTransactionHash);
        byte[] bundleHash =
                BridgeUtilities.computeBundleHash(blockHash, new BridgeTransfer[] {bundle});

        byte[][] signatures = new byte[3][];
        for (int i = 0; i < 3; i++) {
            signatures[i] = members[i].sign(bundleHash).toBytes();
        }

        ErrCode code = this.controller.setRelayer(OWNER_ADDR.toByteArray(), senderAddress);
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
        assertThat(state.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ZERO);
        assertThat(state.getBalance(new AionAddress(recipient))).isEqualTo(BigInteger.ONE);
    }

    @Test
    public void testBeyondUpperBoundSignatures() {
        final byte[] senderAddress = members[0].getAddress();
        final byte[] blockHash = HashUtil.h256("blockHash".getBytes());
        final byte[] recipient = HashUtil.h256("recipient".getBytes());
        final byte[] sourceTransactionHash = HashUtil.h256("transaction".getBytes());
        final byte[] aionTransactionHash = HashUtil.h256("aionTransaction".getBytes());

        // ensure we have enough balance
        state.addBalance(CONTRACT_ADDR, BigInteger.ONE);

        BridgeTransfer bundle =
                BridgeTransfer.getInstance(BigInteger.valueOf(1), recipient, sourceTransactionHash);
        byte[] bundleHash =
                BridgeUtilities.computeBundleHash(blockHash, new BridgeTransfer[] {bundle});

        byte[][] signatures = new byte[6][];
        for (int i = 0; i < 5; i++) {
            signatures[i] = members[i].sign(bundleHash).toBytes();
        }

        ErrCode code = this.controller.setRelayer(OWNER_ADDR.toByteArray(), senderAddress);
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
        assertThat(state.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ONE);
        assertThat(state.getBalance(new AionAddress(recipient))).isEqualTo(BigInteger.ZERO);
    }

    @Test
    public void testTransferZeroBundle() {
        final byte[] senderAddress = members[0].getAddress();
        final byte[] blockHash = HashUtil.h256("blockHash".getBytes());
        final byte[] recipient = HashUtil.h256("recipient".getBytes());
        final byte[] sourceTransactionHash = HashUtil.h256("transaction".getBytes());
        final byte[] aionTransactionHash = HashUtil.h256("aionTransaction".getBytes());

        state.addBalance(CONTRACT_ADDR, BigInteger.ONE);
        BridgeTransfer bundle =
                BridgeTransfer.getInstance(BigInteger.ZERO, recipient, sourceTransactionHash);
        byte[] bundleHash =
                BridgeUtilities.computeBundleHash(blockHash, new BridgeTransfer[] {bundle});

        byte[][] signatures = new byte[5][];
        for (int i = 0; i < 5; i++) {
            signatures[i] = members[i].sign(bundleHash).toBytes();
        }

        ErrCode code = this.controller.setRelayer(OWNER_ADDR.toByteArray(), senderAddress);
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
        assertThat(state.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ONE);
        assertThat(state.getBalance(new AionAddress(recipient))).isEqualTo(BigInteger.ZERO);
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

        ErrCode code = this.controller.setRelayer(OWNER_ADDR.toByteArray(), senderAddress);
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
        final byte[] senderAddress = members[0].getAddress();
        final byte[] blockHash = HashUtil.h256("blockHash".getBytes());
        final byte[] recipient = HashUtil.h256("recipient".getBytes());
        final byte[] sourceTransactionHash = HashUtil.h256("transaction".getBytes());
        final byte[] aionTransactionHash = HashUtil.h256("aionTransactionHash".getBytes());

        // ensure we have enough balance
        state.addBalance(CONTRACT_ADDR, BigInteger.TWO);

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
        assertThat(this.context.getLogs().size()).isEqualTo(1);
        assertThat(this.context.getLogs().get(0).copyOfTopics().get(0))
                .isEqualTo(BridgeEventSig.SUCCESSFUL_TXHASH.getHashed());
        assertThat(this.context.getLogs().get(0).copyOfTopics().get(1))
                .isEqualTo(aionTransactionHash);

        // one transfer should have gone through, second shouldn't
        assertThat(state.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ONE);
        assertThat(state.getBalance(new AionAddress(recipient))).isEqualTo(BigInteger.ONE);
    }

    // Transfer 511 times, ONE per transfer
    // Also thoroughly checks event signatures
    @Test
    public void testBundleMultipleTransferSameRecipient() {
        final byte[] senderAddress = members[0].getAddress();
        final byte[] blockHash = HashUtil.h256("blockHash".getBytes());
        final byte[] recipient = HashUtil.h256("recipient".getBytes());
        final byte[] aionTransactionHash = HashUtil.h256("aionTransactionHash".getBytes());

        int transferTotal = 511;
        final BigInteger transferTotalBigInteger = BigInteger.valueOf(transferTotal);

        state.addBalance(CONTRACT_ADDR, transferTotalBigInteger);

        BridgeTransfer[] transfers = new BridgeTransfer[transferTotal];
        for (int i = 0; i < transferTotal; i++) {
            byte[] randomBytes = new byte[32];
            new SecureRandom().nextBytes(randomBytes);
            transfers[i] = BridgeTransfer.getInstance(BigInteger.ONE, recipient, randomBytes);
        }

        ResultHashTuple tuple =
                executeSignController(senderAddress, blockHash, aionTransactionHash, transfers);

        assertThat(tuple.results.controllerResult).isEqualTo(ErrCode.NO_ERROR);
        assertThat(state.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ZERO);
        assertThat(state.getBalance(new AionAddress(recipient)))
                .isEqualTo(transferTotalBigInteger);

        // 511 transfer events + 1 distributed event
        assertThat(this.context.getLogs().size()).isEqualTo(512);

        List<Log> logs = this.context.getLogs();
        for (int i = 0; i < 511; i++) {
            List<byte[]> topics = logs.get(i).copyOfTopics();
            assertThat(topics.get(0)).isEqualTo(BridgeEventSig.DISTRIBUTED.getHashed());
            assertThat(topics.get(1)).isEqualTo(transfers[i].getSourceTransactionHash());
            assertThat(topics.get(2)).isEqualTo(transfers[i].getRecipient());
            assertThat(topics.get(3))
                    .isEqualTo(
                            PrecompiledUtilities.pad(transfers[i].getTransferValueByteArray(), 32));
        }

        // for the last element
        {
            List<byte[]> topics = logs.get(511).copyOfTopics();
            assertThat(topics.get(0)).isEqualTo(BridgeEventSig.PROCESSED_BUNDLE.getHashed());
            assertThat(topics.get(1)).isEqualTo(blockHash);
            assertThat(topics.get(2)).isEqualTo(tuple.bundleHash);
        }
    }
}
