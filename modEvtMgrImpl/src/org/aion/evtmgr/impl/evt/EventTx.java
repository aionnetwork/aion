/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.evtmgr.impl.evt;

import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.impl.abs.AbstractEvent;

/**
 * @author jay
 */
public class EventTx extends AbstractEvent implements IEvent {

    public final static int EVTTYPE = TYPE.TX0.getValue();
    private int state = -1;
    private int callback = -1;

    public EventTx(CALLBACK _cb) {
        this.callback = _cb.getValue();
    }

    public EventTx(STATE _s, CALLBACK _cb) {
        this.state = _s.getValue();
        this.callback = _cb.getValue();
    }

    public int getEventType() {
        return EventTx.EVTTYPE;
    }

    public int getState() {
        return this.state;
    }

    public int getCallbackType() {
        return this.callback;
    }

    public enum STATE {
        /**
         * Transaction may be dropped due to: - Invalid transaction (invalid nonce, low nrg price,
         * insufficient account funds, invalid signature) - Timeout (when pending transaction is not
         * included to any block for last [transaction.outdated.threshold] blocks This is the final
         * state
         */
        DROPPED0(0),
        /**
         * The same as PENDING when transaction is just arrived Next state can be either PENDING or
         * INCLUDED
         */
        NEW_PENDING0(1),
        /**
         * State when transaction is not included to any blocks (on the main chain), and was
         * executed on the last best block. The repository state is reflected in the PendingState
         * Next state can be either INCLUDED, DROPPED (due to timeout) or again PENDING when a new
         * block (without this transaction) arrives
         */
        PENDING0(2),
        /**
         * State when the transaction is included to a block. This could be the final state, however
         * next state could also be PENDING: when a fork became the main chain but doesn't include
         * this tx INCLUDED: when a fork became the main chain and tx is included into another block
         * from the new main chain DROPPED: If switched to a new (long enough) main chain without
         * this Tx
         */
        INCLUDED(3);

        final static int MAX = 3;
        final static int MIN = 0;
        private final static STATE[] intMapState = new STATE[MAX + 1];

        static {
            for (STATE type : STATE.values()) {
                intMapState[0xff & type.value] = type;
            }
        }

        private int value;

        STATE(final int _value) {
            this.value = _value;
        }

        public static STATE GETSTATE(final int _ctrlInt) {
            if (_ctrlInt < MIN || _ctrlInt > MAX) {
                return null;
            } else {
                return intMapState[0xff & _ctrlInt];
            }
        }

        public int getValue() {
            return this.value;
        }

        public boolean isPending() {
            return this.value == 1 || this.value == 2;
        }
    }

    public enum CALLBACK {
        PENDINGTXSTATECHANGE0(0), PENDINGTXUPDATE0(1), PENDINGTXRECEIVED0(2), TXEXECUTED0(
            3), TXBACKUP0(4);

        final static int MAX = 127;
        final static int MIN = 0;
        private final static CALLBACK[] intMapCallback = new CALLBACK[MAX + 1];

        static {
            for (CALLBACK type : CALLBACK.values()) {
                intMapCallback[0xff & type.value] = type;
            }
        }

        private int value;

        CALLBACK(final int _value) {
            this.value = _value;
        }

        public static CALLBACK GETCALLBACK(final int _ctrlInt) {
            if (_ctrlInt < MIN || _ctrlInt > MAX) {
                return null;
            } else {
                return intMapCallback[0xff & _ctrlInt];
            }
        }

        public int getValue() {
            return this.value;
        }
    }

}
