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
import static org.aion.zero.impl.BlockchainTestUtils.generateAccounts;
import static org.aion.zero.impl.BlockchainTestUtils.generateNewBlock;
import static org.aion.zero.impl.BlockchainTestUtils.generateNextBlock;
import static org.aion.zero.impl.BlockchainTestUtils.generateRandomChain;
import static org.aion.zero.impl.sync.TaskImportBlocks.isAlreadyStored;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.aion.crypto.ECKey;
import org.aion.mcf.core.ImportResult;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.sync.PeerState.Mode;
import org.aion.zero.impl.types.AionBlock;
import org.junit.Test;
import org.junit.runner.RunWith;

/** @author Alexandra Roatis */
@RunWith(JUnitParamsRunner.class)
public class TaskImportBlocksTest {

    /** @return parameters for {@link #testCountStates(long, long, Mode, Collection)} */
    @SuppressWarnings("unused")
    private Object parametersForTestCountStates() {
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
            parameters.add(new Object[] {0L, -1L, mode, Collections.emptySet()});
            parameters.add(new Object[] {1L, 50L, mode, set1});
            parameters.add(new Object[] {0L, 100L, mode, set1});
            parameters.add(new Object[] {2L, 99L, mode, set2});
            parameters.add(new Object[] {1L, 100L, mode, set2});
            parameters.add(new Object[] {1L, 199L, mode, set2});
            parameters.add(new Object[] {0L, 200L, mode, set2});
            List<PeerState> set3 =
                    new ArrayList<>(set2)
                            .stream()
                            .filter(s -> s.getMode() != mode)
                            .collect(Collectors.toList());
            parameters.add(new Object[] {0L, -1L, mode, set3});
        }

        return parameters.toArray();
    }

    @Test
    @Parameters(method = "parametersForTestCountStates")
    public void testCountStates(long expected, long best, Mode mode, Collection<PeerState> set) {
        long actual = TaskImportBlocks.countStates(best, mode, set);
        assertThat(actual).isEqualTo(expected);
    }

    /** @return parameters for {@link #testSelectBase(long, long, SortedSet, SortedSet)} */
    @SuppressWarnings("unused")
    private Object parametersForTestSelectBase() {
        List<Object> parameters = new ArrayList<>();

        SortedSet<Long> emptySet = new TreeSet<>();
        parameters.add(new Object[] {100L, 100L, new TreeSet<Long>(), new TreeSet<Long>()});

        SortedSet<Long> set1 = new TreeSet<>();
        set1.add(200L);
        parameters.add(new Object[] {200L, 100L, set1, new TreeSet<Long>()});

        SortedSet<Long> set2 = new TreeSet<>();
        set2.add(10L);
        set2.add(50L);
        set2.add(100L);
        set2.add(200L);
        set2.add(300L);
        SortedSet<Long> expectedSet = new TreeSet<>();
        expectedSet.add(300L);
        parameters.add(new Object[] {200L, 100L, set2, expectedSet});

        SortedSet<Long> set3 = new TreeSet<>();
        set3.addAll(set2);
        parameters.add(new Object[] {300L, 300L, set3, new TreeSet<Long>()});

        SortedSet<Long> set4 = new TreeSet<>();
        set3.addAll(set2);
        parameters.add(new Object[] {310L, 310L, set4, new TreeSet<Long>()});

        return parameters.toArray();
    }

    @Test
    @Parameters(method = "parametersForTestSelectBase")
    public void testSelectBase(
            long expected, long best, SortedSet<Long> set, SortedSet<Long> expectedSet) {
        long actual = TaskImportBlocks.selectBase(best, set);
        assertThat(actual).isEqualTo(expected);
        assertThat(set).isEqualTo(expectedSet);
    }

    @Test
    public void testIsAlreadyStored() {
        List<ECKey> accounts = generateAccounts(10);

        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;

        // populate chain at random
        generateRandomChain(chain, 3, 1, accounts, 10);

        AionBlock current = chain.getBestBlock();
        while (current.getNumber() > 0) {
            // will pass both checks
            assertThat(isAlreadyStored(chain.getBlockStore(), current)).isTrue();
            current = chain.getBlockByHash(current.getParentHash());
        }

        // will fail the max number check
        current = generateNextBlock(chain, accounts, 10);
        assertThat(isAlreadyStored(chain.getBlockStore(), current)).isFalse();

        assertThat(chain.tryToConnect(current)).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(isAlreadyStored(chain.getBlockStore(), current)).isTrue();

        // will fail the existence check
        current = generateNewBlock(chain, chain.getGenesis(), accounts, 10);
        assertThat(isAlreadyStored(chain.getBlockStore(), current)).isFalse();

        assertThat(chain.tryToConnect(current)).isEqualTo(ImportResult.IMPORTED_NOT_BEST);
        assertThat(isAlreadyStored(chain.getBlockStore(), current)).isTrue();
    }
}
