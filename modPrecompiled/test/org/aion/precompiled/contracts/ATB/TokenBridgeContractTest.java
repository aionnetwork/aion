package org.aion.precompiled.contracts.ATB;

import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.precompiled.DummyRepo;
import org.aion.precompiled.PrecompiledUtilities;
import org.aion.precompiled.encoding.AbiEncoder;
import org.aion.precompiled.encoding.AddressFVM;
import org.aion.precompiled.encoding.ListFVM;
import org.aion.precompiled.encoding.Uint128FVM;
import org.aion.vm.AbstractExecutionResult;
import org.aion.vm.ExecutionResult;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.precompiled.contracts.ATB.BridgeTestUtils.context;
import static org.aion.precompiled.contracts.ATB.BridgeTestUtils.dummyContext;

/**
 * TokenBridgeContract is coupled with a serialization layer, this is
 * mainly dedicated to testing that functionality
 */
public class TokenBridgeContractTest {
    private TokenBridgeContract contract;
    private BridgeController controller;
    private BridgeStorageConnector connector;
    private DummyRepo repository;

    private static final ECKey members[] = new ECKey[] {
            ECKeyFac.inst().create(),
            ECKeyFac.inst().create(),
            ECKeyFac.inst().create(),
            ECKeyFac.inst().create(),
            ECKeyFac.inst().create()
    };

    private static final Address CONTRACT_ADDR = new Address(HashUtil.h256("contractAddress".getBytes()));
    private static final Address OWNER_ADDR = new Address(HashUtil.h256("ownerAddress".getBytes()));

    private static final long DEFAULT_NRG = 21000L;

    @Before
    public void before() {
        this.repository = new DummyRepo();
        // override defaults
        this.contract = new TokenBridgeContract(dummyContext(),
                this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();
    }

    @Test
    public void testNotEnoughEnergyExecution() {

    }

    @Test
    public void testGetOwner() {
        ExecutionResult result = this.contract.execute(BridgeFuncSig.PURE_OWNER.getBytes(), DEFAULT_NRG);
        assertThat(result.getOutput()).isEqualTo(OWNER_ADDR.toBytes());
        assertThat(result.getNrgLeft()).isEqualTo(0L);
    }

    @Test
    public void testGetNewOwner() {
        // override defaults
        this.contract = new TokenBridgeContract(context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        byte[] newOwner = AddressSpecs.computeA0Address(HashUtil.h256("newOwner".getBytes()));

        byte[] payload = new AbiEncoder(
                BridgeFuncSig.SIG_CHANGE_OWNER.getSignature(), new AddressFVM(new ByteArrayWrapper(newOwner))).encodeBytes();
        System.out.println("encoded payload: " + ByteUtil.toHexString(payload));

        ExecutionResult setResult = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(setResult.getResultCode()).isEqualTo(AbstractExecutionResult.ResultCode.SUCCESS);

        ExecutionResult result = this.contract.execute(BridgeFuncSig.PURE_NEW_OWNER.getBytes(), DEFAULT_NRG);
        assertThat(result.getOutput()).isEqualTo(newOwner);
        assertThat(result.getNrgLeft()).isEqualTo(0L);
    }

    @Test
    public void testInitializeRing() {
        // override defaults
        this.contract = new TokenBridgeContract(context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }
        byte[] payload = new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList).encodeBytes();
        ExecutionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(AbstractExecutionResult.ResultCode.SUCCESS);

        // pull results from controller
        for (ECKey k : members) {
            assertThat(this.connector.getActiveMember(k.getAddress())).isTrue();
        }
    }

    @Test
    public void testTransfer() {
        // override defaults
        this.contract = new TokenBridgeContract(context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }
        byte[] payload = new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList).encodeBytes();
        ExecutionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(AbstractExecutionResult.ResultCode.SUCCESS);

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);
        this.contract = new TokenBridgeContract(context(new Address(members[0].getAddress()),
                CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = HashUtil.h256("blockHash".getBytes());

        BridgeBundle[] bundles = new BridgeBundle[10];
        for (int i = 0; i < 10; i++) {
            bundles[i] = new BridgeBundle(BigInteger.ONE,
                    AddressSpecs.computeA0Address(HashUtil.h256(Integer.toHexString(i).getBytes())));
        }
        byte[] payloadHash = generateSignature(blockHash, bundles);

        byte[][] signatures = new byte[members.length][];
        int i = 0;
        for (ECKey k : members) {
            signatures[i] = k.sign(payloadHash).toBytes();
            i++;
        }

        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeBundle b : bundles) {
            addressList.add(new AddressFVM(new ByteArrayWrapper(b.recipient)));
            uintList.add(new Uint128FVM(new ByteArrayWrapper(PrecompiledUtilities.pad(b.transferValue.toByteArray(), 16))));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 0, 32))));
            sigChunk2.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 32, 64))));
            sigChunk3.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 64, 96))));
        }

        byte[] callPayload = new AbiEncoder(BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                new AddressFVM(new ByteArrayWrapper(blockHash)),
                addressList,
                uintList,
                sigChunk1,
                sigChunk2,
                sigChunk3).encodeBytes();
        ExecutionResult transferResult = this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(AbstractExecutionResult.ResultCode.SUCCESS);
    }

    private static byte[] generateSignature(byte[] blockHash, BridgeBundle[] bundles) {
        byte[] hash = HashUtil.h256(blockHash);
        for (BridgeBundle b : bundles) {
            hash = HashUtil.h256(ByteUtil.merge(hash, b.recipient, b.transferValue.toByteArray()));
        }
        return hash;
    }
}
