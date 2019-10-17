package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.aion.crypto.HashUtil;
import org.aion.util.types.ByteArrayWrapper;
import org.junit.Test;

/** Test suite for {@link org.aion.zero.impl.db.AionBlockStore.BlockInfo} serialization */
public class BlockInfoTest {

    private byte[] DEFAULT_HASH = HashUtil.h256("hello world".getBytes());

    @Test
    public void testBlockInfoSerialization() {
        AionBlockStore.BlockInfo info =
                new AionBlockStore.BlockInfo(DEFAULT_HASH, BigInteger.ONE, true);

        byte[] serialized =
                AionBlockStore.BLOCK_INFO_SERIALIZER.serialize(Collections.singletonList(info));
        System.out.println("serialized: " + ByteArrayWrapper.wrap(serialized));

        List<AionBlockStore.BlockInfo> deserializedBlockInfos =
                AionBlockStore.BLOCK_INFO_SERIALIZER.deserialize(serialized);
        assertThat(deserializedBlockInfos.size()).isEqualTo(1);

        AionBlockStore.BlockInfo deserializedInfo = deserializedBlockInfos.get(0);

        assertThat(deserializedInfo.getTotalDifficulty()).isEqualTo(info.getTotalDifficulty());
        assertThat(deserializedInfo.getHash()).isEqualTo(info.getHash());
        assertThat(deserializedInfo.isMainChain()).isEqualTo(info.isMainChain());
    }

    @Test
    public void testBlockInfoMultipleSerialization() {
        AionBlockStore.BlockInfo info =
                new AionBlockStore.BlockInfo(DEFAULT_HASH, BigInteger.ONE, true);
        AionBlockStore.BlockInfo info2 =
                new AionBlockStore.BlockInfo(HashUtil.h256(DEFAULT_HASH), BigInteger.TWO, false);

        byte[] serialized =
                AionBlockStore.BLOCK_INFO_SERIALIZER.serialize(Arrays.asList(info, info2));
        System.out.println("serialized: " + ByteArrayWrapper.wrap(serialized));

        // deserialized
        List<AionBlockStore.BlockInfo> deserializedBlockInfos =
                AionBlockStore.BLOCK_INFO_SERIALIZER.deserialize(serialized);

        AionBlockStore.BlockInfo dInfo1 = deserializedBlockInfos.get(0);

        assertThat(dInfo1.getTotalDifficulty()).isEqualTo(info.getTotalDifficulty());
        assertThat(dInfo1.getHash()).isEqualTo(info.getHash());
        assertThat(dInfo1.isMainChain()).isEqualTo(info.isMainChain());

        AionBlockStore.BlockInfo dInfo2 = deserializedBlockInfos.get(1);

        assertThat(dInfo2.getTotalDifficulty()).isEqualTo(info2.getTotalDifficulty());
        assertThat(dInfo2.getHash()).isEqualTo(info2.getHash());
        assertThat(dInfo2.isMainChain()).isEqualTo(info2.isMainChain());
    }
}
