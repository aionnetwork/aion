package org.aion.mcf.evt;

@FunctionalInterface
public interface EvtCb<T extends EvtData> {
    void call(T t);
}
