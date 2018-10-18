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

package org.aion.evtmgr;

import java.util.List;

/**
 * @author jay
 */
public interface IEvent {

    int getEventType();

    List<Object> getFuncArgs();

    void setFuncArgs(final List<Object> _objs);

    int getCallbackType();

    enum TYPE {
        DUMMY(0), TX0(1), BLOCK0(2), MINER0(3), CONSENSUS0(4);

        final static int MAX = 16;
        final static int MIN = 0;
        private final static TYPE[] intMapType = new TYPE[MAX + 1];

        static {
            for (TYPE type : TYPE.values()) {
                intMapType[0xff & type.value] = type;
            }
        }

        private int value;

        TYPE(final int _value) {
            this.value = _value;
        }

        public int getValue() {
            return this.value;
        }
    }
}
