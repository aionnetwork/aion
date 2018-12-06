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

package org.aion.zero.impl.core;

import org.aion.base.type.AionAddress;
import org.aion.crypto.HashUtil;
import org.aion.mcf.vm.types.Bloom;

public class BloomFilter {
    public static boolean containsAddress(Bloom bloom, AionAddress address) {
        Bloom targetBloom = Bloom.create(HashUtil.h256(address.toBytes()));
        return bloom.contains(targetBloom);
    }

    public static boolean containsEvent(Bloom bloom, byte[] eventHash) {
        Bloom targetBloom = Bloom.create(HashUtil.h256(eventHash));
        return bloom.contains(targetBloom);
    }

    // more for testing, not used in production
    public static boolean containsString(Bloom bloom, String s) {
        Bloom targetBloom = Bloom.create(HashUtil.h256(s.getBytes()));
        return bloom.contains(targetBloom);
    }

    public static boolean contains(Bloom bloom, Bloom targetBloom) {
        return bloom.contains(targetBloom);
    }

    public static Bloom create(byte[]... input) {
        Bloom targetBloom = new Bloom();

        for (int i = 0; i < input.length; i++) {
            targetBloom.or(Bloom.create(HashUtil.h256(input[i])));
        }
        return targetBloom;
    }
}
