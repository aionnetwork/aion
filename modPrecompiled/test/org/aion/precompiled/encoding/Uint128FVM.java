package org.aion.precompiled.encoding;

import javax.annotation.Nonnull;

public class Uint128FVM extends BaseTypeFVM {

    private final byte[] payload;

    public Uint128FVM(@Nonnull final byte[] word) {
        assert word.length == 16;
        this.payload = word;
    }

    @Override
    public byte[] serialize() {
        return this.payload;
    }

    @Override
    public boolean isDynamic() {
        return false;
    }
}
