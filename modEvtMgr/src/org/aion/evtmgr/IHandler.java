package org.aion.evtmgr;

/** @author jay */
public interface IHandler {

    enum TYPE {
        POISONPILL(0),
        TX0(1),
        BLOCK0(2),
        MINER0(3),
        CONSENSUS(4);

        static final int MAX = 8;

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

        public static TYPE GETTYPE(final int _ctrlInt) {
            if (_ctrlInt < MIN || _ctrlInt > MAX) return null;
            else return intMapType[0xff & _ctrlInt];
        }
    }

    int getType();

    boolean addEvent(IEvent _evt);

    boolean removeEvent(IEvent _evt);

    void onEvent(IEvent _evt);

    void eventCallback(IEventCallback _evtCallback);

    void start();

    void stop() throws InterruptedException;
}
