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

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.core.ImportResult;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class BlockchainEnergyTest {

    @Test
    public void testConsistentEnergyUsage() {
        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
                .withValidatorConfiguration("simple")
                .withDefaultAccounts()
                .build();
        StandaloneBlockchain bc = bundle.bc;

        // in cases where no transactions are included (no energy usage)
        // the default energy limit should persist (it should not degrade)
        AionBlock block = bc.createNewBlock(bc.getBestBlock(), Collections.EMPTY_LIST, true);
        assertThat(block.getNrgLimit()).isEqualTo(bc.getGenesis().getNrgLimit());
    }

    @Test
    public void testEnergyUsageRecorded() {
        final int DEFAULT_TX_AMOUNT = 21000;
        final Address RECEIPT_ADDR = Address.wrap("CAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFECAFE");

        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
            .withDefaultAccounts().withValidatorConfiguration("simple").build();
        StandaloneBlockchain bc = bundle.bc;

        // TODO: where is the 21000 defined? bad to define magic variables
        int amount = (int) (bc.getGenesis().getNrgLimit() / DEFAULT_TX_AMOUNT);


        //(byte[] nonce, byte[] from, byte[] to, byte[] value, byte[] data, byte[] nrg, byte[] nrgPrice)
        List<AionTransaction> txs = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            // this transaction should send one (1) AION coin from acc[0] to RECEIPT_ADDR
            AionTransaction atx = new AionTransaction(
                    ByteUtil.intToBytes(i),
                    RECEIPT_ADDR,
                    BigInteger.ONE.toByteArray(),
                    ByteUtil.EMPTY_BYTE_ARRAY,
                    21000L,
                    BigInteger.valueOf(5).multiply(BigInteger.TEN.pow(9)).longValue()
            );
            atx.sign(bundle.privateKeys.get(0));
            txs.add(atx);

        }
        AionBlock block = bc.createNewBlock(bc.getBestBlock(), txs, true);
        ImportResult result = bc.tryToConnect(block);

        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

        // proceed with connecting the next block, should observe an increase in energyLimit
        AionBlock secondBlock = bc.createNewBlock(bc.getBestBlock(), Collections.EMPTY_LIST, true);
        assertThat(secondBlock.getNrgLimit()).isEqualTo(block.getNrgLimit());
        System.out.println(String.format("%d > %d", secondBlock.getNrgLimit(), block.getNrgLimit()));
    }
}
