/*******************************************************************************
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
 *     
 ******************************************************************************/

package org.aion.evtmgr;

/**
 * @author jay
 *
 */
public interface IHandler {

    enum TYPE {
        DUMMY(0), TX0(1), BLOCK0(2), MINER0(3), CONSENSUS(4);

        final static int MAX = 8;

        final static int MIN = 0;

        private int value;

        private final static TYPE[] intMapType = new TYPE[MAX + 1];

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
            if (_ctrlInt < MIN || _ctrlInt > MAX)
                return null;
            else
                return intMapType[0xff & _ctrlInt];
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
