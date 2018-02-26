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
package org.aion.zero.impl.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.aion.zero.impl.sync.SequentialHeaders;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Test;

public class TestSequentialHeaders {

    @Test   
    public void testUniqueAndSequential() {
        List<A0BlockHeader> headers = new ArrayList<A0BlockHeader>();
        SequentialHeaders<A0BlockHeader> hs = new SequentialHeaders<A0BlockHeader>();
        
        /**
         * Missing block 5,6,9
         */
        headers.add(new A0BlockHeader.Builder().withNumber(0).build());
        headers.add(new A0BlockHeader.Builder().withNumber(1).build());
        headers.add(new A0BlockHeader.Builder().withNumber(2).build());
        headers.add(new A0BlockHeader.Builder().withNumber(10).build());
        headers.add(new A0BlockHeader.Builder().withNumber(3).build());
        headers.add(new A0BlockHeader.Builder().withNumber(7).build());
        headers.add(new A0BlockHeader.Builder().withNumber(4).build());
        headers.add(new A0BlockHeader.Builder().withNumber(8).build());
        headers.add(new A0BlockHeader.Builder().withNumber(2).build());
        headers.add(new A0BlockHeader.Builder().withNumber(1).build());
        headers.add(new A0BlockHeader.Builder().withNumber(0).build());
        hs.addAll(headers);
        assertThat(hs.size()).isEqualTo(5);
                
        headers.add(new A0BlockHeader.Builder().withNumber(5).build());
        headers.add(new A0BlockHeader.Builder().withNumber(6).build());
        headers.add(new A0BlockHeader.Builder().withNumber(9).build());
        hs.addAll(headers);
        assertThat(hs.size()).isEqualTo(11);
        
        for(int i = 0, m = hs.size(); i < m; i++ ) {
            assertEquals(i, hs.get(i).getNumber());
        }
    }

    @Test
    public void testAllLesserThan() {
        SequentialHeaders<A0BlockHeader> hs = new SequentialHeaders<A0BlockHeader>();

        // place in 3
        hs.addAll(Arrays.asList(new A0BlockHeader.Builder().withNumber(3).build()));

        assertThat(hs.size()).isEqualTo(1);

        // now try adding in 0, 1, 2
        hs.addAll(Arrays.asList(
                new A0BlockHeader.Builder().withNumber(0).build(),
                new A0BlockHeader.Builder().withNumber(1).build(),
                new A0BlockHeader.Builder().withNumber(2).build()
        ));
        assertThat(hs.size()).isEqualTo(1);
        assertThat(hs.get(0).getNumber()).isEqualTo(3);
    }

    @Test
    public void testAddEmpty() {
        SequentialHeaders<A0BlockHeader> hs = new SequentialHeaders<>();
        hs.addAll(Collections.emptyList());
        assertThat(hs.size()).isEqualTo(0);
    }

    @Test
    public void testSameElementMultiple() {
        SequentialHeaders<A0BlockHeader> hs = new SequentialHeaders<>();
        hs.addAll(Arrays.asList(new A0BlockHeader.Builder().withNumber(3).build()));

        assertThat(hs.size()).isEqualTo(1);

        hs.addAll(Arrays.asList(
                new A0BlockHeader.Builder().withNumber(3).build(),
                new A0BlockHeader.Builder().withNumber(3).build()
        ));
        assertThat(hs.size()).isEqualTo(1);
    }

    @Test
    public void testSameAsTipAndHigher() {
        SequentialHeaders<A0BlockHeader> hs = new SequentialHeaders<>();
        hs.addAll(Arrays.asList(
                new A0BlockHeader.Builder().withNumber(1).build(),
                new A0BlockHeader.Builder().withNumber(1).build(),
                new A0BlockHeader.Builder().withNumber(2).build()
        ));

        assertThat(hs.size()).isEqualTo(2);
        assertThat(hs.get(0).getNumber()).isEqualTo(1);
        assertThat(hs.get(1).getNumber()).isEqualTo(2);
    }

    @Test
    public void testSomeSmallerThanTip() {
        SequentialHeaders<A0BlockHeader> hs = new SequentialHeaders<>();
        hs.addAll(Arrays.asList(
                new A0BlockHeader.Builder().withNumber(1).build()
        ));

        hs.addAll(Arrays.asList(
                new A0BlockHeader.Builder().withNumber(0).build(),
                new A0BlockHeader.Builder().withNumber(1).build(),
                new A0BlockHeader.Builder().withNumber(2).build()
        ));

        assertThat(hs.size()).isEqualTo(2);
        assertThat(hs.get(0).getNumber()).isEqualTo(1);
        assertThat(hs.get(1).getNumber()).isEqualTo(2);
    }

    @Test
    public void testLowerAndSameAsTip() {
        SequentialHeaders<A0BlockHeader> hs = new SequentialHeaders<>();
        hs.addAll(Arrays.asList(
                new A0BlockHeader.Builder().withNumber(1).build()
        ));

        hs.addAll(Arrays.asList(
                new A0BlockHeader.Builder().withNumber(0).build(),
                new A0BlockHeader.Builder().withNumber(1).build(),
                new A0BlockHeader.Builder().withNumber(1).build()
        ));

        assertThat(hs.size()).isEqualTo(1);
        assertThat(hs.get(0).getNumber()).isEqualTo(1);
    }
}
