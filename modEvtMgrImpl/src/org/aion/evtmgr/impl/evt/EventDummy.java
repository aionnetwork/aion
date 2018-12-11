package org.aion.evtmgr.impl.evt;

import java.util.List;
import org.aion.evtmgr.IEvent;

/** @author jay */
public class EventDummy implements IEvent {

    private static final int type = TYPE.DUMMY.getValue();

    @Override
    public int getEventType() {
        return EventDummy.type;
    }

    public static int getTypeStatic() {
        return EventDummy.type;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.aion.evt.common.IEvent#setFuncArgs(java.util.List)
     */
    @Override
    public void setFuncArgs(List<Object> _objs) {}

    @Override
    public List<Object> getFuncArgs() {
        return null;
    }

    @Override
    public int getCallbackType() {
        return 0;
    }
}
