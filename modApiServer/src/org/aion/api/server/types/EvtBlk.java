package org.aion.api.server.types;

import static org.aion.api.server.types.Fltr.Type;

import org.aion.base.type.IBlock;
import org.aion.base.util.TypeConverter;

@SuppressWarnings("rawtypes")
public class EvtBlk extends Evt {

    public final IBlock b;

    public EvtBlk(IBlock b) {
        this.b = b;
    }

    @Override
    public Type getType() {
        return Type.BLOCK;
    }

    @Override
    public String toJSON() {
        return TypeConverter.toJsonHex(b.getHash());
    }
}
