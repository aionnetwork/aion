package org.aion.precompiled.contracts.ATB;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.precompiled.DummyRepo;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.precompiled.contracts.ATB.BridgeTestUtils.dummyContext;

public class BridgeTransferTest {

    private DummyRepo repo;
    private BridgeStorageConnector connector;
    private BridgeController controller;
    private TokenBridgeContract contract;

    private static final Address CONTRACT_ADDR = new Address(HashUtil.h256("contractAddress".getBytes()));
    private static final Address OWNER_ADDR = new Address(HashUtil.h256("ownerAddress".getBytes()));

    private static final ECKey members[] = new ECKey[] {
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
        this.repo = new DummyRepo();
        this.contract = new TokenBridgeContract(dummyContext(), repo, OWNER_ADDR, CONTRACT_ADDR);
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

        // ensure we have enough balance
        this.repo.addBalance(CONTRACT_ADDR, BigInteger.ONE);

        BridgeBundle bundle = new BridgeBundle(BigInteger.valueOf(1), recipient);
        byte[] bundleHash = generateSignature(blockHash, new BridgeBundle[] {bundle});

        byte[][] signatures = new byte[members.length][];
        for (int i = 0; i < members.length; i++) {
            signatures[i] = members[i].sign(bundleHash).toBytes();
        }

        ErrCode code = this.controller.setRelayer(OWNER_ADDR.toBytes(), senderAddress);
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);

        BridgeController.ProcessedResults results =
                this.controller.processBundles(senderAddress, blockHash, new BridgeBundle[] {bundle}, signatures);
        assertThat(results).isNotNull();
        assertThat(results.controllerResult).isEqualTo(ErrCode.NO_ERROR);
        assertThat(this.repo.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ZERO);
    }

    @Test
    public void testBridgeNotEnoughSignatures() {
        final byte[] senderAddress = this.members[0].getAddress();
        final byte[] blockHash = HashUtil.h256("blockHash".getBytes());
        final byte[] recipient = HashUtil.h256("recipient".getBytes());

        // ensure we have enough balance
        this.repo.addBalance(CONTRACT_ADDR, BigInteger.ONE);

        BridgeBundle bundle = new BridgeBundle(BigInteger.valueOf(1), recipient);
        byte[] bundleHash = generateSignature(blockHash, new BridgeBundle[] {bundle});

        byte[][] signatures = new byte[2][];
        for (int i = 0; i < 2; i++) {
            signatures[i] = members[i].sign(bundleHash).toBytes();
        }

        ErrCode code = this.controller.setRelayer(OWNER_ADDR.toBytes(), senderAddress);
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);

        BridgeController.ProcessedResults results =
                this.controller.processBundles(senderAddress, blockHash, new BridgeBundle[] {bundle}, signatures);

        assertThat(results).isNotNull();
        assertThat(results.controllerResult).isEqualTo(ErrCode.INVALID_SIGNATURE_BOUNDS);
        assertThat(this.repo.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ONE);
        assertThat(this.repo.getBalance(new Address(recipient))).isEqualTo(BigInteger.ZERO);
    }

    @Test
    public void testLowerBoundSignature() {
        final byte[] senderAddress = this.members[0].getAddress();
        final byte[] blockHash = HashUtil.h256("blockHash".getBytes());
        final byte[] recipient = HashUtil.h256("recipient".getBytes());

        // ensure we have enough balance
        this.repo.addBalance(CONTRACT_ADDR, BigInteger.ONE);

        BridgeBundle bundle = new BridgeBundle(BigInteger.valueOf(1), recipient);
        byte[] bundleHash = generateSignature(blockHash, new BridgeBundle[] {bundle});

        byte[][] signatures = new byte[3][];
        for (int i = 0; i < 3; i++) {
            signatures[i] = members[i].sign(bundleHash).toBytes();
        }

        ErrCode code = this.controller.setRelayer(OWNER_ADDR.toBytes(), senderAddress);
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);

        BridgeController.ProcessedResults results =
                this.controller.processBundles(senderAddress, blockHash, new BridgeBundle[] {bundle}, signatures);
        assertThat(results).isNotNull();
        assertThat(results.controllerResult).isEqualTo(ErrCode.NO_ERROR);
        assertThat(this.repo.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ZERO);
        assertThat(this.repo.getBalance(new Address(recipient))).isEqualTo(BigInteger.ONE);
    }

    @Test
    public void testBeyondUpperBoundSignatures() {
        final byte[] senderAddress = this.members[0].getAddress();
        final byte[] blockHash = HashUtil.h256("blockHash".getBytes());
        final byte[] recipient = HashUtil.h256("recipient".getBytes());

        // ensure we have enough balance
        this.repo.addBalance(CONTRACT_ADDR, BigInteger.ONE);

        BridgeBundle bundle = new BridgeBundle(BigInteger.valueOf(1), recipient);
        byte[] bundleHash = generateSignature(blockHash, new BridgeBundle[] {bundle});

        byte[][] signatures = new byte[6][];
        for (int i = 0; i < 5; i++) {
            signatures[i] = members[i].sign(bundleHash).toBytes();
        }

        ErrCode code = this.controller.setRelayer(OWNER_ADDR.toBytes(), senderAddress);
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);

        BridgeController.ProcessedResults results =
                this.controller.processBundles(senderAddress, blockHash, new BridgeBundle[] {bundle}, signatures);
        assertThat(results).isNotNull();
        assertThat(results.controllerResult).isEqualTo(ErrCode.INVALID_SIGNATURE_BOUNDS);
        assertThat(this.repo.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ONE);
        assertThat(this.repo.getBalance(new Address(recipient))).isEqualTo(BigInteger.ZERO);
    }

    private static byte[] generateSignature(byte[] blockHash, BridgeBundle[] bundles) {
        byte[] hash = HashUtil.h256(blockHash);
        for (BridgeBundle b : bundles) {
            hash = HashUtil.h256(ByteUtil.merge(hash, b.recipient, b.transferValue.toByteArray()));
        }
        return hash;
    }
}
