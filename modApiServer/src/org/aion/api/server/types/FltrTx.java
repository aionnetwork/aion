package org.aion.api.server.types;

import org.aion.base.AionTransaction;

public class FltrTx extends Fltr {

    public FltrTx() {
        super(Fltr.Type.TRANSACTION);
    }

    @Override
    public boolean onTransaction(AionTransaction tx) {
        add(new EvtTx(tx));
        return true;
    }
}
