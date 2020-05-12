package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.crypto.HashUtil.h256;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import org.aion.db.impl.ByteArrayKeyValueStore;
import org.aion.db.store.XorDataSource;
import org.aion.base.InternalVmType;
import org.aion.types.AionAddress;
import org.aion.util.types.ByteArrayWrapper;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

/**
 * Unit tests for {@link FvmContractDetails}.
 *
 * @author Alexandra Roatis
 */
public class InnerContractDetailsTest {

    @Test(expected = NullPointerException.class)
    public void testCommitToInner_withNullParent() {
        new InnerContractDetails(null).commitTo((InnerContractDetails) null);
    }

    @Test
    public void testCommitToInner_withVmType() {
        InnerContractDetails parent = new InnerContractDetails(null);

        InnerContractDetails child = new InnerContractDetails(null);
        child.setVmType(InternalVmType.FVM);
        assertThat(child.isDirty()).isFalse();

        assertThat(parent.getVmType()).isEqualTo(InternalVmType.EITHER);

        child.commitTo(parent);

        assertThat(parent.getVmType()).isEqualTo(InternalVmType.FVM);
        assertThat(parent.isDirty()).isFalse();
    }

    @Test
    public void testCommitToInner_withObjectGraph() {
        InnerContractDetails parent = new InnerContractDetails(null);

        byte[] graph = RandomUtils.nextBytes(100);
        InnerContractDetails child = new InnerContractDetails(null);
        child.setObjectGraph(graph);
        assertThat(child.isDirty()).isTrue();

        assertThat(parent.getVmType()).isEqualTo(InternalVmType.EITHER);

        child.commitTo(parent);

        assertThat(parent.getVmType()).isEqualTo(InternalVmType.AVM);
        assertThat(parent.getObjectGraph()).isEqualTo(graph);
        assertThat(parent.isDirty()).isTrue();
    }

    @Test
    public void testCommitToInner_withStorage() {
        InnerContractDetails parent = new InnerContractDetails(null);

        InnerContractDetails child = new InnerContractDetails(null);
        Map<ByteArrayWrapper, ByteArrayWrapper> storage = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            ByteArrayWrapper key = ByteArrayWrapper.wrap(RandomUtils.nextBytes(32));
            ByteArrayWrapper value = ByteArrayWrapper.wrap(RandomUtils.nextBytes(100));
            child.put(key, value);
            storage.put(key, value);
        }
        ByteArrayWrapper deletedKey = ByteArrayWrapper.wrap(RandomUtils.nextBytes(32));
        child.delete(deletedKey);
        storage.put(deletedKey, null);

        assertThat(child.isDirty()).isTrue();
        assertThat(parent.getVmType()).isEqualTo(InternalVmType.EITHER);

        child.commitTo(parent);

        assertThat(parent.getVmType()).isEqualTo(InternalVmType.EITHER);
        for (ByteArrayWrapper key : storage.keySet()) {
            assertThat(parent.get(key)).isEqualTo(storage.get(key));
        }
        assertThat(parent.isDirty()).isTrue();
    }

    @Test
    public void testCommitToInner_withCode() {
        InnerContractDetails parent = new InnerContractDetails(null);

        byte[] code = RandomUtils.nextBytes(100);
        InnerContractDetails child = new InnerContractDetails(null);
        child.setCode(code);
        assertThat(child.isDirty()).isTrue();

        assertThat(parent.getVmType()).isEqualTo(InternalVmType.EITHER);

        child.commitTo(parent);

        assertThat(parent.getVmType()).isEqualTo(InternalVmType.EITHER);
        assertThat(parent.getCode(h256(code))).isEqualTo(code);
        assertThat(parent.isDirty()).isTrue();
    }

    @Test(expected = NullPointerException.class)
    public void testCommitToStored_withNullParent() {
        new InnerContractDetails(null).commitTo((StoredContractDetails) null);
    }

    @Test
    public void testCommitToStored_withObjectGraph() {
        AionAddress address = mock(AionAddress.class);
        ByteArrayKeyValueStore db = mock(XorDataSource.class);
        AvmContractDetails parent = new AvmContractDetails(address, db, db);

        byte[] graph = RandomUtils.nextBytes(100);
        InnerContractDetails child = new InnerContractDetails(null);
        child.setObjectGraph(graph);
        assertThat(child.isDirty()).isTrue();

        child.commitTo(parent);

        assertThat(parent.getVmType()).isEqualTo(InternalVmType.AVM);
        assertThat(parent.getObjectGraph()).isEqualTo(graph);
        assertThat(parent.isDirty()).isTrue();
    }

    @Test
    public void testCommitToStored_withStorageOnAvm() {
        AionAddress address = mock(AionAddress.class);
        ByteArrayKeyValueStore db = mock(XorDataSource.class);
        AvmContractDetails parent = new AvmContractDetails(address, db, db);

        InnerContractDetails child = new InnerContractDetails(null);
        Map<ByteArrayWrapper, ByteArrayWrapper> storage = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            ByteArrayWrapper key = ByteArrayWrapper.wrap(RandomUtils.nextBytes(32));
            ByteArrayWrapper value = ByteArrayWrapper.wrap(RandomUtils.nextBytes(100));
            child.put(key, value);
            storage.put(key, value);
        }
        ByteArrayWrapper deletedKey = ByteArrayWrapper.wrap(RandomUtils.nextBytes(32));
        child.delete(deletedKey);
        storage.put(deletedKey, null);

        assertThat(child.isDirty()).isTrue();
        assertThat(parent.getVmType()).isEqualTo(InternalVmType.AVM);

        child.commitTo(parent);

        assertThat(parent.getVmType()).isEqualTo(InternalVmType.AVM);
        for (ByteArrayWrapper key : storage.keySet()) {
            assertThat(parent.get(key)).isEqualTo(storage.get(key));
        }
        assertThat(parent.isDirty()).isTrue();
    }

    @Test
    public void testCommitToStored_withStorageOnFvm() {
        AionAddress address = mock(AionAddress.class);
        ByteArrayKeyValueStore db = mock(XorDataSource.class);
        FvmContractDetails parent = new FvmContractDetails(address, db);

        InnerContractDetails child = new InnerContractDetails(null);
        Map<ByteArrayWrapper, ByteArrayWrapper> storage = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            ByteArrayWrapper key = ByteArrayWrapper.wrap(RandomUtils.nextBytes(32));
            ByteArrayWrapper value = ByteArrayWrapper.wrap(RandomUtils.nextBytes(100));
            child.put(key, value);
            storage.put(key, value);
        }
        ByteArrayWrapper deletedKey = ByteArrayWrapper.wrap(RandomUtils.nextBytes(32));
        child.delete(deletedKey);
        storage.put(deletedKey, null);

        assertThat(child.isDirty()).isTrue();
        assertThat(parent.getVmType()).isEqualTo(InternalVmType.FVM);

        child.commitTo(parent);

        assertThat(parent.getVmType()).isEqualTo(InternalVmType.FVM);
        for (ByteArrayWrapper key : storage.keySet()) {
            assertThat(parent.get(key)).isEqualTo(storage.get(key));
        }
        assertThat(parent.isDirty()).isTrue();
    }

    @Test
    public void testCommitToStored_withCodeOnAvm() {
        AionAddress address = mock(AionAddress.class);
        ByteArrayKeyValueStore db = mock(XorDataSource.class);
        AvmContractDetails parent = new AvmContractDetails(address, db, db);

        byte[] code = RandomUtils.nextBytes(100);
        InnerContractDetails child = new InnerContractDetails(null);
        child.setCode(code);
        assertThat(child.isDirty()).isTrue();

        assertThat(parent.getVmType()).isEqualTo(InternalVmType.AVM);

        child.commitTo(parent);

        assertThat(parent.getVmType()).isEqualTo(InternalVmType.AVM);
        assertThat(parent.getCode(h256(code))).isEqualTo(code);
        assertThat(parent.isDirty()).isTrue();
    }

    @Test
    public void testCommitToStored_withCodeOnFvm() {
        AionAddress address = mock(AionAddress.class);
        ByteArrayKeyValueStore db = mock(XorDataSource.class);
        FvmContractDetails parent = new FvmContractDetails(address, db);

        byte[] code = RandomUtils.nextBytes(100);
        InnerContractDetails child = new InnerContractDetails(null);
        child.setCode(code);
        assertThat(child.isDirty()).isTrue();

        assertThat(parent.getVmType()).isEqualTo(InternalVmType.FVM);

        child.commitTo(parent);

        assertThat(parent.getVmType()).isEqualTo(InternalVmType.FVM);
        assertThat(parent.getCode(h256(code))).isEqualTo(code);
        assertThat(parent.isDirty()).isTrue();
    }
}
