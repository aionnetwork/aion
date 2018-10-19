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

import javafx.scene.Node;

public class WindowControlsEvent extends AbstractUIEvent<WindowControlsEvent.Type> {

    public static final String ID = "gui.window_controls";

    private final Node eventSource;

    public WindowControlsEvent(final Type eventType, final Node eventSource) {
        super(eventType);
        this.eventSource = eventSource;
    }

    public Node getSource() {
        return eventSource;
    }

    public enum Type {
        MINIMIZE,
        CLOSE
    }
}
