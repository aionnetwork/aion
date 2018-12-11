package org.aion.evtmgr.impl.handler;

import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.abs.AbstractHandler;

/** @author jay */
public class ConsensusHandler extends AbstractHandler implements IHandler {

    // Default constructor to set name of the thread, simplifies troubleshooting
    public ConsensusHandler() {
        super(TYPE.CONSENSUS.getValue());
        dispatcher.setName("ConsHdr");
    }
}
