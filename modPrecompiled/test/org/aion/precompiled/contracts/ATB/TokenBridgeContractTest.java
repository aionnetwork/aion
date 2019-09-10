package org.aion.precompiled.contracts.ATB;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.precompiled.contracts.ATB.BridgeTestUtils.context;
import static org.aion.precompiled.contracts.ATB.BridgeTestUtils.dummyContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.aion.precompiled.ExternalCapabilitiesForTesting;
import org.aion.precompiled.RepositoryForPrecompiled;
import org.aion.precompiled.type.CapabilitiesProvider;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.precompiled.PrecompiledUtilities;
import org.aion.precompiled.ExternalStateForTests;
import org.aion.precompiled.encoding.AbiEncoder;
import org.aion.precompiled.encoding.AddressFVM;
import org.aion.precompiled.encoding.ListFVM;
import org.aion.precompiled.encoding.Uint128FVM;
import org.aion.precompiled.type.PrecompiledTransactionContext;
import org.aion.precompiled.util.AddressUtils;
import org.aion.precompiled.util.ByteUtil;
import org.aion.precompiled.util.DataWord;
import org.aion.types.TransactionStatus;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.types.Log;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * TokenBridgeContract is coupled with a serialization layer, this is mainly dedicated to testing
 * that functionality
 */
public class TokenBridgeContractTest {
    private TokenBridgeContract contract;
    private BridgeStorageConnector connector;
    private RepositoryForPrecompiled repository;

    private static byte[][] members = new byte[5][32];
    private static Random r = new Random();

    private static final long DEFAULT_NRG = 21000L;
    private static ExternalCapabilitiesForTesting capabilities;

    private static AionAddress CONTRACT_ADDR;
    private static AionAddress OWNER_ADDR;

    @BeforeClass
    public static void setupCapabilities() {
        capabilities = new ExternalCapabilitiesForTesting();
        CapabilitiesProvider.installExternalCapabilities(capabilities);
        CONTRACT_ADDR = new AionAddress(capabilities.blake2b("contractAddress".getBytes()));
        OWNER_ADDR = new AionAddress(capabilities.blake2b("ownerAddress".getBytes()));

        for (int i = 0; i < members.length; i++) {
            members[i] = getRandomAddr();
        }
    }

    @AfterClass
    public static void teardownCapabilities() {
        CapabilitiesProvider.removeExternalCapabilities();
    }

    @Before
    public void before() {
        this.repository = new RepositoryForPrecompiled();
        // override defaults
        this.contract =
                new TokenBridgeContract(dummyContext(), ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();
    }

    @Test
    public void testNotEnoughEnergyExecution() {
        assertThat(this.connector.getInitialized()).isFalse();
        this.contract.execute(BridgeFuncSig.PURE_OWNER.getBytes(), 20_000L);
        assertThat(this.connector.getInitialized()).isFalse();
    }

    @Test
    public void testGetOwner() {
        assertThat(this.connector.getInitialized()).isFalse();
        PrecompiledTransactionResult result =
                this.contract.execute(BridgeFuncSig.PURE_OWNER.getBytes(), DEFAULT_NRG);
        assertThat(result.getReturnData()).isEqualTo(OWNER_ADDR.toByteArray());
        assertThat(result.getEnergyRemaining()).isEqualTo(0L);
        assertThat(this.connector.getInitialized()).isTrue();
    }

    @Test
    public void testGetNewOwner() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        byte[] newOwner = capabilities.computeA0Address(capabilities.blake2b("newOwner".getBytes()));
        byte[] payload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_CHANGE_OWNER.getSignature(),
                                new AddressFVM(newOwner))
                        .encodeBytes();
        System.out.println("encoded payload: " + ByteUtil.toHexString(payload));

        assertThat(this.connector.getInitialized()).isFalse();
        PrecompiledTransactionResult setResult = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(this.connector.getInitialized()).isTrue();
        assertTrue(setResult.getStatus().isSuccess());

        PrecompiledTransactionResult result =
                this.contract.execute(BridgeFuncSig.PURE_NEW_OWNER.getBytes(), DEFAULT_NRG);
        assertThat(result.getReturnData()).isEqualTo(newOwner);
        assertThat(result.getEnergyRemaining()).isEqualTo(0L);
    }

    @Test
    public void testGetNewOwnerNotOwnerAddress() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(AddressUtils.ZERO_ADDRESS, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        byte[] newOwner = capabilities.computeA0Address(capabilities.blake2b("newOwner".getBytes()));

        byte[] payload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_CHANGE_OWNER.getSignature(),
                                new AddressFVM(newOwner))
                        .encodeBytes();
        System.out.println("encoded payload: " + ByteUtil.toHexString(payload));

        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals("FAILURE", result.getStatus().causeOfError);
    }

    @Test
    public void testInitializeRing() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());

        // pull results from controller
        for (byte[] k : members) {
            assertThat(this.connector.getActiveMember(k)).isTrue();
        }
    }

    @Test
    public void testInitializeRingNotOwner() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(AddressUtils.ZERO_ADDRESS, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals("FAILURE", result.getStatus().causeOfError);
    }

    @Test
    public void testTransfer() {
        // override defaults
        PrecompiledTransactionContext initializationContext = context(OWNER_ADDR, CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(members[0]))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertTrue(transferResult.getStatus().isSuccess());

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(new AionAddress(members[0]), CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = capabilities.blake2b("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[10];
        for (int i = 0; i < 10; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = capabilities.blake2b(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            capabilities.computeA0Address(
                                    capabilities.blake2b(Integer.toHexString(i).getBytes())),
                            sourceTransactionHash);
        }

        byte[] payloadHash = BridgeUtilities.computeBundleHash(blockHash, transfers);

        // ATB-4, do one assert here to check that transactionHash is not set
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(ByteUtil.EMPTY_WORD);

        byte[][] signatures = new byte[members.length][];
        int i = 0;
        for (byte[] k : members) {
            signatures[i] = capabilities.sign(k, payloadHash);
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(b.getSourceTransactionHash()));
            addressList.add(new AddressFVM(b.getRecipient()));
            uintList.add(
                    new Uint128FVM(
                            PrecompiledUtilities.pad(
                                    b.getTransferValue().toByteArray(), 16)));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(Arrays.copyOfRange(sig, 0, 32)));
            sigChunk2.add(new AddressFVM(Arrays.copyOfRange(sig, 32, 64)));
            sigChunk3.add(new AddressFVM(Arrays.copyOfRange(sig, 64, 96)));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(blockHash),
                                sourceTransactionList,
                                addressList,
                                uintList,
                                sigChunk1,
                                sigChunk2,
                                sigChunk3)
                        .encodeBytes();
        transferResult = this.contract.execute(callPayload, DEFAULT_NRG);

        /// VERIFICATION

        // ATB-4 assert that transactionHash is now properly set
        // ATB-4, do one assert here to check that transactionHash is not set
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(submitBundleContext.copyOfTransactionHash());

        assertTrue(transferResult.getStatus().isSuccess());

        for (BridgeTransfer b : transfers) {
            assertThat(this.repository.getBalance(new AionAddress(b.getRecipient())))
                    .isEqualTo(BigInteger.ONE);
        }
        assertThat(this.repository.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ZERO);

        // context verification
        // we expect on successful output:
        // 10 internal transactions (that all succeed)
        // 10 Distributed events
        // 1  ProcessedBundle Event
        assertThat(submitBundleContext.getInternalTransactions().size()).isEqualTo(10);
        i = 0;
        for (InternalTransaction tx : submitBundleContext.getInternalTransactions()) {

            // verify the internal transaction is not rejected
            assertThat(tx.isRejected).isFalse();

            // verify the from is the contract address
            assertThat(tx.sender).isEqualTo(CONTRACT_ADDR);

            // verify that we sent the correct amount
            assertThat(tx.value.intValueExact()).isEqualTo(1);

            // verify that the recipient is what we intended (in the order we submitted)
            assertThat(tx.destination).isEqualTo(new AionAddress(transfers[i].getRecipient()));
            i++;
        }

        // check that proper events are emit
        assertThat(submitBundleContext.getLogs().size()).isEqualTo(11);
        i = 0;
        for (Log l : submitBundleContext.getLogs()) {
            // verify address is correct
            assertThat(l.copyOfAddress()).isEqualTo(CONTRACT_ADDR.toByteArray());
            List<byte[]> topics = l.copyOfTopics();

            // on the 11th log, it should be the processed bundle event
            if (i == 10) {
                assertThat(topics.get(0)).isEqualTo(BridgeEventSig.PROCESSED_BUNDLE.getHashed());
                assertThat(topics.get(1)).isEqualTo(blockHash);
                assertThat(topics.get(2)).isEqualTo(payloadHash);
                continue;
            }

            // otherwise we expect a Distributed event
            assertThat(topics.get(0)).isEqualTo(BridgeEventSig.DISTRIBUTED.getHashed());
            assertThat(topics.get(1)).isEqualTo(transfers[i].getSourceTransactionHash());
            assertThat(topics.get(2)).isEqualTo(transfers[i].getRecipient());
            assertThat(new BigInteger(1, topics.get(3))).isEqualTo(transfers[i].getTransferValue());
            i++;
        }
    }

    @Test
    public void testNonA0AddressTransfer() {
        // override defaults
        PrecompiledTransactionContext initializationContext = context(OWNER_ADDR, CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(members[0]))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertTrue(transferResult.getStatus().isSuccess());

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(new AionAddress(members[0]), CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = capabilities.blake2b("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[10];
        for (int i = 0; i < 10; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = capabilities.blake2b(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            capabilities.blake2b(Integer.toHexString(i).getBytes()),
                            sourceTransactionHash);
        }

        byte[] payloadHash = BridgeUtilities.computeBundleHash(blockHash, transfers);

        // ATB-4, do one assert here to check that transactionHash is not set
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(ByteUtil.EMPTY_WORD);

        byte[][] signatures = new byte[members.length][];
        int i = 0;
        for (byte[] k : members) {
            signatures[i] = capabilities.sign(k, payloadHash);
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(b.getSourceTransactionHash()));
            addressList.add(new AddressFVM(b.getRecipient()));
            uintList.add(
                    new Uint128FVM(
                            PrecompiledUtilities.pad(
                                    b.getTransferValue().toByteArray(), 16)));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(Arrays.copyOfRange(sig, 0, 32)));
            sigChunk2.add(new AddressFVM(Arrays.copyOfRange(sig, 32, 64)));
            sigChunk3.add(new AddressFVM(Arrays.copyOfRange(sig, 64, 96)));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(blockHash),
                                sourceTransactionList,
                                addressList,
                                uintList,
                                sigChunk1,
                                sigChunk2,
                                sigChunk3)
                        .encodeBytes();
        transferResult = this.contract.execute(callPayload, DEFAULT_NRG);

        /// VERIFICATION

        // ATB-4 assert that transactionHash is now properly set
        // ATB-4, do one assert here to check that transactionHash is not set
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(submitBundleContext.copyOfTransactionHash());

        assertTrue(transferResult.getStatus().isSuccess());

        for (BridgeTransfer b : transfers) {
            assertThat(this.repository.getBalance(new AionAddress(b.getRecipient())))
                    .isEqualTo(BigInteger.ONE);
        }
        assertThat(this.repository.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ZERO);

        // context verification
        // we expect on successful output:
        // 10 internal transactions (that all succeed)
        // 10 Distributed events
        // 1  ProcessedBundle Event
        assertThat(submitBundleContext.getInternalTransactions().size()).isEqualTo(10);
        i = 0;
        for (InternalTransaction tx : submitBundleContext.getInternalTransactions()) {

            // verify the internal transaction is not rejected
            assertThat(tx.isRejected).isFalse();

            // verify the from is the contract address
            assertThat(tx.sender).isEqualTo(CONTRACT_ADDR);

            // verify that we sent the correct amount
            assertThat(tx.value.intValueExact()).isEqualTo(1);

            // verify that the recipient is what we intended (in the order we submitted)
            assertThat(tx.destination).isEqualTo(new AionAddress(transfers[i].getRecipient()));
            i++;
        }

        // check that proper events are emit
        assertThat(submitBundleContext.getLogs().size()).isEqualTo(11);
        i = 0;
        for (Log l : submitBundleContext.getLogs()) {
            // verify address is correct
            assertThat(l.copyOfAddress()).isEqualTo(CONTRACT_ADDR.toByteArray());
            List<byte[]> topics = l.copyOfTopics();

            // on the 11th log, it should be the processed bundle event
            if (i == 10) {
                assertThat(topics.get(0)).isEqualTo(BridgeEventSig.PROCESSED_BUNDLE.getHashed());
                assertThat(topics.get(1)).isEqualTo(blockHash);
                assertThat(topics.get(2)).isEqualTo(payloadHash);
                continue;
            }

            // otherwise we expect a Distributed event
            assertThat(topics.get(0)).isEqualTo(BridgeEventSig.DISTRIBUTED.getHashed());
            assertThat(topics.get(1)).isEqualTo(transfers[i].getSourceTransactionHash());
            assertThat(topics.get(2)).isEqualTo(transfers[i].getRecipient());
            assertThat(new BigInteger(1, topics.get(3))).isEqualTo(transfers[i].getTransferValue());
            i++;
        }
    }

    @Test
    public void testTransferNotRelayer() {
        // override defaults
        PrecompiledTransactionContext initializationContext = context(OWNER_ADDR, CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(members[0]))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertTrue(transferResult.getStatus().isSuccess());

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(new AionAddress(members[0]), CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = capabilities.blake2b("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[10];
        for (int i = 0; i < 10; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = capabilities.blake2b(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            capabilities.computeA0Address(
                                    capabilities.blake2b(Integer.toHexString(i).getBytes())),
                            sourceTransactionHash);
        }
        byte[] payloadHash = BridgeUtilities.computeBundleHash(blockHash, transfers);

        // ATB-4, do one assert here to check that transactionHash is not set
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(ByteUtil.EMPTY_WORD);

        byte[][] signatures = new byte[members.length][];
        int i = 0;
        for (byte[] k : members) {
            signatures[i] = capabilities.sign(k, payloadHash);
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(b.getSourceTransactionHash()));
            addressList.add(new AddressFVM(b.getRecipient()));
            uintList.add(
                    new Uint128FVM(
                            PrecompiledUtilities.pad(
                                    b.getTransferValue().toByteArray(), 16)));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(Arrays.copyOfRange(sig, 0, 32)));
            sigChunk2.add(new AddressFVM(Arrays.copyOfRange(sig, 32, 64)));
            sigChunk3.add(new AddressFVM(Arrays.copyOfRange(sig, 64, 96)));
        }

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext incorrectRelaySubmitBundleContext =
                context(AddressUtils.ZERO_ADDRESS, CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        incorrectRelaySubmitBundleContext,
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(blockHash),
                                sourceTransactionList,
                                addressList,
                                uintList,
                                sigChunk1,
                                sigChunk2,
                                sigChunk3)
                        .encodeBytes();
        transferResult = this.contract.execute(callPayload, DEFAULT_NRG);
        assertEquals("FAILURE", transferResult.getStatus().causeOfError);
    }

    @Test
    public void testTransfersGreaterThanMaxListSize() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(members[0]))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertTrue(transferResult.getStatus().isSuccess());

        // override defaults
        PrecompiledTransactionContext submitBundleContext =
                context(new AionAddress(members[0]), CONTRACT_ADDR);
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.valueOf(1024));
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = capabilities.blake2b("blockHash".getBytes());

        // place a value greater than the maximum 1024
        BridgeTransfer[] transfers = new BridgeTransfer[1024];
        for (int i = 0; i < 1024; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = capabilities.blake2b(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            capabilities.computeA0Address(
                                    capabilities.blake2b(Integer.toHexString(i).getBytes())),
                            sourceTransactionHash);
        }
        byte[] payloadHash = BridgeUtilities.computeBundleHash(blockHash, transfers);
        byte[][] signatures = new byte[members.length][];
        int i = 0;
        for (byte[] k : members) {
            signatures[i] = capabilities.sign(k, payloadHash);
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(b.getSourceTransactionHash()));
            addressList.add(new AddressFVM(b.getRecipient()));
            uintList.add(
                    new Uint128FVM(
                            PrecompiledUtilities.pad(
                                    b.getTransferValue().toByteArray(), 16)));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(Arrays.copyOfRange(sig, 0, 32)));
            sigChunk2.add(new AddressFVM(Arrays.copyOfRange(sig, 32, 64)));
            sigChunk3.add(new AddressFVM(Arrays.copyOfRange(sig, 64, 96)));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(blockHash),
                                sourceTransactionList,
                                addressList,
                                uintList,
                                sigChunk1,
                                sigChunk2,
                                sigChunk3)
                        .encodeBytes();
        transferResult = this.contract.execute(callPayload, DEFAULT_NRG);

        /// VERIFICATION

        assertEquals("FAILURE", transferResult.getStatus().causeOfError);

        for (BridgeTransfer b : transfers) {
            assertThat(this.repository.getBalance(new AionAddress(b.getRecipient())))
                    .isEqualTo(BigInteger.ZERO);
        }
        assertThat(this.repository.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.valueOf(1024));

        assertThat(submitBundleContext.getInternalTransactions()).isEmpty();
        assertThat(submitBundleContext.getLogs()).isEmpty();
    }

    @Test
    public void testAlreadySubmittedBundle() {
        // override defaults
        PrecompiledTransactionContext initializationContext = context(OWNER_ADDR, CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(members[0]))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertTrue(transferResult.getStatus().isSuccess());

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(new AionAddress(members[0]), CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = capabilities.blake2b("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[10];
        for (int i = 0; i < 10; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = capabilities.blake2b(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            capabilities.computeA0Address(
                                    capabilities.blake2b(Integer.toHexString(i).getBytes())),
                            sourceTransactionHash);
        }
        byte[] payloadHash = BridgeUtilities.computeBundleHash(blockHash, transfers);

        // ATB-4.1 in order to test, we pretend that a bundle is already complete
        this.connector.setBundle(payloadHash, submitBundleContext.copyOfTransactionHash());

        byte[][] signatures = new byte[members.length][];
        int i = 0;
        for (byte[] k : members) {
            signatures[i] = capabilities.sign(k, payloadHash);
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(b.getSourceTransactionHash()));
            addressList.add(new AddressFVM(b.getRecipient()));
            uintList.add(
                    new Uint128FVM(
                            PrecompiledUtilities.pad(
                                    b.getTransferValue().toByteArray(), 16)));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(Arrays.copyOfRange(sig, 0, 32)));
            sigChunk2.add(new AddressFVM(Arrays.copyOfRange(sig, 32, 64)));
            sigChunk3.add(new AddressFVM(Arrays.copyOfRange(sig, 64, 96)));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(blockHash),
                                sourceTransactionList,
                                addressList,
                                uintList,
                                sigChunk1,
                                sigChunk2,
                                sigChunk3)
                        .encodeBytes();
        transferResult = this.contract.execute(callPayload, DEFAULT_NRG);

        /// VERIFICATION
        assertTrue(transferResult.getStatus().isSuccess());
        assertThat(submitBundleContext.getInternalTransactions()).isEmpty();
        assertThat(submitBundleContext.getLogs().size()).isEqualTo(1);

        // ATB 4.1 check that proper event was emit
        assertThat(submitBundleContext.getLogs().get(0).copyOfTopics().get(0))
                .isEqualTo(BridgeEventSig.SUCCESSFUL_TXHASH.getHashed());

        assertThat(submitBundleContext.getLogs().get(0).copyOfTopics().get(1))
                .isEqualTo(submitBundleContext.copyOfTransactionHash());
    }

    // william test
    @Test
    public void testTransferRingLocked() {
        // override defaults
        PrecompiledTransactionContext initializationContext = context(OWNER_ADDR, CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }

        // not initializing the ring

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(members[0]))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertTrue(transferResult.getStatus().isSuccess());

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(new AionAddress(members[0]), CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = capabilities.blake2b("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[5];
        for (int i = 0; i < 5; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = capabilities.blake2b(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            capabilities.computeA0Address(
                                    capabilities.blake2b(Integer.toHexString(i).getBytes())),
                            sourceTransactionHash);
        }
        byte[] payloadHash = BridgeUtilities.computeBundleHash(blockHash, transfers);

        // ATB-4, do one assert here to check that transactionHash is not set
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(ByteUtil.EMPTY_WORD);

        byte[][] signatures = new byte[members.length][];
        int i = 0;
        for (byte[] k : members) {
            signatures[i] = capabilities.sign(k, payloadHash);
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(b.getSourceTransactionHash()));
            addressList.add(new AddressFVM(b.getRecipient()));
            uintList.add(
                    new Uint128FVM(
                            PrecompiledUtilities.pad(
                                    b.getTransferValue().toByteArray(), 16)));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(Arrays.copyOfRange(sig, 0, 32)));
            sigChunk2.add(new AddressFVM(Arrays.copyOfRange(sig, 32, 64)));
            sigChunk3.add(new AddressFVM(Arrays.copyOfRange(sig, 64, 96)));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(blockHash),
                                sourceTransactionList,
                                addressList,
                                uintList,
                                sigChunk1,
                                sigChunk2,
                                sigChunk3)
                        .encodeBytes();

        transferResult = this.contract.execute(callPayload, DEFAULT_NRG);

        // VERIFICATION - failure
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(new byte[32]);

        assertEquals("FAILURE", transferResult.getStatus().causeOfError);

        // check that nothing has been modified from the failed transfer
        for (BridgeTransfer b : transfers) {
            assertThat(this.repository.getBalance(new AionAddress(b.getRecipient())))
                    .isEqualTo(BigInteger.ZERO);
        }
        assertThat(this.repository.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.valueOf(10));

        assertThat(submitBundleContext.getInternalTransactions()).isEmpty();
        assertThat(submitBundleContext.getLogs()).isEmpty();
    }

    @Test
    public void testTransferInvalidReLayer() {
        // override defaults
        PrecompiledTransactionContext initializationContext = context(OWNER_ADDR, CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());

        // not setting relayer

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(new AionAddress(members[0]), CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = capabilities.blake2b("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[10];
        for (int i = 0; i < 10; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = capabilities.blake2b(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            capabilities.computeA0Address(
                                    capabilities.blake2b(Integer.toHexString(i).getBytes())),
                            sourceTransactionHash);
        }
        byte[] payloadHash = BridgeUtilities.computeBundleHash(blockHash, transfers);

        // ATB-4, do one assert here to check that transactionHash is not set
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(ByteUtil.EMPTY_WORD);

        byte[][] signatures = new byte[members.length][];
        int i = 0;
        for (byte[] k : members) {
            signatures[i] = capabilities.sign(k, payloadHash);
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(b.getSourceTransactionHash()));
            addressList.add(new AddressFVM(b.getRecipient()));
            uintList.add(
                    new Uint128FVM(
                            PrecompiledUtilities.pad(
                                    b.getTransferValue().toByteArray(), 16)));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(Arrays.copyOfRange(sig, 0, 32)));
            sigChunk2.add(new AddressFVM(Arrays.copyOfRange(sig, 32, 64)));
            sigChunk3.add(new AddressFVM(Arrays.copyOfRange(sig, 64, 96)));
        }

        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(blockHash),
                                sourceTransactionList,
                                addressList,
                                uintList,
                                sigChunk1,
                                sigChunk2,
                                sigChunk3)
                        .encodeBytes();
        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);

        // VERIFICATION - failure
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(new byte[32]);

        assertEquals("FAILURE", transferResult.getStatus().causeOfError);

        // check that nothing has been modified from the failed transfer
        for (BridgeTransfer b : transfers) {
            assertThat(this.repository.getBalance(new AionAddress(b.getRecipient())))
                    .isEqualTo(BigInteger.ZERO);
        }
        assertThat(this.repository.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.valueOf(10));

        assertThat(submitBundleContext.getInternalTransactions()).isEmpty();
        assertThat(submitBundleContext.getLogs()).isEmpty();
    }

    @Test
    public void testTransferLessThanMinimumRequiredValidators() {
        // override defaults
        PrecompiledTransactionContext initializationContext = context(OWNER_ADDR, CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(members[0]))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertTrue(transferResult.getStatus().isSuccess());

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(new AionAddress(members[0]), CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = capabilities.blake2b("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[5];
        for (int i = 0; i < 5; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = capabilities.blake2b(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            capabilities.computeA0Address(
                                    capabilities.blake2b(Integer.toHexString(i).getBytes())),
                            sourceTransactionHash);
        }
        byte[] payloadHash = BridgeUtilities.computeBundleHash(blockHash, transfers);

        // ATB-4, do one assert here to check that transactionHash is not set
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(ByteUtil.EMPTY_WORD);

        // only give 2/5 signatures
        byte[][] signatures = new byte[2][];
        signatures[0] = capabilities.sign(members[0], payloadHash);
        signatures[1] = capabilities.sign(members[1], payloadHash);

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(b.getSourceTransactionHash()));
            addressList.add(new AddressFVM(b.getRecipient()));
            uintList.add(
                    new Uint128FVM(
                            PrecompiledUtilities.pad(
                                    b.getTransferValue().toByteArray(), 16)));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(Arrays.copyOfRange(sig, 0, 32)));
            sigChunk2.add(new AddressFVM(Arrays.copyOfRange(sig, 32, 64)));
            sigChunk3.add(new AddressFVM(Arrays.copyOfRange(sig, 64, 96)));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(blockHash),
                                sourceTransactionList,
                                addressList,
                                uintList,
                                sigChunk1,
                                sigChunk2,
                                sigChunk3)
                        .encodeBytes();

        // input will include 5 transfers and 2 validators with correct signatures
        transferResult = this.contract.execute(callPayload, DEFAULT_NRG);

        /// VERIFICATION
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(new byte[32]);

        assertEquals("FAILURE", transferResult.getStatus().causeOfError);

        // check that nothing has been modified from the failed transfer
        for (BridgeTransfer b : transfers) {
            assertThat(this.repository.getBalance(new AionAddress(b.getRecipient())))
                    .isEqualTo(BigInteger.ZERO);
        }
        assertThat(this.repository.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.valueOf(10));

        assertThat(submitBundleContext.getInternalTransactions()).isEmpty();
        assertThat(submitBundleContext.getLogs()).isEmpty();
    }

    @Test
    public void testTransferInsufficientValidatorSignatures() {
        // override defaults
        PrecompiledTransactionContext initializationContext = context(OWNER_ADDR, CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(members[0]))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertTrue(transferResult.getStatus().isSuccess());

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(new AionAddress(members[0]), CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = capabilities.blake2b("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[5];
        for (int i = 0; i < 5; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = capabilities.blake2b(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            capabilities.computeA0Address(
                                    capabilities.blake2b(Integer.toHexString(i).getBytes())),
                            sourceTransactionHash);
        }
        byte[] payloadHash = BridgeUtilities.computeBundleHash(blockHash, transfers);

        // ATB-4, do one assert here to check that transactionHash is not set
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(ByteUtil.EMPTY_WORD);

        // only give 3/5 signatures
        byte[][] signatures = new byte[3][];
        signatures[0] = capabilities.sign(members[0], payloadHash);
        signatures[1] = capabilities.sign(members[1], payloadHash);
        signatures[2] = capabilities.sign(members[2], payloadHash);

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(b.getSourceTransactionHash()));
            addressList.add(new AddressFVM(b.getRecipient()));
            uintList.add(
                    new Uint128FVM(
                            PrecompiledUtilities.pad(
                                    b.getTransferValue().toByteArray(), 16)));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            // add incorrect signatures, copies [0:32] for all chunks
            // verifyISig for testing will only fail if signature is empty byte array
            sigChunk1.add(new AddressFVM(ByteUtil.EMPTY_WORD));
            sigChunk2.add(new AddressFVM(ByteUtil.EMPTY_WORD));
            sigChunk3.add(new AddressFVM(ByteUtil.EMPTY_WORD));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(blockHash),
                                sourceTransactionList,
                                addressList,
                                uintList,
                                sigChunk1,
                                sigChunk2,
                                sigChunk3)
                        .encodeBytes();

        // input will include 5 transfers and 3 validators with incorrect signatures
        transferResult = this.contract.execute(callPayload, DEFAULT_NRG);

        assertEquals("FAILURE", transferResult.getStatus().causeOfError);

        /// VERIFICATION
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(ByteUtil.EMPTY_WORD);

        // check that nothing has been changed from the failed transfer
        for (BridgeTransfer b : transfers) {
            assertThat(this.repository.getBalance(new AionAddress(b.getRecipient())))
                    .isEqualTo(BigInteger.ZERO);
        }
        assertThat(this.repository.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.valueOf(10));

        assertThat(submitBundleContext.getInternalTransactions()).isEmpty();
        assertThat(submitBundleContext.getLogs()).isEmpty();
    }

    @Test
    public void testTransferOutOfBoundsListMeta() {
        // override defaults
        PrecompiledTransactionContext initializationContext = context(OWNER_ADDR, CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }

        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(members[0]))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertTrue(transferResult.getStatus().isSuccess());

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(new AionAddress(members[0]), CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = capabilities.blake2b("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[5];
        for (int i = 0; i < 5; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = capabilities.blake2b(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            capabilities.computeA0Address(
                                    capabilities.blake2b(Integer.toHexString(i).getBytes())),
                            sourceTransactionHash);
        }
        byte[] payloadHash = BridgeUtilities.computeBundleHash(blockHash, transfers);

        // ATB-4, do one assert here to check that transactionHash is not set
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(ByteUtil.EMPTY_WORD);

        byte[][] signatures = new byte[members.length][];
        int i = 0;
        for (byte[] k : members) {
            signatures[i] = capabilities.sign(k, payloadHash);
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(b.getSourceTransactionHash()));
            addressList.add(new AddressFVM(b.getRecipient()));
            uintList.add(
                    new Uint128FVM(
                            PrecompiledUtilities.pad(
                                    b.getTransferValue().toByteArray(), 16)));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(Arrays.copyOfRange(sig, 0, 32)));
            sigChunk2.add(new AddressFVM(Arrays.copyOfRange(sig, 32, 64)));
            sigChunk3.add(new AddressFVM(Arrays.copyOfRange(sig, 64, 96)));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(blockHash),
                                sourceTransactionList,
                                addressList,
                                uintList,
                                sigChunk1,
                                sigChunk2,
                                sigChunk3)
                        .encodeBytes();

        callPayload[36] = (byte) 0x172;
        callPayload[37] = (byte) 0x172;
        callPayload[38] = (byte) 0x172;

        transferResult = this.contract.execute(callPayload, DEFAULT_NRG);

        /// VERIFICATION
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(new byte[32]);

        assertEquals("FAILURE", transferResult.getStatus().causeOfError);

        // check that nothing has been changed from the failed transfer
        for (BridgeTransfer b : transfers) {
            assertThat(this.repository.getBalance(new AionAddress(b.getRecipient())))
                    .isEqualTo(BigInteger.ZERO);
        }
        assertThat(this.repository.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.valueOf(10));

        assertThat(submitBundleContext.getInternalTransactions()).isEmpty();
        assertThat(submitBundleContext.getLogs()).isEmpty();
    }

    @Test
    public void testTransferToSameAddressTwiceInOneBundle() {

        BridgeTransfer[] transfers = new BridgeTransfer[10];
        // generate a unique sourceTransactionHash for each transfer
        for (int i = 0; i < 10; i++) {

            // send to the same addr more than once
            if (i == 2 || i == 3) {
                byte[] sourceTransactionHashDefault = capabilities.blake2b(Integer.toString(2).getBytes());
                transfers[i] =
                        BridgeTransfer.getInstance(
                                BigInteger.ONE,
                                capabilities.computeA0Address(
                                        capabilities.blake2b(Integer.toHexString(2).getBytes())),
                                sourceTransactionHashDefault);
            } else {
                // generate a unique sourceTransactionHash for each transfer
                byte[] sourceTransactionHash = capabilities.blake2b(Integer.toString(i).getBytes());
                transfers[i] =
                        BridgeTransfer.getInstance(
                                BigInteger.ONE,
                                capabilities.computeA0Address(
                                        capabilities.blake2b(Integer.toHexString(i).getBytes())),
                                sourceTransactionHash);
            }
        }

        ReturnDataFromSetup fromSetup = setupForTest(transfers, members);
        PrecompiledTransactionContext submitBundleContext = fromSetup.submitBundleContext;
        byte[] blockHash = fromSetup.blockHash;
        byte[] payloadHash = fromSetup.payloadHash;
        byte[] callPayload = fromSetup.callPayload;

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);

        /// VERIFICATION

        assertTrue(transferResult.getStatus().isSuccess());

        int i = 0;
        for (BridgeTransfer b : transfers) {
            if (i == 2 || i == 3) {
                assertThat(this.repository.getBalance(new AionAddress(b.getRecipient())))
                        .isEqualTo(BigInteger.TWO);
            } else {
                assertThat(this.repository.getBalance(new AionAddress(b.getRecipient())))
                        .isEqualTo(BigInteger.ONE);
            }
            i++;
        }
        assertThat(this.repository.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.ZERO);

        // context verification
        // we expect on successful output:
        // 10 internal transactions (that all succeed)
        // 10 Distributed events
        // 1  ProcessedBundle Event
        assertThat(submitBundleContext.getInternalTransactions().size()).isEqualTo(10);
        i = 0;
        for (InternalTransaction tx : submitBundleContext.getInternalTransactions()) {

            // verify the internal transaction is not rejected
            assertThat(tx.isRejected).isFalse();

            // verify the from is the contract address
            assertThat(tx.sender).isEqualTo(CONTRACT_ADDR);

            // verify that we sent the correct amount
            assertThat(tx.value.intValueExact()).isEqualTo(1);

            // verify that the recipient is what we intended (in the order we submitted)
            assertThat(tx.destination).isEqualTo(new AionAddress(transfers[i].getRecipient()));
            i++;
        }

        // check that proper events are emit
        assertThat(submitBundleContext.getLogs().size()).isEqualTo(11);
        i = 0;
        for (Log l : submitBundleContext.getLogs()) {
            // verify address is correct
            assertThat(l.copyOfAddress()).isEqualTo(CONTRACT_ADDR.toByteArray());
            List<byte[]> topics = l.copyOfTopics();

            // on the 11th log, it should be the processed bundle event
            if (i == 10) {
                assertThat(topics.get(0)).isEqualTo(BridgeEventSig.PROCESSED_BUNDLE.getHashed());
                assertThat(topics.get(1)).isEqualTo(blockHash);
                assertThat(topics.get(2)).isEqualTo(payloadHash);
                continue;
            }

            // otherwise we expect a Distributed event
            assertThat(topics.get(0)).isEqualTo(BridgeEventSig.DISTRIBUTED.getHashed());
            assertThat(topics.get(1)).isEqualTo(transfers[i].getSourceTransactionHash());
            assertThat(topics.get(2)).isEqualTo(transfers[i].getRecipient());
            assertThat(new BigInteger(1, topics.get(3))).isEqualTo(transfers[i].getTransferValue());
            i++;
        }
    }

    // deserialization checks
    @Test
    public void testTransferHugeListOffset() {

        BridgeTransfer[] transfers = new BridgeTransfer[10];
        for (int i = 0; i < 10; i++) {
            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = capabilities.blake2b(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            capabilities.computeA0Address(
                                    capabilities.blake2b(Integer.toHexString(i).getBytes())),
                            sourceTransactionHash);
        }

        // setup
        ReturnDataFromSetup fromSetup = setupForTest(transfers, members);
        PrecompiledTransactionContext submitBundleContext = fromSetup.submitBundleContext;
        byte[] payloadHash = fromSetup.payloadHash;
        byte[] callPayload = fromSetup.callPayload;

        callPayload[50] = (byte) 0x128; // make the list offset here too big
        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertEquals("FAILURE", transferResult.getStatus().causeOfError);

        // VERIFICATION failure
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(new byte[32]);

        assertEquals("FAILURE", transferResult.getStatus().causeOfError);

        // check that nothing has been changed from the failed transfer
        for (BridgeTransfer b : transfers) {
            assertThat(this.repository.getBalance(new AionAddress(b.getRecipient())))
                    .isEqualTo(BigInteger.ZERO);
        }
        assertThat(this.repository.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.valueOf(10));

        assertThat(submitBundleContext.getInternalTransactions()).isEmpty();
        assertThat(submitBundleContext.getLogs()).isEmpty();
    }

    @Test
    public void testTransferHugeListLength() {

        BridgeTransfer[] transfers = new BridgeTransfer[10];
        for (int i = 0; i < 10; i++) {
            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = capabilities.blake2b(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            capabilities.computeA0Address(
                                    capabilities.blake2b(Integer.toHexString(i).getBytes())),
                            sourceTransactionHash);
        }

        // setup
        ReturnDataFromSetup fromSetup = setupForTest(transfers, members);
        PrecompiledTransactionContext submitBundleContext = fromSetup.submitBundleContext;
        byte[] payloadHash = fromSetup.payloadHash;
        byte[] callPayload = fromSetup.callPayload;

        callPayload[50] = (byte) 0x128;
        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertEquals("FAILURE", transferResult.getStatus().causeOfError);

        // VERIFICATION failure
        assertThat(
                        this.contract
                                .execute(
                                        ByteUtil.merge(
                                                BridgeFuncSig.PURE_ACTION_MAP.getBytes(),
                                                payloadHash),
                                        21000L)
                                .getReturnData())
                .isEqualTo(new byte[32]);

        assertEquals("FAILURE", transferResult.getStatus().causeOfError);

        // check that nothing has been changed from the failed transfer
        for (BridgeTransfer b : transfers) {
            assertThat(this.repository.getBalance(new AionAddress(b.getRecipient())))
                    .isEqualTo(BigInteger.ZERO);
        }
        assertThat(this.repository.getBalance(CONTRACT_ADDR)).isEqualTo(BigInteger.valueOf(10));

        assertThat(submitBundleContext.getInternalTransactions()).isEmpty();
        assertThat(submitBundleContext.getLogs()).isEmpty();
    }

    @Test
    public void testTransferFailLength() {
        // instantiate a bundle with 1 transfer and 1 validator
        // input byte should have length 404
        byte[][] members = new byte[1][32];
        members[0] = getRandomAddr();

        int n = 1;
        BridgeTransfer[] transfers = new BridgeTransfer[n];
        for (int i = 0; i < n; i++) {
            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = capabilities.blake2b(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            capabilities.computeA0Address(
                                    capabilities.blake2b(Integer.toHexString(i).getBytes())),
                            sourceTransactionHash);
        }

        // setup
        ReturnDataFromSetup fromSetup = setupForTest(transfers, members);
        byte[] callPayload = fromSetup.callPayload;

        // loops through all possible shorter length and check for failure
        int i;
        for (i = 1; i < 404; i++) {
            byte[] input = new byte[i];
            System.arraycopy(callPayload, 0, input, 0, i);
            PrecompiledTransactionResult result = this.contract.execute(input, DEFAULT_NRG);
            assertEquals("FAILURE", result.getStatus().causeOfError);
        }
        System.out.println("fail count: " + i);

        // try with more bytes than expected
        byte[] input = new byte[555];
        System.arraycopy(callPayload, 0, input, 0, 404);
        PrecompiledTransactionResult result = this.contract.execute(input, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());
    }

    @Test
    public void testTransferFailLength2() {
        // instantiate a bundle with 10 transfers and 5 validators
        // input byte[] should have length of 1508 = 404 + (10-1)*80 + (5-1)*96
        byte[][] members = new byte[5][32];
        for (int i = 0; i < members.length; i++) {
            members[i] = getRandomAddr();
        }

        int n = 10; // number of transfers
        BridgeTransfer[] transfers = new BridgeTransfer[n];
        for (int i = 0; i < n; i++) {
            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = capabilities.blake2b(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            capabilities.computeA0Address(
                                    capabilities.blake2b(Integer.toHexString(i).getBytes())),
                            sourceTransactionHash);
        }

        // setup
        ReturnDataFromSetup fromSetup = setupForTest(transfers, members);
        byte[] callPayload = fromSetup.callPayload;

        // loops through all possible shorter length and check for failure
        int i;
        for (i = 1; i < 1508; i++) {
            byte[] input = new byte[i];
            System.arraycopy(callPayload, 0, input, 0, i);
            PrecompiledTransactionResult result = this.contract.execute(input, DEFAULT_NRG);
            assertEquals("FAILURE", result.getStatus().causeOfError);
        }
        System.out.println("fail count: " + i);
    }

    private ReturnDataFromSetup setupForTest(BridgeTransfer[] transfers, byte[][] members) {
        // override defaults
        PrecompiledTransactionContext initializationContext = context(OWNER_ADDR, CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(members[0]))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertTrue(transferResult.getStatus().isSuccess());

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(new AionAddress(members[0]), CONTRACT_ADDR);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, ExternalStateForTests.usingRepository(this.repository), OWNER_ADDR, CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = capabilities.blake2b("blockHash".getBytes());

        byte[] payloadHash = BridgeUtilities.computeBundleHash(blockHash, transfers);

        byte[][] signatures = new byte[members.length][];
        int i = 0;
        for (byte[] k : members) {
            signatures[i] = capabilities.sign(k, payloadHash);
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(b.getSourceTransactionHash()));
            addressList.add(new AddressFVM(b.getRecipient()));
            uintList.add(
                    new Uint128FVM(
                            PrecompiledUtilities.pad(
                                    b.getTransferValue().toByteArray(), 16)));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(Arrays.copyOfRange(sig, 0, 32)));
            sigChunk2.add(new AddressFVM(Arrays.copyOfRange(sig, 32, 64)));
            sigChunk3.add(new AddressFVM(Arrays.copyOfRange(sig, 64, 96)));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(blockHash),
                                sourceTransactionList,
                                addressList,
                                uintList,
                                sigChunk1,
                                sigChunk2,
                                sigChunk3)
                        .encodeBytes();

        return new ReturnDataFromSetup(submitBundleContext, blockHash, payloadHash, callPayload);
    }

    class ReturnDataFromSetup {
        PrecompiledTransactionContext submitBundleContext;
        byte[] blockHash;
        byte[] payloadHash;
        byte[] callPayload;

        public ReturnDataFromSetup(
                PrecompiledTransactionContext submitBundleContext,
                byte[] blockHash,
                byte[] payloadHash,
                byte[] callPayload) {
            this.submitBundleContext = submitBundleContext;
            this.blockHash = blockHash;
            this.payloadHash = payloadHash;
            this.callPayload = callPayload;
        }
    }

    // other functions coverage
    @Test
    public void testRingLocked() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }

        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());

        // try before
        byte[] callPayload =
                new AbiEncoder(BridgeFuncSig.PURE_RING_LOCKED.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertTrue(transferResult.getStatus().isSuccess());
        assertThat(transferResult.getReturnData()).isEqualTo(DataWord.ONE.getData());

        // lock the ring
        this.connector.setRingLocked(false);

        // try after
        byte[] callPayload2 =
                new AbiEncoder(BridgeFuncSig.PURE_RING_LOCKED.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult transferResult2 =
                this.contract.execute(callPayload2, DEFAULT_NRG);
        assertTrue(transferResult2.getStatus().isSuccess());
        assertThat(transferResult2.getReturnData()).isEqualTo(DataWord.ZERO.getData());
    }

    @Test
    public void testMinThreshold() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }

        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());

        // try before
        byte[] callPayload =
                new AbiEncoder(BridgeFuncSig.PURE_MIN_THRESH.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertTrue(transferResult.getStatus().isSuccess());
        assertThat(transferResult.getReturnData())
                .isEqualTo(new DataWord(new BigInteger("3")).getData());

        // explicitly set the min threshold to 5
        this.connector.setMinThresh(5);

        // try after
        byte[] callPayload2 =
                new AbiEncoder(BridgeFuncSig.PURE_MIN_THRESH.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult transferResult2 =
                this.contract.execute(callPayload2, DEFAULT_NRG);
        assertTrue(transferResult2.getStatus().isSuccess());
        assertThat(transferResult2.getReturnData())
                .isEqualTo(new DataWord(new BigInteger("5")).getData());

        // try setting threshold greater than number of validator members
        this.connector.setMinThresh(10);

        // try after
        byte[] callPayload3 =
                new AbiEncoder(BridgeFuncSig.PURE_MIN_THRESH.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult transferResult3 =
                this.contract.execute(callPayload3, DEFAULT_NRG);
        assertTrue(transferResult3.getStatus().isSuccess());
        assertThat(transferResult3.getReturnData())
                .isEqualTo(new DataWord(new BigInteger("10")).getData());
    }

    @Test
    public void testMemberCount() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }

        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());

        // try before
        byte[] callPayload =
                new AbiEncoder(BridgeFuncSig.PURE_MEMBER_COUNT.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertTrue(transferResult.getStatus().isSuccess());
        assertThat(transferResult.getReturnData())
                .isEqualTo(new DataWord(new BigInteger("5")).getData());

        // explicitly set the member count to 10
        this.connector.setMemberCount(10);

        // try after
        byte[] callPayload2 =
                new AbiEncoder(BridgeFuncSig.PURE_MEMBER_COUNT.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult transferResult2 =
                this.contract.execute(callPayload2, DEFAULT_NRG);
        assertTrue(transferResult2.getStatus().isSuccess());
        assertThat(transferResult2.getReturnData())
                .isEqualTo(new DataWord(new BigInteger("10")).getData());
    }

    @Test
    public void testRingMap() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }

        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());

        // create input byte[]
        byte[] callPayload = new byte[36];
        byte[] encodeBytes =
                new AbiEncoder(BridgeFuncSig.PURE_RING_MAP.getSignature()).encodeBytes();
        byte[] randomAddress = getRandomAddr();
        System.arraycopy(encodeBytes, 0, callPayload, 0, 4);
        System.arraycopy(randomAddress, 0, callPayload, 4, 32);

        // execute with valid input
        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertTrue(transferResult.getStatus().isSuccess());

        // execute with invalid input
        PrecompiledTransactionResult transferResult2 =
                this.contract.execute(encodeBytes, DEFAULT_NRG);
        assertEquals("FAILURE", transferResult2.getStatus().causeOfError);
    }

    @Test
    public void testFailRingInitialize() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }

        // address null
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature()).encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals("FAILURE", result.getStatus().causeOfError);

        this.connector.setRingLocked(true);

        // failed to initialize due to locked ring
        byte[] payload2 =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result2 = this.contract.execute(payload2, DEFAULT_NRG);
        assertEquals("FAILURE", result2.getStatus().causeOfError);
    }

    @Test
    public void testAddRingMember() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }

        // address null - fail
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_ADD_MEMBER.getSignature()).encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals("FAILURE", result.getStatus().causeOfError);

        // add new member - fail
        byte[] sig =
                new AbiEncoder(BridgeFuncSig.SIG_RING_ADD_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] newMember = getRandomAddr(); // the new member
        byte[] payload2 = new byte[4 + 32];
        System.arraycopy(sig, 0, payload2, 0, 4);
        System.arraycopy(newMember, 0, payload2, 4, 32);

        PrecompiledTransactionResult result2 = this.contract.execute(payload2, DEFAULT_NRG);
        assertEquals("FAILURE", result2.getStatus().causeOfError);

        // lock the ring
        this.connector.setRingLocked(true);

        // add new member - success
        byte[] sig3 =
                new AbiEncoder(BridgeFuncSig.SIG_RING_ADD_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] newMember3 = getRandomAddr(); // the new member
        byte[] payload3 = new byte[4 + 32];
        System.arraycopy(sig3, 0, payload3, 0, 4);
        System.arraycopy(newMember3, 0, payload3, 4, 32);

        PrecompiledTransactionResult result3 = this.contract.execute(payload3, DEFAULT_NRG);
        assertTrue(result3.getStatus().isSuccess());
    }

    @Test
    public void testAddRingMemberNotOwner() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(AddressUtils.ZERO_ADDRESS, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }

        // lock the ring
        this.connector.setRingLocked(true);

        // add new member - success
        byte[] sig3 =
                new AbiEncoder(BridgeFuncSig.SIG_RING_ADD_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] newMember3 = getRandomAddr(); // the new member
        byte[] payload3 = new byte[4 + 32];
        System.arraycopy(sig3, 0, payload3, 0, 4);
        System.arraycopy(newMember3, 0, payload3, 4, 32);

        PrecompiledTransactionResult result3 = this.contract.execute(payload3, DEFAULT_NRG);
        assertEquals("FAILURE", result3.getStatus().causeOfError);
    }

    @Test
    public void testRemoveRingMember() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }

        // address null - fail
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_REMOVE_MEMBER.getSignature()).encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals("FAILURE", result.getStatus().causeOfError);

        // add new member - fail
        byte[] sig =
                new AbiEncoder(BridgeFuncSig.SIG_RING_REMOVE_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] newMember = getRandomAddr(); // the new member
        byte[] payload2 = new byte[4 + 32];
        System.arraycopy(sig, 0, payload2, 0, 4);
        System.arraycopy(newMember, 0, payload2, 4, 32);

        PrecompiledTransactionResult result2 = this.contract.execute(payload2, DEFAULT_NRG);
        assertEquals("FAILURE", result2.getStatus().causeOfError);

        // initialize ring
        byte[] ring =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        this.contract.execute(ring, DEFAULT_NRG);

        // remove member - fail, member does not exist
        byte[] sig3 =
                new AbiEncoder(BridgeFuncSig.SIG_RING_REMOVE_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] newMember3 = getRandomAddr(); // the new member
        byte[] payload3 = new byte[4 + 32];
        System.arraycopy(sig3, 0, payload3, 0, 4);
        System.arraycopy(newMember3, 0, payload3, 4, 32);

        PrecompiledTransactionResult result3 = this.contract.execute(payload3, DEFAULT_NRG);
        assertEquals("FAILURE", result3.getStatus().causeOfError);

        // remove member - success, member exists
        byte[] sig4 =
                new AbiEncoder(BridgeFuncSig.SIG_RING_REMOVE_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] payload4 = new byte[4 + 32];
        System.arraycopy(sig4, 0, payload4, 0, 4);
        System.arraycopy(members[0], 0, payload4, 4, 32);

        PrecompiledTransactionResult result4 = this.contract.execute(payload4, DEFAULT_NRG);
        assertTrue(result4.getStatus().isSuccess());
    }

    @Test
    public void testRemoveRingMemberNotOwner() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }

        // address null - fail
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_REMOVE_MEMBER.getSignature()).encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals("FAILURE", result.getStatus().causeOfError);

        // add new member - fail
        byte[] sig =
                new AbiEncoder(BridgeFuncSig.SIG_RING_REMOVE_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] newMember = getRandomAddr(); // the new member
        byte[] payload2 = new byte[4 + 32];
        System.arraycopy(sig, 0, payload2, 0, 4);
        System.arraycopy(newMember, 0, payload2, 4, 32);

        PrecompiledTransactionResult result2 = this.contract.execute(payload2, DEFAULT_NRG);
        assertEquals("FAILURE", result2.getStatus().causeOfError);

        // initialize ring
        byte[] ring =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        this.contract.execute(ring, DEFAULT_NRG);

        // remove member - fail, member does not exist
        byte[] sig3 =
                new AbiEncoder(BridgeFuncSig.SIG_RING_REMOVE_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] newMember3 = getRandomAddr(); // the new member
        byte[] payload3 = new byte[4 + 32];
        System.arraycopy(sig3, 0, payload3, 0, 4);
        System.arraycopy(newMember3, 0, payload3, 4, 32);

        PrecompiledTransactionResult result3 = this.contract.execute(payload3, DEFAULT_NRG);
        assertEquals("FAILURE", result3.getStatus().causeOfError);

        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(AddressUtils.ZERO_ADDRESS, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        // failure, member exists but sender is no longer owner
        byte[] sig4 =
                new AbiEncoder(BridgeFuncSig.SIG_RING_REMOVE_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] payload4 = new byte[4 + 32];
        System.arraycopy(sig4, 0, payload4, 0, 4);
        System.arraycopy(members[0], 0, payload4, 4, 32);

        PrecompiledTransactionResult result4 = this.contract.execute(payload4, DEFAULT_NRG);
        assertEquals("FAILURE", result4.getStatus().causeOfError);
    }

    @Test
    public void testSetReplayer() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (byte[] k : members) {
            encodingList.add(new AddressFVM(k));
        }

        // address null
        byte[] nullInput =
                new AbiEncoder(BridgeFuncSig.SIG_SET_RELAYER.getSignature()).encodeBytes();
        PrecompiledTransactionResult res = this.contract.execute(nullInput, DEFAULT_NRG);
        assertEquals("FAILURE", res.getStatus().causeOfError);

        // address valid
        byte[] sig = new AbiEncoder(BridgeFuncSig.SIG_SET_RELAYER.getSignature()).encodeBytes();
        byte[] newReplayer = getRandomAddr();
        byte[] payload = new byte[4 + 32];

        System.arraycopy(sig, 0, payload, 0, 4);
        System.arraycopy(newReplayer, 0, payload, 4, 32);
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertEquals(result.getStatus(), TransactionStatus.successful());

        // caller not owner - fail
        AionAddress address1 = new AionAddress(getRandomAddr());
        this.contract =
                new TokenBridgeContract(
                        context(address1, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        byte[] sig2 = new AbiEncoder(BridgeFuncSig.SIG_SET_RELAYER.getSignature()).encodeBytes();
        byte[] newReplayer2 = getRandomAddr();
        byte[] payload2 = new byte[4 + 32];

        System.arraycopy(sig2, 0, payload2, 0, 4);
        System.arraycopy(newReplayer2, 0, payload2, 4, 32);
        PrecompiledTransactionResult result2 = this.contract.execute(payload2, DEFAULT_NRG);
        assertEquals("FAILURE", result2.getStatus().causeOfError);
    }

    @Test
    public void testFallbackTransaction() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(AddressUtils.ZERO_ADDRESS, CONTRACT_ADDR),
                        ExternalStateForTests.usingRepository(this.repository),
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.connector = this.contract.getConnector();

        assertThat(this.connector.getInitialized()).isFalse();
        PrecompiledTransactionResult result =
                this.contract.execute(ByteUtil.EMPTY_BYTE_ARRAY, 21_000L);
        assertEquals(result.getStatus(), TransactionStatus.successful());
        assertThat(this.connector.getInitialized()).isTrue();
    }

    private static byte[] getRandomAddr() {
        byte[] addr = new byte[32];
        r.nextBytes(addr);
        addr[0] = org.aion.precompiled.util.ByteUtil.hexStringToBytes("0xa0")[0];
        return addr;
    }
}
