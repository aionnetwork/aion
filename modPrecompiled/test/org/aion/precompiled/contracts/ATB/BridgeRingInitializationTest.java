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

import org.aion.base.type.AionAddress;
import org.aion.crypto.HashUtil;
import org.aion.precompiled.contracts.DummyRepo;
import org.junit.Before;
import org.junit.Test;

public class BridgeRingInitializationTest {

    private BridgeStorageConnector connector;
    private BridgeController controller;
    private static final AionAddress CONTRACT_ADDR =
            new AionAddress(HashUtil.h256("contractAddress".getBytes()));
    private static final AionAddress OWNER_ADDR = new AionAddress(HashUtil.h256("ownerAddress".getBytes()));

    @Before
    public void beforeEach() {
        DummyRepo repo = new DummyRepo();
        this.connector = new BridgeStorageConnector(repo, CONTRACT_ADDR);
        this.controller =
                new BridgeController(connector, dummyContext().helper(), CONTRACT_ADDR, OWNER_ADDR);
        this.controller.initialize();
    }

    @Test
    public void testRingEmptyInitialization() {
        ErrCode code = this.controller.ringInitialize(OWNER_ADDR.toBytes(), new byte[][] {});
        assertThat(code).isEqualTo(ErrCode.NO_ERROR);
        assertThat(this.connector.getMemberCount()).isEqualTo(0);
        assertThat(this.connector.getMinThresh()).isEqualTo(1);
    }

    @Test
    public void testRingSingleMemberInitialization() {
        ErrCode code =
                this.controller.ringInitialize(
                        OWNER_ADDR.toBytes(), new byte[][] {OWNER_ADDR.toBytes()});
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
        ErrCode code = this.controller.ringInitialize(OWNER_ADDR.toBytes(), members);
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
