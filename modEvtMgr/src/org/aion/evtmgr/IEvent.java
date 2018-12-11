package org.aion.evtmgr;

import java.util.List;

/** @author jay */
public interface IEvent {

    enum TYPE {
        DUMMY(0),
        TX0(1),
        BLOCK0(2),
        MINER0(3),
        CONSENSUS0(4);

        static final int MAX = 16;
        static final int MIN = 0;
        private int value;

        private static final TYPE[] intMapType = new TYPE[MAX + 1];

        static {
            for (TYPE type : TYPE.values()) {
                intMapType[0xff & type.value] = type;
            }
        }

        TYPE(final int _value) {
            this.value = _value;
        }

        public int getValue() {
            return this.value;
        }
    }

    int getEventType();

    void setFuncArgs(final List<Object> _objs);

    List<Object> getFuncArgs();

    int getCallbackType();
}
