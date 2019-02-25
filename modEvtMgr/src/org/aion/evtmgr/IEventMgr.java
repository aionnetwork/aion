package org.aion.evtmgr;

import java.util.List;

public interface IEventMgr {
    boolean registerEvent(List<IEvent> _evt);

    boolean unregisterEvent(List<IEvent> _evt);

    boolean newEvents(List<IEvent> _evt);

    boolean newEvent(IEvent _evt);

    List<IHandler> getHandlerList();

    IHandler getHandler(int _type);

    void shutDown() throws InterruptedException;

    void start();
}
