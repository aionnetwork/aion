package org.aion.evtmgr.impl.handler;

import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.abs.AbstractHandler;

/** @author jay */
public class TxHandler extends AbstractHandler implements IHandler {

    public TxHandler() {
        super(TYPE.TX0.getValue());
        dispatcher.setName("TxHdr");
    }
}
