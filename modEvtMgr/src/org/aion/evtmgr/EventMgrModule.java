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

import java.util.Properties;

public final class EventMgrModule {
    private static EventMgrModule singleton = null;
    public static final String MODULENAME = "module_name";

    private static IEventMgr EVTMGR;

    private EventMgrModule(Properties config) throws Throwable {
        String moduleName = (String) config.get(MODULENAME);
        if (moduleName != null) {
            EVTMGR = (IEventMgr) getClass().getClassLoader().loadClass(moduleName)
                    .getDeclaredConstructor().newInstance();
            if (EVTMGR == null) {
                throw new Exception("Can not load the event manager module!");
            }
        } else {
            throw new Exception("No module name input!");
        }
    }

    public static EventMgrModule getSingleton(Properties config) throws Throwable {
        if (config == null) {
            throw new Exception("empty config!");
        }

        if (EventMgrModule.singleton == null) {
            EventMgrModule.singleton = new EventMgrModule(config);
        }

        return EventMgrModule.singleton;
    }

    public IEventMgr getEventMgr() throws Throwable {
        if (EventMgrModule.singleton == null) {
            throw new Exception("Module does not initialzed!");
        }

        return EventMgrModule.EVTMGR;
    }
}
