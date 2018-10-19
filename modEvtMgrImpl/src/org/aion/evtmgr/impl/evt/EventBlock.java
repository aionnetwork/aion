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
