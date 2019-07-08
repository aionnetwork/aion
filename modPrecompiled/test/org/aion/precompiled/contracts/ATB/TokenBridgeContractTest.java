package org.aion.precompiled.contracts.ATB;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.precompiled.contracts.ATB.BridgeTestUtils.context;
import static org.aion.precompiled.contracts.ATB.BridgeTestUtils.dummyContext;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.mcf.config.CfgPrune;
import org.aion.mcf.db.ContractDetails;
import org.aion.mcf.db.PruneConfig;
import org.aion.mcf.db.RepositoryConfig;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.precompiled.PrecompiledResultCode;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.precompiled.PrecompiledUtilities;
import org.aion.precompiled.encoding.AbiEncoder;
import org.aion.precompiled.encoding.AddressFVM;
import org.aion.precompiled.encoding.ListFVM;
import org.aion.precompiled.encoding.Uint128FVM;
import org.aion.precompiled.type.PrecompiledTransactionContext;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.types.Log;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.AddressUtils;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.db.AionRepositoryCache;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.junit.Before;
import org.junit.Test;

/**
 * TokenBridgeContract is coupled with a serialization layer, this is mainly dedicated to testing
 * that functionality
 */
public class TokenBridgeContractTest {
    private TokenBridgeContract contract;
    private BridgeController controller;
    private BridgeStorageConnector connector;
    private AionRepositoryCache repository;

    private static final ECKey members[] =
            new ECKey[] {
                ECKeyFac.inst().create(),
                ECKeyFac.inst().create(),
                ECKeyFac.inst().create(),
                ECKeyFac.inst().create(),
                ECKeyFac.inst().create()
            };

    private static final AionAddress CONTRACT_ADDR =
            new AionAddress(HashUtil.h256("contractAddress".getBytes()));
    private static final AionAddress OWNER_ADDR =
            new AionAddress(HashUtil.h256("ownerAddress".getBytes()));

    private static final long DEFAULT_NRG = 21000L;

    @Before
    public void before() {
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
        this.repository = new AionRepositoryCache(AionRepositoryImpl.createForTesting(repoConfig));
        // override defaults
        this.contract =
                new TokenBridgeContract(dummyContext(), this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();
    }

    @Test
    public void testNotEnoughEnergyExecution() {
        assertThat(this.connector.getInitialized()).isFalse();
        PrecompiledTransactionResult result =
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
                        context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        byte[] newOwner = AddressSpecs.computeA0Address(HashUtil.h256("newOwner".getBytes()));
        byte[] payload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_CHANGE_OWNER.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(newOwner)))
                        .encodeBytes();
        System.out.println("encoded payload: " + ByteUtil.toHexString(payload));

        assertThat(this.connector.getInitialized()).isFalse();
        PrecompiledTransactionResult setResult = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(this.connector.getInitialized()).isTrue();
        assertThat(setResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

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
                        context(
                                AddressUtils.ZERO_ADDRESS,
                                CONTRACT_ADDR,
                                ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        byte[] newOwner = AddressSpecs.computeA0Address(HashUtil.h256("newOwner".getBytes()));

        byte[] payload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_CHANGE_OWNER.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(newOwner)))
                        .encodeBytes();
        System.out.println("encoded payload: " + ByteUtil.toHexString(payload));

        PrecompiledTransactionResult setResult = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(setResult.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);
    }

    @Test
    public void testInitializeRing() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // pull results from controller
        for (ECKey k : members) {
            assertThat(this.connector.getActiveMember(k.getAddress())).isTrue();
        }
    }

    @Test
    public void testInitializeRingNotOwner() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(
                                AddressUtils.ZERO_ADDRESS,
                                CONTRACT_ADDR,
                                ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);
    }

    @Test
    public void testTransfer() {
        // override defaults
        PrecompiledTransactionContext initializationContext =
                context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(members[0].getAddress())))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(
                        new AionAddress(members[0].getAddress()),
                        CONTRACT_ADDR,
                        ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = HashUtil.h256("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[10];
        for (int i = 0; i < 10; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            AddressSpecs.computeA0Address(
                                    HashUtil.h256(Integer.toHexString(i).getBytes())),
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
        for (ECKey k : members) {
            signatures[i] = k.sign(payloadHash).toBytes();
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(new ByteArrayWrapper(b.getSourceTransactionHash())));
            addressList.add(new AddressFVM(new ByteArrayWrapper(b.getRecipient())));
            uintList.add(
                    new Uint128FVM(
                            new ByteArrayWrapper(
                                    PrecompiledUtilities.pad(
                                            b.getTransferValue().toByteArray(), 16))));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 0, 32))));
            sigChunk2.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 32, 64))));
            sigChunk3.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 64, 96))));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(blockHash)),
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

        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

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
        PrecompiledTransactionContext initializationContext =
                context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(members[0].getAddress())))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(
                        new AionAddress(members[0].getAddress()),
                        CONTRACT_ADDR,
                        ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = HashUtil.h256("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[10];
        for (int i = 0; i < 10; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            HashUtil.h256(Integer.toHexString(i).getBytes()),
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
        for (ECKey k : members) {
            signatures[i] = k.sign(payloadHash).toBytes();
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(new ByteArrayWrapper(b.getSourceTransactionHash())));
            addressList.add(new AddressFVM(new ByteArrayWrapper(b.getRecipient())));
            uintList.add(
                    new Uint128FVM(
                            new ByteArrayWrapper(
                                    PrecompiledUtilities.pad(
                                            b.getTransferValue().toByteArray(), 16))));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 0, 32))));
            sigChunk2.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 32, 64))));
            sigChunk3.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 64, 96))));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(blockHash)),
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

        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

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
        PrecompiledTransactionContext initializationContext =
                context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(members[0].getAddress())))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(
                        new AionAddress(members[0].getAddress()),
                        CONTRACT_ADDR,
                        ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = HashUtil.h256("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[10];
        for (int i = 0; i < 10; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            AddressSpecs.computeA0Address(
                                    HashUtil.h256(Integer.toHexString(i).getBytes())),
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
        for (ECKey k : members) {
            signatures[i] = k.sign(payloadHash).toBytes();
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(new ByteArrayWrapper(b.getSourceTransactionHash())));
            addressList.add(new AddressFVM(new ByteArrayWrapper(b.getRecipient())));
            uintList.add(
                    new Uint128FVM(
                            new ByteArrayWrapper(
                                    PrecompiledUtilities.pad(
                                            b.getTransferValue().toByteArray(), 16))));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 0, 32))));
            sigChunk2.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 32, 64))));
            sigChunk3.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 64, 96))));
        }

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext incorrectRelaySubmitBundleContext =
                context(AddressUtils.ZERO_ADDRESS, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        incorrectRelaySubmitBundleContext,
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(blockHash)),
                                sourceTransactionList,
                                addressList,
                                uintList,
                                sigChunk1,
                                sigChunk2,
                                sigChunk3)
                        .encodeBytes();
        transferResult = this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);
    }

    @Test
    public void testTransfersGreaterThanMaxListSize() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(members[0].getAddress())))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // override defaults
        PrecompiledTransactionContext submitBundleContext =
                context(
                        new AionAddress(members[0].getAddress()),
                        CONTRACT_ADDR,
                        ByteUtil.EMPTY_BYTE_ARRAY);
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.valueOf(1024));
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = HashUtil.h256("blockHash".getBytes());

        // place a value greater than the maximum 1024
        BridgeTransfer[] transfers = new BridgeTransfer[1024];
        for (int i = 0; i < 1024; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            AddressSpecs.computeA0Address(
                                    HashUtil.h256(Integer.toHexString(i).getBytes())),
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
            sourceTransactionList.add(
                    new AddressFVM(new ByteArrayWrapper(b.getSourceTransactionHash())));
            addressList.add(new AddressFVM(new ByteArrayWrapper(b.getRecipient())));
            uintList.add(
                    new Uint128FVM(
                            new ByteArrayWrapper(
                                    PrecompiledUtilities.pad(
                                            b.getTransferValue().toByteArray(), 16))));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 0, 32))));
            sigChunk2.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 32, 64))));
            sigChunk3.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 64, 96))));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(blockHash)),
                                sourceTransactionList,
                                addressList,
                                uintList,
                                sigChunk1,
                                sigChunk2,
                                sigChunk3)
                        .encodeBytes();
        transferResult = this.contract.execute(callPayload, DEFAULT_NRG);

        /// VERIFICATION

        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

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
        PrecompiledTransactionContext initializationContext =
                context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(members[0].getAddress())))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(
                        new AionAddress(members[0].getAddress()),
                        CONTRACT_ADDR,
                        ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = HashUtil.h256("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[10];
        for (int i = 0; i < 10; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            AddressSpecs.computeA0Address(
                                    HashUtil.h256(Integer.toHexString(i).getBytes())),
                            sourceTransactionHash);
        }
        byte[] payloadHash = BridgeUtilities.computeBundleHash(blockHash, transfers);

        // ATB-4.1 in order to test, we pretend that a bundle is already complete
        this.connector.setBundle(payloadHash, submitBundleContext.copyOfTransactionHash());

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
            sourceTransactionList.add(
                    new AddressFVM(new ByteArrayWrapper(b.getSourceTransactionHash())));
            addressList.add(new AddressFVM(new ByteArrayWrapper(b.getRecipient())));
            uintList.add(
                    new Uint128FVM(
                            new ByteArrayWrapper(
                                    PrecompiledUtilities.pad(
                                            b.getTransferValue().toByteArray(), 16))));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 0, 32))));
            sigChunk2.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 32, 64))));
            sigChunk3.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 64, 96))));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(blockHash)),
                                sourceTransactionList,
                                addressList,
                                uintList,
                                sigChunk1,
                                sigChunk2,
                                sigChunk3)
                        .encodeBytes();
        transferResult = this.contract.execute(callPayload, DEFAULT_NRG);

        /// VERIFICATION
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);
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
        PrecompiledTransactionContext initializationContext =
                context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }

        // not initializing the ring

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(members[0].getAddress())))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(
                        new AionAddress(members[0].getAddress()),
                        CONTRACT_ADDR,
                        ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = HashUtil.h256("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[5];
        for (int i = 0; i < 5; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            AddressSpecs.computeA0Address(
                                    HashUtil.h256(Integer.toHexString(i).getBytes())),
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
        for (ECKey k : members) {
            signatures[i] = k.sign(payloadHash).toBytes();
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(new ByteArrayWrapper(b.getSourceTransactionHash())));
            addressList.add(new AddressFVM(new ByteArrayWrapper(b.getRecipient())));
            uintList.add(
                    new Uint128FVM(
                            new ByteArrayWrapper(
                                    PrecompiledUtilities.pad(
                                            b.getTransferValue().toByteArray(), 16))));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 0, 32))));
            sigChunk2.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 32, 64))));
            sigChunk3.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 64, 96))));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(blockHash)),
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

        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

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
        PrecompiledTransactionContext initializationContext =
                context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // not setting relayer

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(
                        new AionAddress(members[0].getAddress()),
                        CONTRACT_ADDR,
                        ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = HashUtil.h256("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[10];
        for (int i = 0; i < 10; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            AddressSpecs.computeA0Address(
                                    HashUtil.h256(Integer.toHexString(i).getBytes())),
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
        for (ECKey k : members) {
            signatures[i] = k.sign(payloadHash).toBytes();
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(new ByteArrayWrapper(b.getSourceTransactionHash())));
            addressList.add(new AddressFVM(new ByteArrayWrapper(b.getRecipient())));
            uintList.add(
                    new Uint128FVM(
                            new ByteArrayWrapper(
                                    PrecompiledUtilities.pad(
                                            b.getTransferValue().toByteArray(), 16))));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 0, 32))));
            sigChunk2.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 32, 64))));
            sigChunk3.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 64, 96))));
        }

        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(blockHash)),
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

        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

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
        PrecompiledTransactionContext initializationContext =
                context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(members[0].getAddress())))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(
                        new AionAddress(members[0].getAddress()),
                        CONTRACT_ADDR,
                        ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = HashUtil.h256("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[5];
        for (int i = 0; i < 5; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            AddressSpecs.computeA0Address(
                                    HashUtil.h256(Integer.toHexString(i).getBytes())),
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
        signatures[0] = members[0].sign(payloadHash).toBytes();
        signatures[1] = members[1].sign(payloadHash).toBytes();

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(new ByteArrayWrapper(b.getSourceTransactionHash())));
            addressList.add(new AddressFVM(new ByteArrayWrapper(b.getRecipient())));
            uintList.add(
                    new Uint128FVM(
                            new ByteArrayWrapper(
                                    PrecompiledUtilities.pad(
                                            b.getTransferValue().toByteArray(), 16))));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 0, 32))));
            sigChunk2.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 32, 64))));
            sigChunk3.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 64, 96))));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(blockHash)),
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

        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

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
        PrecompiledTransactionContext initializationContext =
                context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(members[0].getAddress())))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(
                        new AionAddress(members[0].getAddress()),
                        CONTRACT_ADDR,
                        ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = HashUtil.h256("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[5];
        for (int i = 0; i < 5; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            AddressSpecs.computeA0Address(
                                    HashUtil.h256(Integer.toHexString(i).getBytes())),
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
        signatures[0] = members[0].sign(payloadHash).toBytes();
        signatures[1] = members[1].sign(payloadHash).toBytes();
        signatures[2] = members[1].sign(payloadHash).toBytes();

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(new ByteArrayWrapper(b.getSourceTransactionHash())));
            addressList.add(new AddressFVM(new ByteArrayWrapper(b.getRecipient())));
            uintList.add(
                    new Uint128FVM(
                            new ByteArrayWrapper(
                                    PrecompiledUtilities.pad(
                                            b.getTransferValue().toByteArray(), 16))));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            // add incorrect signatures, copies [0:32] for all chunks
            sigChunk1.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 0, 32))));
            sigChunk2.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 0, 32))));
            sigChunk3.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 0, 32))));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(blockHash)),
                                sourceTransactionList,
                                addressList,
                                uintList,
                                sigChunk1,
                                sigChunk2,
                                sigChunk3)
                        .encodeBytes();

        // input will include 5 transfers and 3 validators with incorrect signatures
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

        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

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
        PrecompiledTransactionContext initializationContext =
                context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }

        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(members[0].getAddress())))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(
                        new AionAddress(members[0].getAddress()),
                        CONTRACT_ADDR,
                        ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = HashUtil.h256("blockHash".getBytes());

        BridgeTransfer[] transfers = new BridgeTransfer[5];
        for (int i = 0; i < 5; i++) {

            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            AddressSpecs.computeA0Address(
                                    HashUtil.h256(Integer.toHexString(i).getBytes())),
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
        for (ECKey k : members) {
            signatures[i] = k.sign(payloadHash).toBytes();
            i++;
        }

        ListFVM sourceTransactionList = new ListFVM();
        ListFVM addressList = new ListFVM();
        ListFVM uintList = new ListFVM();
        for (BridgeTransfer b : transfers) {
            sourceTransactionList.add(
                    new AddressFVM(new ByteArrayWrapper(b.getSourceTransactionHash())));
            addressList.add(new AddressFVM(new ByteArrayWrapper(b.getRecipient())));
            uintList.add(
                    new Uint128FVM(
                            new ByteArrayWrapper(
                                    PrecompiledUtilities.pad(
                                            b.getTransferValue().toByteArray(), 16))));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 0, 32))));
            sigChunk2.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 32, 64))));
            sigChunk3.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 64, 96))));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(blockHash)),
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

        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

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
                byte[] sourceTransactionHashDefault = HashUtil.h256(Integer.toString(2).getBytes());
                transfers[i] =
                        BridgeTransfer.getInstance(
                                BigInteger.ONE,
                                AddressSpecs.computeA0Address(
                                        HashUtil.h256(Integer.toHexString(2).getBytes())),
                                sourceTransactionHashDefault);
            } else {
                // generate a unique sourceTransactionHash for each transfer
                byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
                transfers[i] =
                        BridgeTransfer.getInstance(
                                BigInteger.ONE,
                                AddressSpecs.computeA0Address(
                                        HashUtil.h256(Integer.toHexString(i).getBytes())),
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

        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

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
            byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            AddressSpecs.computeA0Address(
                                    HashUtil.h256(Integer.toHexString(i).getBytes())),
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
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

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

        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

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
            byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            AddressSpecs.computeA0Address(
                                    HashUtil.h256(Integer.toHexString(i).getBytes())),
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
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

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

        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

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
        ECKey members[] = new ECKey[] {ECKeyFac.inst().create()};

        int n = 1;
        BridgeTransfer[] transfers = new BridgeTransfer[n];
        for (int i = 0; i < n; i++) {
            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            AddressSpecs.computeA0Address(
                                    HashUtil.h256(Integer.toHexString(i).getBytes())),
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
            assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);
        }
        System.out.println("fail count: " + i);

        // try with more bytes than expected
        byte[] input = new byte[555];
        System.arraycopy(callPayload, 0, input, 0, 404);
        PrecompiledTransactionResult result = this.contract.execute(input, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);
    }

    @Test
    public void testTransferFailLength2() {
        // instantiate a bundle with 10 transfers and 5 validators
        // input byte[] should have length of 1508 = 404 + (10-1)*80 + (5-1)*96
        ECKey members[] =
                new ECKey[] {
                    ECKeyFac.inst().create(),
                    ECKeyFac.inst().create(),
                    ECKeyFac.inst().create(),
                    ECKeyFac.inst().create(),
                    ECKeyFac.inst().create()
                };

        int n = 10; // number of transfers
        BridgeTransfer[] transfers = new BridgeTransfer[n];
        for (int i = 0; i < n; i++) {
            // generate a unique sourceTransactionHash for each transfer
            byte[] sourceTransactionHash = HashUtil.h256(Integer.toString(i).getBytes());
            transfers[i] =
                    BridgeTransfer.getInstance(
                            BigInteger.ONE,
                            AddressSpecs.computeA0Address(
                                    HashUtil.h256(Integer.toHexString(i).getBytes())),
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
            assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);
        }
        System.out.println("fail count: " + i);
    }

    private ReturnDataFromSetup setupForTest(BridgeTransfer[] transfers, ECKey[] members) {
        // override defaults
        PrecompiledTransactionContext initializationContext =
                context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        initializationContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // set relayer
        byte[] callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SET_RELAYER.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(members[0].getAddress())))
                        .encodeBytes();

        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // override defaults
        this.repository.addBalance(CONTRACT_ADDR, BigInteger.TEN);

        // we create a new token bridge contract here because we
        // need to change the execution context
        PrecompiledTransactionContext submitBundleContext =
                context(
                        new AionAddress(members[0].getAddress()),
                        CONTRACT_ADDR,
                        ByteUtil.EMPTY_BYTE_ARRAY);
        this.contract =
                new TokenBridgeContract(
                        submitBundleContext, this.repository, OWNER_ADDR, CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        // assemble the payload
        byte[] blockHash = HashUtil.h256("blockHash".getBytes());

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
            sourceTransactionList.add(
                    new AddressFVM(new ByteArrayWrapper(b.getSourceTransactionHash())));
            addressList.add(new AddressFVM(new ByteArrayWrapper(b.getRecipient())));
            uintList.add(
                    new Uint128FVM(
                            new ByteArrayWrapper(
                                    PrecompiledUtilities.pad(
                                            b.getTransferValue().toByteArray(), 16))));
        }

        ListFVM sigChunk1 = new ListFVM();
        ListFVM sigChunk2 = new ListFVM();
        ListFVM sigChunk3 = new ListFVM();
        for (byte[] sig : signatures) {
            sigChunk1.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 0, 32))));
            sigChunk2.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 32, 64))));
            sigChunk3.add(new AddressFVM(new ByteArrayWrapper(Arrays.copyOfRange(sig, 64, 96))));
        }

        callPayload =
                new AbiEncoder(
                                BridgeFuncSig.SIG_SUBMIT_BUNDLE.getSignature(),
                                new AddressFVM(new ByteArrayWrapper(blockHash)),
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
                        context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }

        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // try before
        byte[] callPayload =
                new AbiEncoder(BridgeFuncSig.PURE_RING_LOCKED.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);
        assertThat(transferResult.getReturnData()).isEqualTo(DataWordImpl.ONE.getData());

        // lock the ring
        this.connector.setRingLocked(false);

        // try after
        byte[] callPayload2 =
                new AbiEncoder(BridgeFuncSig.PURE_RING_LOCKED.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult transferResult2 =
                this.contract.execute(callPayload2, DEFAULT_NRG);
        assertThat(transferResult2.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);
        assertThat(transferResult2.getReturnData()).isEqualTo(DataWordImpl.ZERO.getData());
    }

    @Test
    public void testMinThreshold() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }

        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // try before
        byte[] callPayload =
                new AbiEncoder(BridgeFuncSig.PURE_MIN_THRESH.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);
        assertThat(transferResult.getReturnData())
                .isEqualTo(new DataWordImpl(new BigInteger("3")).getData());

        // explicitly set the min threshold to 5
        this.connector.setMinThresh(5);

        // try after
        byte[] callPayload2 =
                new AbiEncoder(BridgeFuncSig.PURE_MIN_THRESH.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult transferResult2 =
                this.contract.execute(callPayload2, DEFAULT_NRG);
        assertThat(transferResult2.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);
        assertThat(transferResult2.getReturnData())
                .isEqualTo(new DataWordImpl(new BigInteger("5")).getData());

        // try setting threshold greater than number of validator members
        this.connector.setMinThresh(10);

        // try after
        byte[] callPayload3 =
                new AbiEncoder(BridgeFuncSig.PURE_MIN_THRESH.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult transferResult3 =
                this.contract.execute(callPayload3, DEFAULT_NRG);
        assertThat(transferResult3.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);
        assertThat(transferResult3.getReturnData())
                .isEqualTo(new DataWordImpl(new BigInteger("10")).getData());
    }

    @Test
    public void testMemberCount() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }

        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // try before
        byte[] callPayload =
                new AbiEncoder(BridgeFuncSig.PURE_MEMBER_COUNT.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);
        assertThat(transferResult.getReturnData())
                .isEqualTo(new DataWordImpl(new BigInteger("5")).getData());

        // explicitly set the member count to 10
        this.connector.setMemberCount(10);

        // try after
        byte[] callPayload2 =
                new AbiEncoder(BridgeFuncSig.PURE_MEMBER_COUNT.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult transferResult2 =
                this.contract.execute(callPayload2, DEFAULT_NRG);
        assertThat(transferResult2.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);
        assertThat(transferResult2.getReturnData())
                .isEqualTo(new DataWordImpl(new BigInteger("10")).getData());
    }

    @Test
    public void testRingMap() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }

        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // create input byte[]
        byte[] callPayload = new byte[36];
        byte[] encodeBytes =
                new AbiEncoder(BridgeFuncSig.PURE_RING_MAP.getSignature()).encodeBytes();
        ECKey newKey = ECKeyFac.inst().create();
        byte[] randomAddress = newKey.getAddress();
        System.arraycopy(encodeBytes, 0, callPayload, 0, 4);
        System.arraycopy(randomAddress, 0, callPayload, 4, 32);

        // execute with valid input
        PrecompiledTransactionResult transferResult =
                this.contract.execute(callPayload, DEFAULT_NRG);
        assertThat(transferResult.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // execute with invalid input
        PrecompiledTransactionResult transferResult2 =
                this.contract.execute(encodeBytes, DEFAULT_NRG);
        assertThat(transferResult2.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);
    }

    @Test
    public void testFailRingInitialize() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }

        // address null
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature()).encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

        this.connector.setRingLocked(true);

        // failed to initialize due to locked ring
        byte[] payload2 =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        PrecompiledTransactionResult result2 = this.contract.execute(payload2, DEFAULT_NRG);
        assertThat(result2.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);
    }

    @Test
    public void testAddRingMember() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }

        // address null - fail
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_ADD_MEMBER.getSignature()).encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

        // add new member - fail
        byte[] sig =
                new AbiEncoder(BridgeFuncSig.SIG_RING_ADD_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] newMember = ECKeyFac.inst().create().getAddress(); // the new member
        byte[] payload2 = new byte[4 + 32];
        System.arraycopy(sig, 0, payload2, 0, 4);
        System.arraycopy(newMember, 0, payload2, 4, 32);

        PrecompiledTransactionResult result2 = this.contract.execute(payload2, DEFAULT_NRG);
        assertThat(result2.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

        // lock the ring
        this.connector.setRingLocked(true);

        // add new member - success
        byte[] sig3 =
                new AbiEncoder(BridgeFuncSig.SIG_RING_ADD_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] newMember3 = ECKeyFac.inst().create().getAddress(); // the new member
        byte[] payload3 = new byte[4 + 32];
        System.arraycopy(sig3, 0, payload3, 0, 4);
        System.arraycopy(newMember3, 0, payload3, 4, 32);

        PrecompiledTransactionResult result3 = this.contract.execute(payload3, DEFAULT_NRG);
        assertThat(result3.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);
    }

    @Test
    public void testAddRingMemberNotOwner() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(
                                AddressUtils.ZERO_ADDRESS,
                                CONTRACT_ADDR,
                                ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }

        // lock the ring
        this.connector.setRingLocked(true);

        // add new member - success
        byte[] sig3 =
                new AbiEncoder(BridgeFuncSig.SIG_RING_ADD_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] newMember3 = ECKeyFac.inst().create().getAddress(); // the new member
        byte[] payload3 = new byte[4 + 32];
        System.arraycopy(sig3, 0, payload3, 0, 4);
        System.arraycopy(newMember3, 0, payload3, 4, 32);

        PrecompiledTransactionResult result3 = this.contract.execute(payload3, DEFAULT_NRG);
        assertThat(result3.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);
    }

    @Test
    public void testRemoveRingMember() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }

        // address null - fail
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_REMOVE_MEMBER.getSignature()).encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

        // add new member - fail
        byte[] sig =
                new AbiEncoder(BridgeFuncSig.SIG_RING_REMOVE_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] newMember = ECKeyFac.inst().create().getAddress(); // the new member
        byte[] payload2 = new byte[4 + 32];
        System.arraycopy(sig, 0, payload2, 0, 4);
        System.arraycopy(newMember, 0, payload2, 4, 32);

        PrecompiledTransactionResult result2 = this.contract.execute(payload2, DEFAULT_NRG);
        assertThat(result2.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

        // initialize ring
        byte[] ring =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        this.contract.execute(ring, DEFAULT_NRG);

        // remove member - fail, member does not exist
        byte[] sig3 =
                new AbiEncoder(BridgeFuncSig.SIG_RING_REMOVE_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] newMember3 = ECKeyFac.inst().create().getAddress(); // the new member
        byte[] payload3 = new byte[4 + 32];
        System.arraycopy(sig3, 0, payload3, 0, 4);
        System.arraycopy(newMember3, 0, payload3, 4, 32);

        PrecompiledTransactionResult result3 = this.contract.execute(payload3, DEFAULT_NRG);
        assertThat(result3.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

        // remove member - success, member exists
        byte[] sig4 =
                new AbiEncoder(BridgeFuncSig.SIG_RING_REMOVE_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] payload4 = new byte[4 + 32];
        System.arraycopy(sig4, 0, payload4, 0, 4);
        System.arraycopy(members[0].getAddress(), 0, payload4, 4, 32);

        PrecompiledTransactionResult result4 = this.contract.execute(payload4, DEFAULT_NRG);
        assertThat(result4.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);
    }

    @Test
    public void testRemoveRingMemberNotOwner() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }

        // address null - fail
        byte[] payload =
                new AbiEncoder(BridgeFuncSig.SIG_RING_REMOVE_MEMBER.getSignature()).encodeBytes();
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

        // add new member - fail
        byte[] sig =
                new AbiEncoder(BridgeFuncSig.SIG_RING_REMOVE_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] newMember = ECKeyFac.inst().create().getAddress(); // the new member
        byte[] payload2 = new byte[4 + 32];
        System.arraycopy(sig, 0, payload2, 0, 4);
        System.arraycopy(newMember, 0, payload2, 4, 32);

        PrecompiledTransactionResult result2 = this.contract.execute(payload2, DEFAULT_NRG);
        assertThat(result2.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

        // initialize ring
        byte[] ring =
                new AbiEncoder(BridgeFuncSig.SIG_RING_INITIALIZE.getSignature(), encodingList)
                        .encodeBytes();
        this.contract.execute(ring, DEFAULT_NRG);

        // remove member - fail, member does not exist
        byte[] sig3 =
                new AbiEncoder(BridgeFuncSig.SIG_RING_REMOVE_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] newMember3 = ECKeyFac.inst().create().getAddress(); // the new member
        byte[] payload3 = new byte[4 + 32];
        System.arraycopy(sig3, 0, payload3, 0, 4);
        System.arraycopy(newMember3, 0, payload3, 4, 32);

        PrecompiledTransactionResult result3 = this.contract.execute(payload3, DEFAULT_NRG);
        assertThat(result3.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(
                                AddressUtils.ZERO_ADDRESS,
                                CONTRACT_ADDR,
                                ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        // failure, member exists but sender is no longer owner
        byte[] sig4 =
                new AbiEncoder(BridgeFuncSig.SIG_RING_REMOVE_MEMBER.getSignature(), encodingList)
                        .encodeBytes();
        byte[] payload4 = new byte[4 + 32];
        System.arraycopy(sig4, 0, payload4, 0, 4);
        System.arraycopy(members[0].getAddress(), 0, payload4, 4, 32);

        PrecompiledTransactionResult result4 = this.contract.execute(payload4, DEFAULT_NRG);
        assertThat(result4.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);
    }

    @Test
    public void testSetReplayer() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(OWNER_ADDR, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        ListFVM encodingList = new ListFVM();
        for (ECKey k : members) {
            encodingList.add(new AddressFVM(new ByteArrayWrapper(k.getAddress())));
        }

        // address null
        byte[] nullInput =
                new AbiEncoder(BridgeFuncSig.SIG_SET_RELAYER.getSignature()).encodeBytes();
        PrecompiledTransactionResult res = this.contract.execute(nullInput, DEFAULT_NRG);
        assertThat(res.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);

        // address valid
        byte[] sig = new AbiEncoder(BridgeFuncSig.SIG_SET_RELAYER.getSignature()).encodeBytes();
        byte[] newReplayer = ECKeyFac.inst().create().getAddress();
        byte[] payload = new byte[4 + 32];

        System.arraycopy(sig, 0, payload, 0, 4);
        System.arraycopy(newReplayer, 0, payload, 4, 32);
        PrecompiledTransactionResult result = this.contract.execute(payload, DEFAULT_NRG);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);

        // caller not owner - fail
        AionAddress address1 = new AionAddress(ECKeyFac.inst().create().getAddress());
        this.contract =
                new TokenBridgeContract(
                        context(address1, CONTRACT_ADDR, ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        byte[] sig2 = new AbiEncoder(BridgeFuncSig.SIG_SET_RELAYER.getSignature()).encodeBytes();
        byte[] newReplayer2 = ECKeyFac.inst().create().getAddress();
        byte[] payload2 = new byte[4 + 32];

        System.arraycopy(sig2, 0, payload2, 0, 4);
        System.arraycopy(newReplayer2, 0, payload2, 4, 32);
        PrecompiledTransactionResult result2 = this.contract.execute(payload2, DEFAULT_NRG);
        assertThat(result2.getResultCode()).isEqualTo(PrecompiledResultCode.FAILURE);
    }

    @Test
    public void testFallbackTransaction() {
        // override defaults
        this.contract =
                new TokenBridgeContract(
                        context(
                                AddressUtils.ZERO_ADDRESS,
                                CONTRACT_ADDR,
                                ByteUtil.EMPTY_BYTE_ARRAY),
                        this.repository,
                        OWNER_ADDR,
                        CONTRACT_ADDR);
        this.controller = this.contract.getController();
        this.connector = this.contract.getConnector();

        assertThat(this.connector.getInitialized()).isFalse();
        PrecompiledTransactionResult result =
                this.contract.execute(ByteUtil.EMPTY_BYTE_ARRAY, 21_000L);
        assertThat(result.getResultCode()).isEqualTo(PrecompiledResultCode.SUCCESS);
        assertThat(this.connector.getInitialized()).isTrue();
    }
}
