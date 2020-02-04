package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import org.aion.db.impl.ByteArrayKeyValueStore;
import org.aion.precompiled.ContractInfo;
import org.aion.types.AionAddress;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link AvmContractDetails}.
 *
 * @author Alexandra Roatis
 */
public class AvmContractDetailsTest {
    @Mock AionAddress mockAddress;
    @Mock ByteArrayKeyValueStore mockDatabase;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_withNullAddress() {
        new AvmContractDetails(null, mockDatabase, mockDatabase);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_withNullStorageDatabase() {
        new AvmContractDetails(mockAddress, null, mockDatabase);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_withNullGraphDatabase() {
        new AvmContractDetails(mockAddress, mockDatabase, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_withPrecompiledContractAddress() {
        new AvmContractDetails(ContractInfo.TOKEN_BRIDGE.contractAddress, mockDatabase, mockDatabase);
    }

    /** Ensures that the external expectations for a new instance are met. */
    @Test
    public void testStateAfterInstantiation() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        AvmContractDetails details = new AvmContractDetails(address, mockDatabase, mockDatabase);
        assertThat(details.getAddress()).isEqualTo(address);
        assertThat(details.isDirty()).isFalse();
        assertThat(details.isDeleted()).isFalse();
        assertThat(details.getObjectGraph()).isEqualTo(EMPTY_BYTE_ARRAY);
        assertThat(details.getCodes()).isEmpty();
    }
}
