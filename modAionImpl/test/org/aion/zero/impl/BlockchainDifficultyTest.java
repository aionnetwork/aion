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
package org.aion.zero.impl;

import org.aion.mcf.core.ImportResult;
import org.aion.zero.impl.types.AionBlock;
import org.junit.Test;

import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;

public class BlockchainDifficultyTest {
    @Test
    public void testDifficultyFirstBlock() {
        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
                .withValidatorConfiguration("simple")
                .withDefaultAccounts()
                .build();

        AionBlock firstBlock = bundle.bc.createNewBlock(bundle.bc.getGenesis(),
                Collections.emptyList(),
                true);
        assertThat(firstBlock.getDifficultyBI()).isEqualTo(bundle.bc.getGenesis().getDifficultyBI());
        assertThat(bundle.bc.tryToConnect(firstBlock)).isEqualTo(ImportResult.IMPORTED_BEST);
    }

    // for all other blocks, we should not have a corner case
    @Test
    public void testDifficultyNotFirstBlock() {
        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
                .withValidatorConfiguration("simple")
                .withDefaultAccounts()
                .build();

        AionBlock firstBlock = bundle.bc.createNewBlock(bundle.bc.getGenesis(),
                Collections.emptyList(),
                true);

        assertThat(bundle.bc.tryToConnect(firstBlock)).isEqualTo(ImportResult.IMPORTED_BEST);

        // connect second block
        AionBlock secondBlock = bundle.bc.createNewBlock(
                firstBlock,
                Collections.emptyList(),
                true);

        assertThat(bundle.bc.tryToConnect(secondBlock)).isEqualTo(ImportResult.IMPORTED_BEST);

        // due to us timestamping the genesis at 0
        assertThat(secondBlock.getDifficultyBI()).isLessThan(firstBlock.getDifficultyBI());
    }

    @Test
    public void testDifficultyThirdBlock() {
        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
                .withValidatorConfiguration("simple")
                .withDefaultAccounts()
                .build();

        AionBlock firstBlock = bundle.bc.createNewBlock(bundle.bc.getGenesis(),
                Collections.emptyList(),
                true);

        assertThat(bundle.bc.tryToConnect(firstBlock)).isEqualTo(ImportResult.IMPORTED_BEST);

        // connect second block
        AionBlock secondBlock = bundle.bc.createNewBlock(
                firstBlock,
                Collections.emptyList(),
                true);

        assertThat(bundle.bc.tryToConnect(secondBlock)).isEqualTo(ImportResult.IMPORTED_BEST);

        // due to us timestamping the genesis at 0
        assertThat(secondBlock.getDifficultyBI()).isLessThan(firstBlock.getDifficultyBI());

        // connect second block
        AionBlock thirdBlock = bundle.bc.createNewBlock(
                secondBlock,
                Collections.emptyList(),
                true);

        assertThat(bundle.bc.tryToConnect(thirdBlock)).isEqualTo(ImportResult.IMPORTED_BEST);

        // due to us timestamping the genesis at 0
        assertThat(thirdBlock.getDifficultyBI()).isGreaterThan(secondBlock.getDifficultyBI());
    }
}
