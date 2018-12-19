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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.aion.base.type.AionAddress;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.vm.types.DataWord;
import org.aion.vm.api.interfaces.Address;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

public class AionTransactionTest {

    private void assertTransactionEquals(AionTransaction tx, AionTransaction tx2) {
        assertArrayEquals(tx.getTransactionHash(), tx2.getTransactionHash());
        assertArrayEquals(tx.getNonce(), tx2.getNonce());
        assertArrayEquals(tx.getValue(), tx2.getValue());
        assertArrayEquals(tx.getData(), tx2.getData());
        assertEquals(tx.getEnergyLimit(), tx2.getEnergyLimit());
        assertEquals(tx.getEnergyPrice(), tx2.getEnergyPrice());
        assertEquals(tx.getTransactionType(), tx2.getTransactionType());

        assertArrayEquals(tx.getTimeStamp(), tx2.getTimeStamp());
        assertArrayEquals(tx.getSignature().toBytes(), tx2.getSignature().toBytes());

        assertArrayEquals(tx.getEncoded(), tx2.getEncoded());
    }

    @Test
    public void testSerializationZero() {
        byte[] nonce = RandomUtils.nextBytes(16);
        Address to = AionAddress.wrap(RandomUtils.nextBytes(32));
        byte[] value = RandomUtils.nextBytes(16);
        byte[] data = RandomUtils.nextBytes(64);
        long nrg = 0;
        long nrgPrice = 0;
        byte type = 0;

        AionTransaction tx = new AionTransaction(nonce, to, value, data, nrg, nrgPrice, type);
        tx.sign(ECKeyFac.inst().create());

        AionTransaction tx2 = new AionTransaction(tx.getEncoded());

        assertTransactionEquals(tx, tx2);
    }

    @Test
    public void testClone() {
        byte[] nonce = RandomUtils.nextBytes(16);
        Address to = AionAddress.wrap(RandomUtils.nextBytes(32));
        byte[] value = RandomUtils.nextBytes(16);
        byte[] data = RandomUtils.nextBytes(64);
        long nrg = RandomUtils.nextLong(0, Long.MAX_VALUE);
        long nrgPrice = RandomUtils.nextLong(0, Long.MAX_VALUE);
        byte type = 1;

        AionTransaction tx = new AionTransaction(nonce, to, value, data, nrg, nrgPrice, type);
        tx.sign(ECKeyFac.inst().create());

        AionTransaction tx2 = tx.clone();

        assertTransactionEquals(tx, tx2);
    }

    @Test
    public void testTransactionCost() {
        byte[] nonce = DataWord.ONE.getData();
        byte[] from = RandomUtils.nextBytes(20);
        byte[] to = RandomUtils.nextBytes(Address.SIZE);
        byte[] value = DataWord.ONE.getData();
        byte[] data = RandomUtils.nextBytes(128);
        long nrg = new DataWord(1000L).longValue();
        long nrgPrice = DataWord.ONE.longValue();

        AionTransaction tx =
                new AionTransaction(nonce, AionAddress.wrap(to), value, data, nrg, nrgPrice);

        long expected = 21000;
        for (byte b : data) {
            expected += (b == 0) ? 4 : 64;
        }
        assertEquals(expected, tx.transactionCost(1));
    }

    @Test
    public void testTransactionCost2() {
        byte[] nonce = DataWord.ONE.getData();
        byte[] from = RandomUtils.nextBytes(Address.SIZE);
        Address to = AionAddress.EMPTY_ADDRESS();
        byte[] value = DataWord.ONE.getData();
        byte[] data = RandomUtils.nextBytes(128);
        long nrg = new DataWord(1000L).longValue();
        long nrgPrice = DataWord.ONE.longValue();

        AionTransaction tx = new AionTransaction(nonce, to, value, data, nrg, nrgPrice);

        long expected = 200000 + 21000;
        for (byte b : data) {
            expected += (b == 0) ? 4 : 64;
        }
        assertEquals(expected, tx.transactionCost(1));
    }
}
