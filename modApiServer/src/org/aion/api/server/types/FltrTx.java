package org.aion.api.server.types;

import org.aion.base.Transaction;

public class FltrTx extends Fltr {

    public FltrTx() {
        super(Fltr.Type.TRANSACTION);
    }

    @Override
    public boolean onTransaction(Transaction tx) {
        add(new EvtTx(tx));
        return true;
    }
}
