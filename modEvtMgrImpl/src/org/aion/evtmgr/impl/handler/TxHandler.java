/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.evtmgr.impl.handler;

import java.util.List;

import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventCallback;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.abs.AbstractHandler;
import org.aion.evtmgr.impl.callback.EventCallbackA0;
import org.aion.evtmgr.impl.evt.EventTx;

/**
 * @author jay
 *
 */
public class TxHandler extends AbstractHandler implements IHandler {

    public TxHandler() {
        dispatcher.setName("TxHdr");
    }

    @SuppressWarnings("rawtypes")
    public <E extends IEvent> void dispatch(E event) {
        if (this.typeEqual(event.getEventType())) {

            if (LOG.isTraceEnabled()) {
                LOG.trace("CB size:[{}] cbType:[{}]", this.eventCallback.size(), event.getCallbackType());
            }

            for (IEventCallback cb : this.eventCallback) {
                es.execute(() -> {
                    switch (event.getCallbackType()) {
                    case 0:
                        ((EventCallbackA0) cb).onPendingTxStateChange();
                        break;
                    case 1:
                        ((EventCallbackA0) cb).onPendingTxUpdate(event.getFuncArgs().get(0),
                                EventTx.STATE.GETSTATE((int) event.getFuncArgs().get(1)), event.getFuncArgs().get(2));
                        break;
                    case 2:
                        List<Object> pendingTx = (List<Object>) event.getFuncArgs().get(0);
                        for (Object o : pendingTx) {
                            ((EventCallbackA0) cb).onPendingTxReceived(o);
                        }
                        break;
                    default:
                    }
                });
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.aion.evt.common.IHandler#getType()
     */

    /*
     * (non-Javadoc)
     * 
     * @see org.aion.evt.common.IHandler#onEvent(org.aion.evt.common.IEvent)
     */
    @Override
    public void onEvent(IEvent _evt) {
        // TODO Auto-generated method stub
        this.queue.add(_evt);
    }

    @Override
    public int getType() {
        return TYPE.TX0.getValue();
    }

    @Override
    public void stop() throws InterruptedException {
        super.stop();
    }
}
