package org.aion.precompiled.contracts.ATB;

import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.mcf.vm.types.Log;
import org.aion.precompiled.DummyRepo;
import org.aion.precompiled.PrecompiledUtilities;
import org.aion.precompiled.encoding.AbiEncoder;
import org.aion.precompiled.encoding.AddressFVM;
import org.aion.precompiled.encoding.ListFVM;
import org.aion.precompiled.encoding.Uint128FVM;
import org.aion.vm.AbstractExecutionResult;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionResult;
import org.aion.zero.types.AionInternalTx;
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
        ExecutionContext initializationContext = context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract = new TokenBridgeContract(initializationContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }
        byte[] payload = new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList).encodeBytes();
        ExecutionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(AbstractExecutionResult.ResultCode.SUCCESS);

        //set relayer
        byte[] callPayload = new AbiEncoder(BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                new AddressFVM(new ByteArrayWrapper(members[0].getAddress()))).encodeBytes();

        ExecutionResult transferResult = this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(AbstractExecutionResult.ResultCode.SUCCESS);

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        ExecutionContext submitBundleContext = context(new Address(members[0].getAddress()),
                CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract = new TokenBridgeContract(submitBundleContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = HashUtil.h256("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[10];
        for (int i = 0; i < 10; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
            transfers[i] = new BridgeTransfer(BigInteger.ONE,
                    AddressSpecs.computeA0Address(HashUtil.h256(Integer.toHexString(i).getBytes())),
                    sourceTransactionHash);
        }
        byte[] payloadHash = BridgeUtilities.computeBundleHash(blockHash, transfers);

        // ATB-4, do one assert here to check that transactionHash is not set
        assertThat(this.contract.execute(
                ByteUtil.merge(BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                        payloadHash), 21000L).getOutput())
                .isEqualTo(ByteUtil.EMPTY_WORD);

        byte[][] signatures = new byte[members.length][];
        int i = 0;
        for (ECKey k : members) {
            signatures[i] = k.sign(payloadHash).toBytes();
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(new AddressFVM(new ByteArrayWrapper(b.getSourceTransactionHash())));
            addressList.add(new AddressFVM(new ByteArrayWrapper(b.getRecipient())));
            uintList.add(new Uint128FVM(new ByteArrayWrapper(PrecompiledUtilities.pad(b.getTransferValue().toByteArray(), 16))));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 0, 32))));
            sigChunk2.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 32, 64))));
            sigChunk3.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 64, 96))));
        }

        callPayload = new AbiEncoder(BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                new AddressFVM(new ByteArrayWrapper(blockHash)),
                sourceTransactionList,
                addressList,
                uintList,
                sigChunk1,
                sigChunk2,
                sigChunk3).encodeBytes();
        transferResult = this.contract.execute(callPayload, DEFAULT_NRG);


        /// VERIFICATION

        // ATB-4 assert that transactionHash is now properly set
        // ATB-4, do one assert here to check that transactionHash is not set
        assertThat(this.contract.execute(
                ByteUtil.merge(BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                        payloadHash), 21000L).getOutput())
                .isEqualTo(submitBundleContext.transactionHash());

        assertThat(transferResult.getResultCode()).isEqualTo(AbstractExecutionResult.ResultCode.SUCCESS);

        for (BridgeTransfer b : transfers) {
            assertThat(this.repository.getBalance(new Address(b.getRecipient()))).isEqualTo(BigInteger.ONE);
        }
        assertThat(this.repository.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ZERO);

        // context verification
        // we expect on successful output:
        // 10 internal transactions (that all succeed)
        // 10 Distributed events
        // 1  ProcessedBundle Event
        assertThat(submitBundleContext.helper().getInternalTransactions().size()).isEqualTo(10);
        i = 0;
        for (AionInternalTx tx : submitBundleContext.helper().getInternalTransactions()) {

            // verify the internal transaction is not rejected
            assertThat(tx.isRejected()).isFalse();

            // verify the from is the contract address
            assertThat(tx.getFrom()).isEqualTo(CONTRACT_ADDR);

            // verify that we sent the correct amount
            assertThat(new BigInteger(1, tx.getValue()).intValueExact()).isEqualTo(1);

            // verify that the recipient is what we intended (in the order we submitted)
            assertThat(tx.getTo()).isEqualTo(new Address(transfers[i].getRecipient()));
            i++;
        }

        // check that proper events are emit
        assertThat(submitBundleContext.helper().getLogs().size()).isEqualTo(11);
        i = 0;
        for (Log l : submitBundleContext.helper().getLogs()) {
            // verify address is correct
            assertThat(l.getAddress()).isEqualTo(CONTRACT_ADDR);

            // on the 11th log, it should be the processed bundle event
            if (i == 10) {
                assertThat(l.getTopics().get(0)).isEqualTo(BridgeEventSig.PROCESSED_BUNDLE.getHashed());
                assertThat(l.getTopics().get(1)).isEqualTo(blockHash);
                assertThat(l.getTopics().get(2)).isEqualTo(payloadHash);
                continue;
            }

            // otherwise we expect a Distributed event
            assertThat(l.getTopics().get(0)).isEqualTo(BridgeEventSig.DISTRIBUTED.getHashed());
            assertThat(l.getTopics().get(1)).isEqualTo(transfers[i].getSourceTransactionHash());
            assertThat(l.getTopics().get(2)).isEqualTo(transfers[i].getRecipient());
            assertThat(new BigInteger(1, l.getTopics().get(3))).isEqualTo(transfers[i].getTransferValue());
            i++;
        }
    }

    @Test
    public void testTransfersGreaterThanMaxListSize() {
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

        //set relayer
        byte[] callPayload = new AbiEncoder(BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                new AddressFVM(new ByteArrayWrapper(members[0].getAddress()))).encodeBytes();

        ExecutionResult transferResult = this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(AbstractExecutionResult.ResultCode.SUCCESS);

        // override defaults
        ExecutionContext submitBundleContext = context(new Address(members[0].getAddress()),
                CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY);
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.valueOf(1024));
        this.contract = new TokenBridgeContract(submitBundleContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = HashUtil.h256("blockHash".getBytes());

        // place a value greater than the maximum 1024
        BridgeTransfer[] transfers = new BridgeTransfer[1024];
        for (int i = 0; i < 1024; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
            transfers[i] = new BridgeTransfer(BigInteger.ONE,
                    AddressSpecs.computeA0Address(HashUtil.h256(Integer.toHexString(i).getBytes())),
                    sourceTransactionHash);
        }
        byte[] payloadHash = BridgeUtilities.computeBundleHash(blockHash, transfers);
        byte[][] signatures = new byte[members.length][];
        int i = 0;
        for (ECKey k : members) {
            signatures[i] = k.sign(payloadHash).toBytes();
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(new AddressFVM(new ByteArrayWrapper(b.getSourceTransactionHash())));
            addressList.add(new AddressFVM(new ByteArrayWrapper(b.getRecipient())));
            uintList.add(new Uint128FVM(new ByteArrayWrapper(PrecompiledUtilities.pad(b.getTransferValue().toByteArray(), 16))));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 0, 32))));
            sigChunk2.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 32, 64))));
            sigChunk3.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 64, 96))));
        }

        callPayload = new AbiEncoder(BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                new AddressFVM(new ByteArrayWrapper(blockHash)),
                sourceTransactionList,
                addressList,
                uintList,
                sigChunk1,
                sigChunk2,
                sigChunk3).encodeBytes();
        transferResult = this.contract.execute(callPayload, DEFAULT_NRG);

        /// VERIFICATION

        assertThat(transferResult.getResultCode()).isEqualTo(AbstractExecutionResult.ResultCode.FAILURE);

        for (BridgeTransfer b : transfers) {
            assertThat(this.repository.getBalance(new Address(b.getRecipient()))).isEqualTo(BigInteger.ZERO);
        }
        assertThat(this.repository.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.valueOf(1024));

        assertThat(submitBundleContext.helper().getInternalTransactions()).isEmpty();
        assertThat(submitBundleContext.helper().getLogs()).isEmpty();
    }

    @Test
    public void testBundleHash() {

    }
}
