package org.aion.evtmgr.impl.handler;

import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.abs.AbstractHandler;

/** @author jay */
public class BlockHandler extends AbstractHandler implements IHandler {

    // Default constructor to set name of the thread, simplifies troubleshooting
    public BlockHandler() {
        super(TYPE.BLOCK0.getValue());
        dispatcher.setName("BlkHdr");
    }
}
