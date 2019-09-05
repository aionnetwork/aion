package org.aion.precompiled.contracts.ATB;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.precompiled.contracts.ATB.BridgeTestUtils.dummyContext;

import org.aion.crypto.HashUtil;
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
    private static final AionAddress CONTRACT_ADDR =
            new AionAddress(HashUtil.h256("contractAddress".getBytes()));
    private static final AionAddress OWNER_ADDR =
            new AionAddress(HashUtil.h256("ownerAddress".getBytes()));

    @BeforeClass
    public static void setupCapabilities() {
        CapabilitiesProvider.installExternalCapabilities(new ExternalCapabilitiesForTesting());
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
                    HashUtil.h256("member1".getBytes()),
                    HashUtil.h256("member2".getBytes()),
                    HashUtil.h256("member3".getBytes()),
                    HashUtil.h256("member4".getBytes()),
                    HashUtil.h256("member5".getBytes())
                };
        ErrCode code = this.controller.ringInitialize(OWNER_ADDR.toByteArray(), members);
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);
        assertThat(this.connector.getMemberCount()).isEqualTo(5);
        assertThat(this.connector.getMinThresh()).isEqualTo(3);
    }

    @Test
    public void testRingInitializationNotOwner() {
        byte[] notOwner = HashUtil.h256("not owner".getBytes());
        ErrCode code = this.controller.ringInitialize(notOwner, new byte[][] {});
        assertThat(code).isEqualTo(ErrCode.NOT_OWNER);

        assertThat(this.connector.getMemberCount()).isEqualTo(0);
        assertThat(this.connector.getMinThresh()).isEqualTo(0);
    }
}
