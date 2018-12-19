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
 */

package org.aion.zero.impl.blockchain;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import java.util.Collections;
import org.aion.base.type.AionAddress;
import org.aion.crypto.HashUtil;
import org.aion.mcf.vm.types.Bloom;
import org.aion.vm.api.interfaces.Address;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.junit.Test;

/** Test cases for AionTxExecSummary */
public class AionTxExecSummaryTest {

    private Address defaultAddress =
            AionAddress.wrap("CAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFE");

    @Test
    public void testRLPEncoding() {
        AionTransaction mockTx =
                new AionTransaction(
                        BigInteger.ONE.toByteArray(),
                        defaultAddress,
                        defaultAddress,
                        BigInteger.ONE.toByteArray(),
                        HashUtil.EMPTY_DATA_HASH,
                        1L,
                        1L);

        AionTxReceipt txReceipt =
                new AionTxReceipt(HashUtil.EMPTY_TRIE_HASH, new Bloom(), Collections.EMPTY_LIST);
        txReceipt.setNrgUsed(1);
        txReceipt.setTransaction(mockTx);

        AionTxExecSummary.Builder builder = AionTxExecSummary.builderFor(txReceipt);
        builder.markAsFailed().result(new byte[0]);
        AionTxExecSummary summary = builder.build();
        byte[] encodedSummary = summary.getEncoded();

        AionTxExecSummary newSummary = new AionTxExecSummary(encodedSummary);

        newSummary.getReceipt().setTransaction(mockTx);

        assertThat(newSummary.getFee()).isEqualTo(BigInteger.ONE);
    }
}
