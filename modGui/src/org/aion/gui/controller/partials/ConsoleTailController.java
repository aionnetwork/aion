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

import com.google.common.eventbus.Subscribe;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import org.aion.gui.controller.AbstractController;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.RefreshEvent;
import org.aion.gui.model.ConsoleTail;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.events.UiMessageEvent;

import java.net.URL;
import java.util.ResourceBundle;

public class ConsoleTailController extends AbstractController {
    private ConsoleTail timeIntervalIndicatorFormatter;
    private ConsoleManager consoleManager;

    public ConsoleTailController(ConsoleTail timeIntervalIndicatorFormatter,
                                 ConsoleManager consoleManager) {
        this.timeIntervalIndicatorFormatter = timeIntervalIndicatorFormatter;
        this.consoleManager = consoleManager;
    }

    @FXML
    private Label tail;

    @Override
    protected void internalInit(URL location, ResourceBundle resources) {
        EventBusRegistry.INSTANCE.getBus(UiMessageEvent.ID).register(this);
        EventBusRegistry.INSTANCE.getBus(RefreshEvent.ID).register(this);
    }

    @Subscribe
    private void handleMessage(final UiMessageEvent event) {
        switch (event.getType()) {
            case CONSOLE_LOG:
                timeIntervalIndicatorFormatter.setMessage(event.getMessage());
                updateStatusText();
        }
    }

    @Override
    protected final void refreshView(final RefreshEvent event) {
        updateStatusText();
    }

    private void updateStatusText() {
        Platform.runLater(() -> tail.setText(timeIntervalIndicatorFormatter.makeStatus()));
    }

    public void onClick(MouseEvent mouseEvent) {
        consoleManager.show();
    }
}
