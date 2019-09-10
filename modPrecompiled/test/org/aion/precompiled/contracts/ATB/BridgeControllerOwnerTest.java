package org.aion.precompiled.contracts.ATB;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.precompiled.contracts.ATB.BridgeTestUtils.dummyContext;

import java.util.List;
import org.aion.precompiled.ExternalCapabilitiesForTesting;
import org.aion.precompiled.ExternalStateForTests;
import org.aion.precompiled.type.IExternalStateForPrecompiled;
import org.aion.precompiled.type.PrecompiledTransactionContext;
import org.aion.precompiled.type.CapabilitiesProvider;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class BridgeControllerOwnerTest {

    private BridgeStorageConnector connector;
    private BridgeController controller;
    private List<Log> logs;
    private static ExternalCapabilitiesForTesting capabilities;

    private static AionAddress CONTRACT_ADDR;
    private static AionAddress OWNER_ADDR;

    @BeforeClass
    public static void setupCapabilities() {
        capabilities = new ExternalCapabilitiesForTesting();
        CapabilitiesProvider.installExternalCapabilities(capabilities);
        CONTRACT_ADDR = new AionAddress(capabilities.blake2b("contractAddress".getBytes()));
        OWNER_ADDR = new AionAddress(capabilities.blake2b("ownerAddress".getBytes()));
    }

    @AfterClass
    public static void teardownCapabilities() {
        CapabilitiesProvider.removeExternalCapabilities();
    }

    @Before
    public void beforeEach() {
        IExternalStateForPrecompiled worldState = ExternalStateForTests.usingDefaultRepository();
        this.connector = new BridgeStorageConnector(worldState, CONTRACT_ADDR);

        PrecompiledTransactionContext context = dummyContext();
        this.logs = context.getLogs();
        this.controller = new BridgeController(connector, this.logs, CONTRACT_ADDR, OWNER_ADDR);
    }

    @Test
    public void testInitialize() {
        this.controller.initialize();
        assertThat(this.connector.getOwner()).isEqualTo(OWNER_ADDR.toByteArray());
    }

    @Test
    public void testTransferOwnership() {
        byte[] transferOwnership = capabilities.keccak256("ChangedOwner(address)".getBytes());
        byte[] newOwner = capabilities.blake2b("newOwner".getBytes());
        this.controller.initialize();
        this.controller.setNewOwner(OWNER_ADDR.toByteArray(), newOwner);

        // sanity check
        assertThat(this.connector.getNewOwner()).isEqualTo(newOwner);
        ErrCode err = this.controller.acceptOwnership(newOwner);
        assertThat(err).isEqualTo(ErrCode.NO_ERROR);

        assertThat(this.connector.getOwner()).isEqualTo(newOwner);
        // check that an event was properly generated
        assertThat(this.logs.size()).isEqualTo(1);

        Log changedOwnerLog = this.logs.get(0);
        assertThat(changedOwnerLog.copyOfData()).isEqualTo(new byte[0]);
        assertThat(changedOwnerLog.copyOfTopics().get(0)).isEqualTo(transferOwnership);
        assertThat(changedOwnerLog.copyOfTopics().get(1)).isEqualTo(newOwner);
    }

    @Test
    public void testInvalidOwnerTransferOwnership() {
        byte[] notOwner = capabilities.blake2b("not owner".getBytes());
        byte[] newOwner = capabilities.blake2b("newOwner".getBytes());
        this.controller.initialize();
        ErrCode err = this.controller.setNewOwner(notOwner, newOwner);
        assertThat(err).isEqualTo(ErrCode.NOT_OWNER);
    }
}
