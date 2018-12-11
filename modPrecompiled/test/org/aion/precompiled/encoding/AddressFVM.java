package org.aion.precompiled.encoding;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.aion.base.util.ByteArrayWrapper;

public class AddressFVM extends BaseTypeFVM {

    private final ByteArrayWrapper address;

    public AddressFVM(@Nonnull final ByteArrayWrapper address) {
        assert address.getData().length == 32;
        this.address = address;
    }

    @Override
    public byte[] serialize() {
        return this.address.getData();
    }

    @Override
    public boolean isDynamic() {
        return false;
    }

    @Override
    public Optional<List<BaseTypeFVM>> getEntries() {
        return Optional.empty();
    }
}
