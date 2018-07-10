package org.aion.precompiled.ATB;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.precompiled.DummyRepo;
import org.aion.precompiled.contracts.ATB.BridgeController;
import org.aion.precompiled.contracts.ATB.BridgeStorageConnector;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class BridgeControllerRingTest {

    private BridgeStorageConnector connector;
    private BridgeController controller;
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
        byte[][] memberList = new byte[members.length][];
        for (int i = 0; i < members.length; i++) {
            memberList[i] = members[i].getAddress();
        }
        return memberList;
    }

    @Before
    public void beforeEach() {
        DummyRepo repo = new DummyRepo();
        this.connector = new BridgeStorageConnector((IRepositoryCache) repo, CONTRACT_ADDR);
        this.controller = new BridgeController(connector, CONTRACT_ADDR, OWNER_ADDR);
        this.controller.initialize();

        byte[][] memberList = new byte[members.length][];
        for (int i = 0; i < members.length; i++) {
            memberList[i] = members[i].getAddress();
        }
        // setup initial ring structure
        this.controller.ringInitialize(OWNER_ADDR.toBytes(), memberList);
    }

    @Test
    public void testRingInitialization() {
        for (ECKey k : members) {
            assertThat(this.connector.getActiveMember(k.getAddress())).isTrue();
        }
    }

    @Test
    public void testRingReinitialization() {
        BridgeController.ErrCode code = this.controller.ringInitialize(
                OWNER_ADDR.toBytes(),
                getMemberAddress(members));
        assertThat(code).isEqualTo(BridgeController.ErrCode.RING_LOCKED);
    }

    @Test
    public void testRingAddMember() {
        // TODO
    }

    @Test
    public void testRingAddMemberNotOwner() {
        // TODO
    }

    @Test
    public void testRingAddExistingMember() {
        // TODO
    }

    @Test
    public void testRingRemoveMember() {
        // TODO
    }

    @Test
    public void testRingRemoveMemberNotOwner() {
        // TODO
    }

    @Test
    public void testRingRemoveNonExistingMember() {
        // TODO
    }
}
