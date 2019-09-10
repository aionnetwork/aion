package org.aion.precompiled.encoding;

import javax.annotation.Nonnull;

public class AddressFVM extends BaseTypeFVM {

    private final byte[] address;

    public AddressFVM(@Nonnull final byte[] address) {
        assert address.length == 32;
        this.address = address;
    }

    @Override
    public byte[] serialize() {
        return this.address;
    }

    @Override
    public boolean isDynamic() {
        return false;
    }
}
