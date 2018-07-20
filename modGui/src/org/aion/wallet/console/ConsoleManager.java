package org.aion.wallet.console;

import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ConsoleManager {
    private StackPane root;
    private final TextArea logsTextField;
    private Stage consoleLogWindow;
    private StringBuilder logs = new StringBuilder();

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("dd-MM-YY - HH.mm.ss");

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
    }
    
    public void addLog(String log, LogType type) {
        addLog(log, type, LogLevel.INFO);
    }

    public void show() {
        consoleLogWindow.show();
    }

    public enum LogType {
        ACCOUNT,
        TRANSACTION,
        SETTINGS
    }

    public enum LogLevel {
        INFO,
        WARNING
    }
}
