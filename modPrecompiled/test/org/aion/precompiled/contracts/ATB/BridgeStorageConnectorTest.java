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

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.HashUtil;
import org.aion.precompiled.contracts.DummyRepo;
import org.aion.vm.api.interfaces.Address;
import org.junit.Before;
import org.junit.Test;

public class BridgeStorageConnectorTest {
    private BridgeStorageConnector connector;
    private static final Address contractAddress = AionAddress.ZERO_ADDRESS();

    @Before
    public void beforeEach() {
        DummyRepo repo = new DummyRepo();
        this.connector = new BridgeStorageConnector((IRepositoryCache) repo, contractAddress);
    }

    // should be null
    @Test
    public void testDefaultOwnerAddress() {
        assertThat(this.connector.getOwner()).isNull();
    }

    @Test
    public void testDefaultNewOwnerAddress() {
        assertThat(this.connector.getNewOwner()).isNull();
    }

    @Test
    public void testDefaultMemberCount() {
        assertThat(this.connector.getMemberCount()).isEqualTo(0);
    }

    @Test
    public void testDefaultMinThresh() {
        assertThat(this.connector.getMinThresh()).isEqualTo(0);
    }

    @Test
    public void testDefaultRingLocked() {
        assertThat(this.connector.getRingLocked()).isFalse();
    }

    @Test
    public void testDefaultMemberMap() {
        byte[] memberKey = HashUtil.h256("member1".getBytes());
        assertThat(this.connector.getActiveMember(memberKey)).isFalse();
    }

    @Test
    public void testDefaultBundleMap() {
        byte[] bundleKey = HashUtil.h256("active1".getBytes());
        assertThat(this.connector.getBundle(bundleKey)).isEqualTo(ByteUtil.EMPTY_WORD);
    }

    @Test
    public void testDefaultInitialized() {
        assertThat(this.connector.getInitialized()).isFalse();
    }

    @Test
    public void testNoKeyOverlap() {
        byte[] key = HashUtil.h256("key".getBytes());
        this.connector.setActiveMember(key, true);
        this.connector.setBundle(key, ByteUtil.EMPTY_WORD);

        assertThat(this.connector.getActiveMember(key)).isTrue();
        assertThat(this.connector.getBundle(key)).isEqualTo(ByteUtil.EMPTY_WORD);

        // test that the reverse is true
        byte[] hash = HashUtil.h256("hash".getBytes());
        this.connector.setActiveMember(key, false);
        this.connector.setBundle(key, hash);

        assertThat(this.connector.getActiveMember(key)).isFalse();
        assertThat(this.connector.getBundle(key)).isEqualTo(hash);
    }

    @Test
    public void testOwnerAddress() {
        byte[] ownerAddress = HashUtil.h256("ownerAddress".getBytes());
        this.connector.setOwner(ownerAddress);
        byte[] retrieved = this.connector.getOwner();
        assertThat(retrieved).isEqualTo(ownerAddress);
    }

    @Test
    public void testNewOwnerAddress() {
        byte[] newOwnerAddress = HashUtil.h256("newOwnerAddress".getBytes());
        this.connector.setNewOwner(newOwnerAddress);
        byte[] retrieved = this.connector.getNewOwner();
        assertThat(retrieved).isEqualTo(newOwnerAddress);
    }

    @Test
    public void testInitialized() {
        this.connector.setInitialized(false);
        assertThat(this.connector.getInitialized()).isFalse();

        this.connector.setInitialized(true);
        assertThat(this.connector.getInitialized()).isTrue();
    }
}
