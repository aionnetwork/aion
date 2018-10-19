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

package org.aion.gui.controller.partials;

import com.google.common.eventbus.EventBus;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.WindowControlsEvent;

public class WindowControls {

    private final WindowControlsEvent closeEvent =
            new WindowControlsEvent(WindowControlsEvent.Type.CLOSE, null);
    private final EventBus eventBus = EventBusRegistry.INSTANCE.getBus(WindowControlsEvent.ID);

    @FXML
    private void minimize(final MouseEvent mouseEvent) {
        Node source = (Node) mouseEvent.getSource();
        final WindowControlsEvent minimizeEvent =
                new WindowControlsEvent(WindowControlsEvent.Type.MINIMIZE, source);
        eventBus.post(minimizeEvent);
    }

    @FXML
    private void close() {
        eventBus.post(closeEvent);
    }
}
