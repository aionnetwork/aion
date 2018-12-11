package org.aion.mcf.evt;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Event Hub. */
public class EventHub<D extends EvtData> {

    List<EvtCb<D>> subs[];

    public EventHub(int size) {
        subs = new List[size];
        for (int i = 0; i < size; i++) {
            subs[i] = new CopyOnWriteArrayList<>();
        }
    }

    public void reg(int type, EvtCb<D> t) {
        subs[type].add(t);
    }

    public void fire(int type, D data) {
        subs[type].forEach(
                (t) -> {
                    t.call(data);
                });
    }
}
