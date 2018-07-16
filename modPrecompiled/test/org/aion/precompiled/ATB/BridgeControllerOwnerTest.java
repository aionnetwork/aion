package org.aion.precompiled.ATB;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.HashUtil;
import org.aion.mcf.vm.types.Log;
import org.aion.precompiled.DummyRepo;
import org.aion.precompiled.contracts.ATB.BridgeController;
import org.aion.precompiled.contracts.ATB.BridgeStorageConnector;
import org.aion.precompiled.contracts.ATB.ErrCode;
import org.aion.vm.ExecutionContext;
import org.aion.vm.TransactionResult;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.precompiled.ATB.BridgeTestUtils.*;

public class BridgeControllerOwnerTest {

    private BridgeStorageConnector connector;
    private BridgeController controller;
    private TransactionResult result;

    private static final Address CONTRACT_ADDR = new Address(HashUtil.h256("contractAddress".getBytes()));
    private static final Address OWNER_ADDR = new Address(HashUtil.h256("ownerAddress".getBytes()));

    @Before
    public void beforeEach() {
        DummyRepo repo = new DummyRepo();
        this.connector = new BridgeStorageConnector((IRepositoryCache) repo, CONTRACT_ADDR);

        ExecutionContext context = dummyContext();
        this.result = context.result();
        this.controller = new BridgeController(connector,
                this.result, CONTRACT_ADDR, OWNER_ADDR);
    }

    @Test
    public void testInitialize() {
        this.controller.initialize();
        assertThat(this.connector.getOwner()).isEqualTo(OWNER_ADDR.toBytes());
    }

    @Test
    public void testTransferOwnership() {
        byte[] transferOwnership = HashUtil.keccak256("ChangedOwner(address)".getBytes());
        byte[] newOwner = HashUtil.h256("newOwner".getBytes());
        this.controller.initialize();
        this.controller.setNewOwner(OWNER_ADDR.toBytes(), newOwner);

        // sanity check
        assertThat(this.connector.getNewOwner()).isEqualTo(newOwner);
        ErrCode err = this.controller.acceptOwnership(newOwner);
        assertThat(err).isEqualTo(ErrCode.NO_ERROR);

        assertThat(this.connector.getOwner()).isEqualTo(newOwner);
        // check that an event was properly generated
        List<Log> logs = this.result.getLogs();
        assertThat(logs.size()).isEqualTo(1);

        Log changedOwnerLog = logs.get(0);
        assertThat(changedOwnerLog.getData()).isEqualTo(ByteUtil.EMPTY_BYTE_ARRAY);
        assertThat(changedOwnerLog.getTopics().get(0)).isEqualTo(transferOwnership);
        assertThat(changedOwnerLog.getTopics().get(1)).isEqualTo(newOwner);
    }

    @Test
    public void testInvalidOwnerTransferOwnership() {
        byte[] notOwner = HashUtil.h256("not owner".getBytes());
        byte[] newOwner = HashUtil.h256("newOwner".getBytes());
        this.controller.initialize();
        ErrCode err = this.controller.setNewOwner(notOwner, newOwner);
        assertThat(err).isEqualTo(ErrCode.NOT_OWNER);
    }
}
