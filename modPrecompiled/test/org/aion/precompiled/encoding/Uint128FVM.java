package org.aion.precompiled.encoding;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.aion.types.ByteArrayWrapper;

public class Uint128FVM extends BaseTypeFVM {

    private final byte[] payload;

    public Uint128FVM(@Nonnull final ByteArrayWrapper word) {
        assert word.getData().length == 16;
        this.payload = word.getData();
    }

    @Override
    public byte[] serialize() {
        return this.payload;
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
