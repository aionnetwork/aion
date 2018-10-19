/**
 * ***************************************************************************** Copyright (c)
 * 2017-2018 Aion foundation.
 *
 * <p>This file is part of the aion network project.
 *
 * <p>The aion network project is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * <p>The aion network project is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with the aion network
 * project source files. If not, see <https://www.gnu.org/licenses/>.
 *
 * <p>The aion network project leverages useful source code from other open source projects. We
 * greatly appreciate the effort that was invested in these projects and we thank the individual
 * contributors for their work. For provenance information and contributors please see
 * <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * <p>Contributors to the aion source files in decreasing order of code volume: Aion foundation.
 * <ether.camp> team through the ethereumJ library. Ether.Camp Inc. (US) team through Ethereum
 * Harmony. John Tromp through the Equihash solver. Samuel Neves through the BLAKE2 implementation.
 * Zcash project team. Bitcoinj team.
 * ****************************************************************************
 */
package org.aion.zero.impl.sync.msg;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertArrayEquals;

import java.util.ArrayList;
import java.util.List;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Test;

public class ResBlocksHeadersTest {

    private static A0BlockHeader bh1;

    static {
        // replace with well-formed header
        A0BlockHeader.Builder builder = new A0BlockHeader.Builder();
        bh1 = builder.build();
    }

    @Test
    public void testHeader() {

        byte[] dd = bh1.getEncoded();
        A0BlockHeader bh2 = null;
        try {
            bh2 = A0BlockHeader.fromRLP(dd, false);
        } catch (Exception e) {
            System.out.println(e.toString());
            e.printStackTrace();
            return;
        }
        assertArrayEquals(bh1.getParentHash(), bh2.getParentHash());
        assertArrayEquals(bh1.getParentHash(), bh2.getParentHash());
        assertTrue(bh1.getCoinbase().equals(bh2.getCoinbase()));

        byte[] log1 = bh1.getLogsBloom();
        byte[] log2 = bh2.getLogsBloom();
        assertThat(log2).isEqualTo(log1);

        byte[] dif1 = bh1.getDifficulty();
        byte[] dif2 = bh2.getDifficulty();
        assertThat(dif2).isEqualTo(dif1);

        assertThat(bh2.getNumber()).isEqualTo(bh1.getNumber());
        assertThat(bh2.getTimestamp()).isEqualTo(bh1.getTimestamp());

        byte[] data1 = bh1.getExtraData();
        byte[] data2 = bh2.getExtraData();
        assertThat(data2).isEqualTo(data1);

        byte[] nonc1 = bh1.getNonce();
        byte[] nonc2 = bh2.getNonce();
        assertThat(nonc2).isEqualTo(nonc1);

        byte[] sol1 = bh1.getSolution();
        byte[] sol2 = bh2.getSolution();
        assertThat(sol2).isEqualTo(sol1);

        assertThat(bh2.getEnergyConsumed()).isEqualTo(bh1.getEnergyConsumed());
        assertThat(bh2.getEnergyLimit()).isEqualTo(bh1.getEnergyLimit());
    }

    @Test
    public void testHeaders() {
        int m = 192;

        List<A0BlockHeader> bhs1 = new ArrayList<A0BlockHeader>();
        for (int i = 0; i < m; i++) {
            bhs1.add(bh1);
        }
        ResBlocksHeaders rbhs1 = new ResBlocksHeaders(bhs1);
        byte[] rbhsBytes = rbhs1.encode();
        ResBlocksHeaders rbhs2 = ResBlocksHeaders.decode(rbhsBytes);
        List<A0BlockHeader> bhs2 = rbhs2.getHeaders();
        assertThat(bhs2.size()).isEqualTo(m);
    }
}
