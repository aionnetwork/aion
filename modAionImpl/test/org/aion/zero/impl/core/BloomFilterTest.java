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

package org.aion.zero.impl.core;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigInteger;
import org.aion.base.type.AionAddress;
import org.aion.crypto.HashUtil;
import org.aion.mcf.vm.types.Bloom;
import org.aion.vm.api.interfaces.Address;
import org.junit.Test;

/**
 * Very basic bloom filter tests, for integration tests, eventually look for them in {@link
 * org.aion.zero.impl.BlockchainIntegrationTest}
 *
 * <p>TODO: implement integration tests
 */
public class BloomFilterTest {
    @Test
    public void testSimpleAddSearchBloom() {
        String input = "hello world";
        Bloom bloom = BloomFilter.create(input.getBytes());
        assertThat(BloomFilter.containsString(bloom, input)).isTrue();
    }

    @Test
    public void testContainsAddress() {
        Address addr =
                new AionAddress("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
        Bloom bloom = BloomFilter.create(addr.toBytes());
        assertThat(BloomFilter.containsAddress(bloom, addr)).isTrue();
    }

    @Test
    public void testContainsEvent() {
        byte[] someEvent = HashUtil.h256(BigInteger.TEN.toByteArray());
        Bloom bloom = BloomFilter.create(someEvent);
        assertThat(BloomFilter.containsEvent(bloom, someEvent)).isTrue();
    }

    @Test
    public void testContainsEvent2() {
        String evt = "Created(uint128,address)";
        byte[] someEvent = HashUtil.h256(evt.getBytes());
        Bloom bloom = BloomFilter.create(someEvent);
        assertThat(BloomFilter.containsEvent(bloom, someEvent)).isTrue();
    }

    @Test
    public void testCompositeBloomFiltering() {
        Address addr =
                new AionAddress("BEEBEEBEEBEEBEEBEEBEEBEEBEEBEEBEEBEEBEEBEEBEEBEEBEEBEEBEEBEEFFFF");
        byte[] someEvent = HashUtil.h256(BigInteger.ONE.toByteArray());
        byte[] anotherEvent = HashUtil.h256(BigInteger.TWO.toByteArray());

        Bloom bloom = BloomFilter.create(addr.toBytes(), someEvent, anotherEvent);
        assertThat(BloomFilter.containsAddress(bloom, addr)).isTrue();
        assertThat(BloomFilter.containsEvent(bloom, someEvent)).isTrue();

        // test filtering composite
        Bloom compositeTargetBloom = BloomFilter.create(someEvent, anotherEvent);
        assertThat(BloomFilter.contains(bloom, compositeTargetBloom)).isTrue();
    }
}
