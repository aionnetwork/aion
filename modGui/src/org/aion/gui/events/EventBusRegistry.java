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

package org.aion.gui.events;

import com.google.common.eventbus.EventBus;
import java.util.HashMap;
import java.util.Map;

public class EventBusRegistry {

    // FIXME: Be consistent about where these constans are stored and what warrants its own EventBus
    public static final String KERNEL_BUS = "KernelBus";

    public static final EventBusRegistry INSTANCE = new EventBusRegistry();

    private final Map<String, EventBus> busMap = new HashMap<>();

    public EventBusRegistry() {
    }

    public EventBus getBus(final String identifier) {
        return getBusById(identifier);
    }

    private EventBus getBusById(final String identifier) {
        return busMap.computeIfAbsent(identifier, EventBus::new);
    }
}
