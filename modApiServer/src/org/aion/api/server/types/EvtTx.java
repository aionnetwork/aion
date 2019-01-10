package org.aion.api.server.types;

import org.aion.api.server.types.Fltr.Type;
import org.aion.base.type.ITransaction;
import org.aion.base.util.TypeConverter;

public class EvtTx extends Evt {

    private final ITransaction tx;

    public EvtTx(ITransaction tx) {
        this.tx = tx;
    }

    @Override
    public Type getType() {
        return Type.TRANSACTION;
    }

    @Override
    public String toJSON() {
        return TypeConverter.toJsonHex(tx.getHash());
    }
}
