package org.aion.precompiled.contracts.ATB;

import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.HashUtil;
import org.aion.precompiled.DummyRepo;
import org.aion.precompiled.encoding.AbiEncoder;
import org.aion.precompiled.encoding.AddressFVM;
import org.aion.vm.AbstractExecutionResult;
import org.aion.vm.ExecutionResult;
import org.junit.Before;
import org.junit.Test;

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
    }
}
