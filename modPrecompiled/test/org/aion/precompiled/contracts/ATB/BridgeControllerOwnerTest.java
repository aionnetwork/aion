/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.precompiled.contracts.ATB;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.precompiled.contracts.ATB.BridgeTestUtils.dummyContext;

import java.util.List;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.HashUtil;
import org.aion.mcf.vm.types.Log;
import org.aion.precompiled.contracts.DummyRepo;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionHelper;
import org.junit.Before;
import org.junit.Test;

public class BridgeControllerOwnerTest {

    private BridgeStorageConnector connector;
    private BridgeController controller;
    private ExecutionHelper result;

    private static final AionAddress CONTRACT_ADDR =
            new AionAddress(HashUtil.h256("contractAddress".getBytes()));
    private static final AionAddress OWNER_ADDR = new AionAddress(HashUtil.h256("ownerAddress".getBytes()));

    @Before
    public void beforeEach() {
        DummyRepo repo = new DummyRepo();
        this.connector = new BridgeStorageConnector(repo, CONTRACT_ADDR);

        ExecutionContext context = dummyContext();
        this.result = context.helper();
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
        List<Log> logs = this.result.getLogs();
        assertThat(logs.size()).isEqualTo(1);

        Log changedOwnerLog = logs.get(0);
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
