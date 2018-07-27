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

package org.aion.evtmgr.impl.abs;

import java.util.List;

import org.aion.evtmgr.IEvent;

/**
 * @author jay
 *
 */
public abstract class AbstractEvent implements IEvent {

    private List<Object> funcArgs;

    public abstract int getEventType();

    public abstract int getCallbackType();

    public void setFuncArgs(final List<Object> _objs) {
        this.funcArgs = _objs;
    }

    public List<Object> getFuncArgs() {
        return this.funcArgs;
    }

    @Override
    public boolean equals(Object o) {
        try{
            return this.getEventType() == ((IEvent) o).getEventType() && this.getCallbackType() == ((IEvent) o).getCallbackType();
        }catch (ClassCastException e){
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.getCallbackType() * 99839 + this.getEventType();
    }
}
