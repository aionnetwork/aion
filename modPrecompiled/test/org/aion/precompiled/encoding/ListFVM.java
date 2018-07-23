package org.aion.precompiled.encoding;

import org.aion.precompiled.PrecompiledUtilities;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ListFVM extends BaseTypeFVM {

    List<BaseTypeFVM> params;

    public ListFVM() {
        this.params = new ArrayList<>();
    };

    public ListFVM(@Nonnull final BaseTypeFVM ...params) {
        this.params = new ArrayList<>(Arrays.asList(params));
    }

    public void add(BaseTypeFVM param) {
        this.params.add(param);
    }

    @Override
    public byte[] serialize() {
        ByteBuffer bb = ByteBuffer.allocate(params.size() * params.get(0).serialize().length + 16);
        int elementLength = params.size();
        bb.put(PrecompiledUtilities.pad(BigInteger.valueOf(elementLength).toByteArray(), 16));

        for (BaseTypeFVM p : params) {
            bb.put(p.serialize());
        }
        return bb.array();
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public Optional<List<BaseTypeFVM>> getEntries() {
        return Optional.of(this.params);
    }
}
