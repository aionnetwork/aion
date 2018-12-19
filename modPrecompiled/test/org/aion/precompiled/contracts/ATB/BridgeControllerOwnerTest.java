package org.aion.precompiled.contracts.ATB;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.precompiled.contracts.ATB.BridgeTestUtils.dummyContext;

import java.util.List;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.HashUtil;
import org.aion.fastvm.ExecutionContext;
import org.aion.precompiled.contracts.DummyRepo;
import org.aion.vm.api.interfaces.Address;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.vm.api.interfaces.TransactionSideEffects;
import org.junit.Before;
import org.junit.Test;

public class BridgeControllerOwnerTest {

    private BridgeStorageConnector connector;
    private BridgeController controller;
    private TransactionSideEffects result;

    private static final Address CONTRACT_ADDR =
            new AionAddress(HashUtil.h256("contractAddress".getBytes()));
    private static final Address OWNER_ADDR =
            new AionAddress(HashUtil.h256("ownerAddress".getBytes()));

    @Before
    public void beforeEach() {
        DummyRepo repo = new DummyRepo();
        this.connector = new BridgeStorageConnector(repo, CONTRACT_ADDR);

        ExecutionContext context = dummyContext();
        this.result = context.getSideEffects();
        this.controller = new BridgeController(connector, this.result, CONTRACT_ADDR, OWNER_ADDR);
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
        List<IExecutionLog> logs = this.result.getExecutionLogs();
        assertThat(logs.size()).isEqualTo(1);

        IExecutionLog changedOwnerLog = logs.get(0);
        assertThat(changedOwnerLog.getLogData()).isEqualTo(ByteUtil.EMPTY_BYTE_ARRAY);
        assertThat(changedOwnerLog.getLogTopics().get(0)).isEqualTo(transferOwnership);
        assertThat(changedOwnerLog.getLogTopics().get(1)).isEqualTo(newOwner);
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
