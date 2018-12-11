package org.aion.evtmgr.impl.evt;

import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.impl.abs.AbstractEvent;

/** @author jay */
public class EventConsensus extends AbstractEvent implements IEvent {

    private int callback = -1;

    public static final int EVTTYPE = TYPE.CONSENSUS0.getValue();

    public enum CALLBACK {
        ON_SYNC_DONE(0),

        ON_BLOCK_TEMPLATE(1),

        ON_SOLUTION(2);

        static final int MAX = 127;
        static final int MIN = 0;
        private int value;

        private static final CALLBACK[] intMapCallback = new CALLBACK[MAX + 1];

        static {
            for (CALLBACK type : CALLBACK.values()) {
                intMapCallback[0xff & type.value] = type;
            }
        }

        CALLBACK(final int _value) {
            this.value = _value;
        }

        public int getValue() {
            return this.value;
        }

        public static CALLBACK GETCALLBACK(final int _ctrlInt) {
            if (_ctrlInt < MIN || _ctrlInt > MAX) return null;
            else return intMapCallback[0xff & _ctrlInt];
        }
    }

    public EventConsensus(CALLBACK _cb) {
        this.callback = _cb.getValue();
    }

    public int getEventType() {
        return EventConsensus.EVTTYPE;
    }

    public int getCallbackType() {
        return this.callback;
    }
}
