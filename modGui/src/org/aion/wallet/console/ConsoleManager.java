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
package org.aion.wallet.console;

import java.text.SimpleDateFormat;
import java.util.Date;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.aion.gui.events.EventPublisher;

public class ConsoleManager {
    private StackPane root;
    private final TextArea logsTextField;
    private Stage consoleLogWindow;
    private StringBuilder logs = new StringBuilder();

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT =
            new SimpleDateFormat("dd-MM-YY - HH.mm.ss");

    public ConsoleManager() {
        logsTextField = new TextArea();
        logsTextField.setEditable(false);

        root = new StackPane();
        root.getChildren().add(logsTextField);
        Scene scene = new Scene(root, 600, 350);

        consoleLogWindow = new Stage();
        consoleLogWindow.setTitle("Console");
        consoleLogWindow.setScene(scene);
    }

    public void addLog(String log, LogType type, LogLevel level) {
        logs.append(SIMPLE_DATE_FORMAT.format(new Date()));
        logs.append(" ");
        logs.append(level.toString());
        logs.append(" [");
        logs.append(type.toString());
        logs.append("]: ");
        logs.append(log);
        logs.append("\n");
        logsTextField.setText(logs.toString());
        logsTextField.setScrollTop(Double.MAX_VALUE);
        EventPublisher.fireConsoleLogged(log);
    }

    public void addLog(String log, LogType type) {
        addLog(log, type, LogLevel.INFO);
    }

    public void show() {
        consoleLogWindow.show();
    }

    public enum LogType {
        KERNEL,
        ACCOUNT,
        TRANSACTION,
        SETTINGS
    }

    public enum LogLevel {
        INFO,
        WARNING
    }
}
