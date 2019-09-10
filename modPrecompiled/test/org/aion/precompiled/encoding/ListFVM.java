package org.aion.precompiled.encoding;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.aion.precompiled.PrecompiledUtilities;

public class ListFVM extends BaseTypeFVM {

    List<BaseTypeFVM> params;

    public ListFVM() {
        this.params = new ArrayList<>();
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
}
