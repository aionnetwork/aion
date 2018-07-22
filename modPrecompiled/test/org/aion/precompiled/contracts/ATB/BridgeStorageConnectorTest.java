package org.aion.precompiled.contracts.ATB;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.crypto.HashUtil;
import org.aion.precompiled.DummyRepo;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.*;

public class BridgeStorageConnectorTest {
    private BridgeStorageConnector connector;
    private static final Address contractAddress = Address.ZERO_ADDRESS();

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
        assertThat(this.connector.getBundle(bundleKey)).isFalse();
    }

    @Test
    public void testDefaultInitialized() {
        assertThat(this.connector.getInitialized()).isFalse();
    }

    @Test
    public void testNoKeyOverlap() {
        byte[] key = HashUtil.h256("key".getBytes());
        this.connector.setActiveMember(key, true);
        this.connector.setBundle(key, false);

        assertThat(this.connector.getActiveMember(key)).isTrue();
        assertThat(this.connector.getBundle(key)).isFalse();

        // test that the reverse is true
        this.connector.setActiveMember(key, false);
        this.connector.setBundle(key, true);

        assertThat(this.connector.getActiveMember(key)).isFalse();
        assertThat(this.connector.getBundle(key)).isTrue();
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
