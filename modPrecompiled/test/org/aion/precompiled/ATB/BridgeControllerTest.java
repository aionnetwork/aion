package org.aion.precompiled.ATB;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.crypto.HashUtil;
import org.aion.precompiled.DummyRepo;
import org.aion.precompiled.contracts.ATB.BridgeController;
import org.aion.precompiled.contracts.ATB.BridgeStorageConnector;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class BridgeControllerTest {

    private BridgeStorageConnector connector;
    private BridgeController controller;
    private static final Address CONTRACT_ADDR = new Address(HashUtil.h256("contractAddress".getBytes()));
    private static final Address OWNER_ADDR = new Address(HashUtil.h256("ownerAddress".getBytes()));

    @Before
    public void beforeEach() {
        DummyRepo repo = new DummyRepo();
        this.connector = new BridgeStorageConnector((IRepositoryCache) repo, CONTRACT_ADDR);
        this.controller = new BridgeController(connector, CONTRACT_ADDR, OWNER_ADDR);
    }

    @Test
    public void testInitialize() {
        this.controller.initialize();
        assertThat(this.connector.getOwner()).isEqualTo(OWNER_ADDR.toBytes());
    }

    @Test
    public void testTransferOwnership() {
        byte[] newOwner = HashUtil.h256("newOwner".getBytes());
        this.controller.initialize();
        this.controller.setNewOwner(OWNER_ADDR.toBytes(), newOwner);

        // sanity check
        assertThat(this.connector.getNewOwner()).isEqualTo(newOwner);

        BridgeController.ErrCode err = this.controller.acceptOwnership(newOwner);
        assertThat(err).isEqualTo(BridgeController.ErrCode.NO_ERROR);

        assertThat(this.connector.getOwner()).isEqualTo(newOwner);
    }

    @Test
    public void testInvalidOwnerTransferOwnership() {
        byte[] notOwner = HashUtil.h256("not owner".getBytes());
        byte[] newOwner = HashUtil.h256("newOwner".getBytes());

        this.controller.initialize();
        BridgeController.ErrCode err = this.controller.setNewOwner(notOwner, newOwner);
        assertThat(err).isEqualTo(BridgeController.ErrCode.NOT_OWNER);
    }
}
