package org.aion.zero.impl.core;

import org.aion.crypto.HashUtil;
import org.aion.mcf.vm.types.Bloom;
import org.aion.vm.api.types.Address;

public class BloomFilter {
    public static boolean containsAddress(Bloom bloom, Address address) {
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
