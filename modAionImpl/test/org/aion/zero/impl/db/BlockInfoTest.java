/*******************************************************************************
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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/
package org.aion.zero.impl.db;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.crypto.HashUtil;
import org.aion.zero.impl.db.AionBlockStore;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Test suite for {@link org.aion.zero.impl.db.AionBlockStore.BlockInfo} serialization
 */
public class BlockInfoTest {

    private byte[] DEFAULT_HASH = HashUtil.h256("hello world".getBytes());

    @Test
    public void testBlockInfoSerialization() {
        AionBlockStore.BlockInfo info = new AionBlockStore.BlockInfo();
        info.setMainChain(true);
        info.setCummDifficulty(BigInteger.ONE);
        info.setHash(DEFAULT_HASH);

        byte[] serialized = AionBlockStore.BLOCK_INFO_SERIALIZER.serialize(Collections.singletonList(info));
        System.out.println(new ByteArrayWrapper(serialized));

        List<AionBlockStore.BlockInfo> deserializedBlockInfos = AionBlockStore.BLOCK_INFO_SERIALIZER.deserialize(serialized);
        assertThat(deserializedBlockInfos.size()).isEqualTo(1);

        AionBlockStore.BlockInfo deserializedInfo = deserializedBlockInfos.get(0);

        assertThat(deserializedInfo.cummDifficulty).isEqualTo(info.cummDifficulty);
        assertThat(deserializedInfo.hash).isEqualTo(info.hash);
        assertThat(deserializedInfo.mainChain).isEqualTo(info.mainChain);
    }
}
