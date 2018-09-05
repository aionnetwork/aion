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
package org.aion.zero.impl.sync;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.aion.zero.impl.sync.PeerState.Mode;
import org.junit.Test;
import org.junit.runner.RunWith;

/** @author Alexandra Roatis */
@RunWith(JUnitParamsRunner.class)
public class TaskImportBlocksTest {

    /** @return parameters for {@link #testGetStateCount(Collection, Mode, long, long)} */
    @SuppressWarnings("unused")
    private Object parametersForTestGetStateCount() {
        List<Object> parameters = new ArrayList<>();

        PeerState state;
        List<PeerState> set1 = new ArrayList<>();
        for (PeerState.Mode mode : PeerState.Mode.values()) {
            state = new PeerState(mode, 10L);
            state.setLastBestBlock(100L);
            set1.add(state);
        }

        List<PeerState> set2 = new ArrayList<>(set1);
        for (PeerState.Mode mode : PeerState.Mode.values()) {
            state = new PeerState(mode, 10L);
            state.setLastBestBlock(200L);
            set2.add(state);
        }

        for (PeerState.Mode mode : PeerState.Mode.values()) {
            parameters.add(new Object[] {Collections.EMPTY_LIST, mode, -1L, 0L});
            parameters.add(new Object[] {set1, mode, 50L, 1L});
            parameters.add(new Object[] {set1, mode, 100L, 0L});
            parameters.add(new Object[] {set2, mode, 99L, 2L});
            parameters.add(new Object[] {set2, mode, 100L, 1L});
            parameters.add(new Object[] {set2, mode, 199L, 1L});
            parameters.add(new Object[] {set2, mode, 200L, 0L});
            List<PeerState> set3 =
                    new ArrayList<>(set2)
                            .stream()
                            .filter(s -> s.getMode() != mode)
                            .collect(Collectors.toList());
            parameters.add(new Object[] {set3, mode, -1L, 0L});
        }

        return parameters.toArray();
    }

    @Test
    @Parameters(method = "parametersForTestGetStateCount")
    public void testGetStateCount(
            Collection<PeerState> allStates, PeerState.Mode mode, long best, long expected) {
        long actual = TaskImportBlocks.getStateCount(allStates, mode, best);
        if (actual != expected) {
            System.out.println(allStates);
            System.out.println(mode);
            System.out.println(best);
        }
        assertThat(actual).isEqualTo(expected);
    }
}
