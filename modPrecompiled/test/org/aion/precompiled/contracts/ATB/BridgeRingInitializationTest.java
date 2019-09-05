package org.aion.precompiled.contracts.ATB;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.precompiled.contracts.ATB.BridgeTestUtils.dummyContext;

import org.aion.precompiled.ExternalCapabilitiesForTesting;
import org.aion.precompiled.ExternalStateForTests;
import org.aion.precompiled.type.CapabilitiesProvider;
import org.aion.precompiled.type.IExternalStateForPrecompiled;
import org.aion.types.AionAddress;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class BridgeRingInitializationTest {

    private BridgeStorageConnector connector;
    private BridgeController controller;
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
        this.controller =
                new BridgeController(
                        connector, dummyContext().getLogs(), CONTRACT_ADDR, OWNER_ADDR);
        this.controller.initialize();
    }

    @Test
    public void testRingEmptyInitialization() {
        ErrCode code = this.controller.ringInitialize(OWNER_ADDR.toByteArray(), new byte[][] {});
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);
        assertThat(this.connector.getMemberCount()).isEqualTo(0);
        assertThat(this.connector.getMinThresh()).isEqualTo(1);
    }

    @Test
    public void testRingSingleMemberInitialization() {
        ErrCode code =
                this.controller.ringInitialize(
                        OWNER_ADDR.toByteArray(), new byte[][] {OWNER_ADDR.toByteArray()});
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);
        assertThat(this.connector.getMemberCount()).isEqualTo(1);
        assertThat(this.connector.getMinThresh()).isEqualTo(1);
    }

    @Test
    public void testRingMultiMemberInitialization() {
        byte[][] members =
                new byte[][] {
                    capabilities.blake2b("member1".getBytes()),
                    capabilities.blake2b("member2".getBytes()),
                    capabilities.blake2b("member3".getBytes()),
                    capabilities.blake2b("member4".getBytes()),
                    capabilities.blake2b("member5".getBytes())
                };
        ErrCode code = this.controller.ringInitialize(OWNER_ADDR.toByteArray(), members);
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);
        assertThat(this.connector.getMemberCount()).isEqualTo(5);
        assertThat(this.connector.getMinThresh()).isEqualTo(3);
    }

    @Test
    public void testRingInitializationNotOwner() {
        byte[] notOwner = capabilities.blake2b("not owner".getBytes());
        ErrCode code = this.controller.ringInitialize(notOwner, new byte[][] {});
        assertThat(code).isEqualTo(ErrCode.NOT_OWNER);

        assertThat(this.connector.getMemberCount()).isEqualTo(0);
        assertThat(this.connector.getMinThresh()).isEqualTo(0);
    }
}
