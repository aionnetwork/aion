package org.aion.evtmgr.impl.evt;

import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.impl.abs.AbstractEvent;

/** @author jay */
public class EventMiner extends AbstractEvent implements IEvent {

    private int callback = -1;

    public static final int EVTTYPE = TYPE.MINER0.getValue();

    public enum CALLBACK {
        MININGSTARTED(0),
        MININGSTOPPED(1),
        BLOCKMININGSTARTED(2),
        BLOCKMINED(3),
        BLOCKMININGCANCELED(4);

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

    public EventMiner(CALLBACK _cb) {
        this.callback = _cb.getValue();
    }

    public int getEventType() {
        // TODO Auto-generated method stub
        return EventMiner.EVTTYPE;
    }

    public int getCallbackType() {
        return this.callback;
    }
}
