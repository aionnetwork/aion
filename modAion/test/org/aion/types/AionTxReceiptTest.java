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

package org.aion.types;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.aion.crypto.HashUtil;
import org.aion.mcf.vm.types.Log;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.zero.types.AionTxReceipt;
import org.junit.Test;

public class AionTxReceiptTest {

    private byte[] EMPTY_BYTE_ARRAY = new byte[0];

    @Test
    public void testSerialization() {
        AionTxReceipt receipt = new AionTxReceipt();
        receipt.setError("");
        receipt.setExecutionResult(HashUtil.h256(EMPTY_BYTE_ARRAY));

        List<IExecutionLog> infos = new ArrayList<>();
        receipt.setLogs(infos);
        receipt.setPostTxState(HashUtil.h256(EMPTY_BYTE_ARRAY));

        byte[] encoded = receipt.getEncoded();
        AionTxReceipt resp = new AionTxReceipt(encoded);

        assertThat(resp.getTransactionOutput(), is(equalTo(receipt.getTransactionOutput())));
        assertThat(resp.getBloomFilter(), is(equalTo(receipt.getBloomFilter())));
        assertThat(resp.getError(), is(equalTo(receipt.getError())));
    }
}
