package org.aion.evtmgr.impl.handler;

import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.abs.AbstractHandler;

/** @author jay */
public class MinerHandler extends AbstractHandler implements IHandler {

    public MinerHandler() {
        super(TYPE.MINER0.getValue());
        dispatcher.setName("MinerHdr");
    }
}
