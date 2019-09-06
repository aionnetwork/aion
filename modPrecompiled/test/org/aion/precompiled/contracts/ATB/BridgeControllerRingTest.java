package org.aion.precompiled.contracts.ATB;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.precompiled.contracts.ATB.BridgeTestUtils.dummyContext;

import java.util.Random;
import org.aion.precompiled.ExternalCapabilitiesForTesting;
import org.aion.precompiled.ExternalStateForTests;
import org.aion.precompiled.type.CapabilitiesProvider;
import org.aion.precompiled.type.IExternalStateForPrecompiled;
import org.aion.types.AionAddress;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class BridgeControllerRingTest {

    private BridgeStorageConnector connector;
    private BridgeController controller;

    private static final byte[][] members = new byte[5][32];

    private static ExternalCapabilitiesForTesting capabilities;

    private static AionAddress CONTRACT_ADDR;
    private static AionAddress OWNER_ADDR;
    private static byte[] memberAddress;

    @BeforeClass
    public static void setupCapabilities() {
        capabilities = new ExternalCapabilitiesForTesting();
        CapabilitiesProvider.installExternalCapabilities(capabilities);
        CONTRACT_ADDR = new AionAddress(capabilities.blake2b("contractAddress".getBytes()));
        OWNER_ADDR = new AionAddress(capabilities.blake2b("ownerAddress".getBytes()));
        memberAddress = capabilities.blake2b("memberAddress".getBytes());

        Random r = new Random();
        for (int i = 0; i < members.length; i++) {
            byte[] addr = new byte[32];
            r.nextBytes(addr);
            members[i] = addr;
        }
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

        // setup initial ring structure
        this.controller.ringInitialize(OWNER_ADDR.toByteArray(), members);
    }

    @Test
    public void testRingInitialization() {
        for (byte[] addr : members) {
            assertThat(this.connector.getActiveMember(addr)).isTrue();
        }
    }

    @Test
    public void testRingReinitialization() {
        ErrCode code = this.controller.ringInitialize(OWNER_ADDR.toByteArray(), members);
        assertThat(code).isEqualTo(ErrCode.RING_LOCKED);
    }

    @Test
    public void testRingAddMember() {
        ErrCode code = this.controller.ringAddMember(OWNER_ADDR.toByteArray(), memberAddress);
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);
    }

    @Test
    public void testRingAddMemberNotOwner() {
        ErrCode code = this.controller.ringAddMember(CONTRACT_ADDR.toByteArray(), memberAddress);
        assertThat(code).isEqualTo(ErrCode.NOT_OWNER);
    }

    @Test
    public void testRingAddExistingMember() {
        // add member twice
        this.controller.ringAddMember(OWNER_ADDR.toByteArray(), memberAddress);
        ErrCode code = this.controller.ringAddMember(OWNER_ADDR.toByteArray(), memberAddress);
        assertThat(code).isEqualTo(ErrCode.RING_MEMBER_EXISTS);
    }

    @Test
    public void testRingRemoveMember() {
        ErrCode code;
        code = this.controller.ringAddMember(OWNER_ADDR.toByteArray(), memberAddress);
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);
        assertThat(this.connector.getActiveMember(memberAddress)).isTrue();

        code = this.controller.ringRemoveMember(OWNER_ADDR.toByteArray(), memberAddress);
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);
        assertThat(this.connector.getActiveMember(memberAddress)).isFalse();
    }

    @Test
    public void testRingRemoveMemberNotOwner() {
        ErrCode code = this.controller.ringRemoveMember(CONTRACT_ADDR.toByteArray(), memberAddress);
        assertThat(code).isEqualTo(ErrCode.NOT_OWNER);
    }

    @Test
    public void testRingRemoveNonExistingMember() {
        ErrCode code = this.controller.ringRemoveMember(OWNER_ADDR.toByteArray(), memberAddress);
        assertThat(code).isEqualTo(ErrCode.RING_MEMBER_NOT_EXISTS);
    }
}
