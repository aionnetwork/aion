package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import org.aion.mcf.db.InternalVmType;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.rlp.SharedRLPList;
import org.aion.types.AionAddress;
import org.aion.zero.impl.db.DetailsDataStore.RLPContractDetails;
import org.aion.zero.impl.trie.SecureTrie;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

/**
 * Unit tests for {@link DetailsDataStore}.
 *
 * @author Alexandra Roatis
 */
public class DetailsDataStoreTest {

    @Test(expected = IllegalStateException.class)
    public void testDecodingWithIncorrectSize() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        byte[] code = RandomUtils.nextBytes(512);

        // create old encoding
        byte[] rlpAddress = RLP.encodeElement(address.toByteArray());
        byte[] rlpIsExternalStorage = RLP.encodeByte((byte) 1);
        byte[] rlpStorageRoot = RLP.encodeElement(RandomUtils.nextBytes(32));
        byte[] rlpStorageTrie = RLP.encodeElement(EMPTY_BYTE_ARRAY);
        byte[] rlpCode = RLP.encodeList(RLP.encodeElement(code));

        byte[] oldEncoding =
                RLP.encodeList(
                        rlpAddress,
                        rlpIsExternalStorage,
                        rlpStorageRoot,
                        rlpStorageTrie,
                        rlpCode,
                        RLP.encodeByte(InternalVmType.AVM.getCode()));

        // create object using encoding
        // throws exception due to the illegal size of the encoding above
        DetailsDataStore.fromEncoding(oldEncoding);
    }

    @Test
    public void testDecodingWithSizeFiveExternal() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        byte[] code = RandomUtils.nextBytes(512);

        // create encoding
        byte[] rlpAddress = RLP.encodeElement(address.toByteArray());
        byte[] rlpIsExternalStorage = RLP.encodeByte((byte) 1);
        byte[] root = RandomUtils.nextBytes(32);
        byte[] rlpStorageRoot = RLP.encodeElement(root);
        byte[] rlpStorageTrie = RLP.encodeElement(EMPTY_BYTE_ARRAY);
        byte[] rlpCode = RLP.encodeElement(code);

        byte[] encoding = RLP.encodeList(rlpAddress, rlpIsExternalStorage, rlpStorageRoot, rlpStorageTrie, rlpCode);

        // decode
        RLPContractDetails details = DetailsDataStore.fromEncoding(encoding);

        assertThat(details.address).isEqualTo(address);
        assertThat(details.isExternalStorage).isTrue();
        assertThat(details.storageRoot.getRLPData()).isEqualTo(root);
        assertThat(details.storageTrie.getRLPData()).isEqualTo(EMPTY_BYTE_ARRAY);
        assertThat(details.code.getRLPData()).isEqualTo(code);
    }

    @Test
    public void testDecodingWithSizeFiveInternal() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        byte[] code = RandomUtils.nextBytes(512);

        // create encoding
        byte[] rlpAddress = RLP.encodeElement(address.toByteArray());
        byte[] rlpIsExternalStorage = RLP.encodeByte((byte) 0);
        byte[] rlpStorageRoot = RLP.encodeElement(EMPTY_BYTE_ARRAY);
        SecureTrie trie = new SecureTrie(null);
        trie.update(RandomUtils.nextBytes(32), RandomUtils.nextBytes(32));
        byte[] rlpStorageTrie = RLP.encodeElement(trie.serialize());
        byte[] rlpCode = RLP.encodeElement(code);

        byte[] encoding = RLP.encodeList(rlpAddress, rlpIsExternalStorage, rlpStorageRoot, rlpStorageTrie, rlpCode);

        // decode
        RLPContractDetails details = DetailsDataStore.fromEncoding(encoding);

        assertThat(details.address).isEqualTo(address);
        assertThat(details.isExternalStorage).isFalse();
        assertThat(details.storageRoot.getRLPData()).isEqualTo(EMPTY_BYTE_ARRAY);
        assertThat(details.storageTrie.getRLPData()).isEqualTo(trie.serialize());
        assertThat(details.code.getRLPData()).isEqualTo(code);
    }

    @Test
    public void testDecodingWithSizeThree() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        byte[] code1 = RandomUtils.nextBytes(512);
        byte[] code2 = RandomUtils.nextBytes(512);

        // create encoding
        byte[] rlpAddress = RLP.encodeElement(address.toByteArray());
        byte[] root = RandomUtils.nextBytes(32);
        byte[] rlpStorageRoot = RLP.encodeElement(root);
        byte[] rlpCode = RLP.encodeList(RLP.encodeElement(code1), RLP.encodeElement(code2));

        byte[] encoding = RLP.encodeList(rlpAddress, rlpStorageRoot, rlpCode);

        // decode
        RLPContractDetails details = DetailsDataStore.fromEncoding(encoding);

        assertThat(details.address).isEqualTo(address);
        assertThat(details.isExternalStorage).isTrue();
        assertThat(details.storageRoot.getRLPData()).isEqualTo(root);
        assertThat(details.storageTrie).isNull();
        assertThat(((SharedRLPList) details.code).get(0).getRLPData()).isEqualTo(code1);
        assertThat(((SharedRLPList) details.code).get(1).getRLPData()).isEqualTo(code2);
    }
}
