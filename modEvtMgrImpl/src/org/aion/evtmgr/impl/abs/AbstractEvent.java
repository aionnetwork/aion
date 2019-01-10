package org.aion.evtmgr.impl.abs;

import java.util.List;
import org.aion.evtmgr.IEvent;

/** @author jay */
public abstract class AbstractEvent implements IEvent {

    private List<Object> funcArgs;

    public abstract int getEventType();

    public abstract int getCallbackType();

    public void setFuncArgs(final List<Object> _objs) {
        this.funcArgs = _objs;
    }

    public List<Object> getFuncArgs() {
        return this.funcArgs;
    }

    @Override
    public boolean equals(Object o) {
        try {
            return this.getEventType() == ((IEvent) o).getEventType()
                    && this.getCallbackType() == ((IEvent) o).getCallbackType();
        } catch (ClassCastException e) {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.getCallbackType() * 99839 + this.getEventType();
    }
}
