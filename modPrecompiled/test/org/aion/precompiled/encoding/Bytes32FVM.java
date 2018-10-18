package org.aion.precompiled.encoding;

import org.aion.base.util.ByteArrayWrapper;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

public class Bytes32FVM extends BaseTypeFVM {
    private ByteArrayWrapper word;

    public Bytes32FVM(@Nonnull final ByteArrayWrapper word) {
        assert word.getData().length == 32;
        this.word = word;
    }

    @Override
    public byte[] serialize() {
        return this.word.getData();
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
