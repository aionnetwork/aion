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
public class EventConsensus extends AbstractEvent implements IEvent {

    public final static int EVTTYPE = TYPE.CONSENSUS0.getValue();
    private int callback = -1;

    public EventConsensus(CALLBACK _cb) {
        this.callback = _cb.getValue();
    }

    public int getEventType() {
        return EventConsensus.EVTTYPE;
    }

    public int getCallbackType() {
        return this.callback;
    }

    public enum CALLBACK {
        ON_SYNC_DONE(0),

        ON_BLOCK_TEMPLATE(1),

        ON_SOLUTION(2);

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
