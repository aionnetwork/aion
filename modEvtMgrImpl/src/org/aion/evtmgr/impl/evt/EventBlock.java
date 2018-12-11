package org.aion.evtmgr.impl.evt;

import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.impl.abs.AbstractEvent;

/** @author jay */
public class EventBlock extends AbstractEvent implements IEvent {

    private int callback = -1;

    public static final int EVTTYPE = TYPE.BLOCK0.getValue();

    public enum CALLBACK {
        ONBLOCK0(0),
        ONTRACE0(1),
        ONBEST0(2);

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

    public EventBlock(CALLBACK _cb) {
        this.callback = _cb.getValue();
    }

    public int getEventType() {
        return EventBlock.EVTTYPE;
    }

    public int getCallbackType() {
        return this.callback;
    }
}
