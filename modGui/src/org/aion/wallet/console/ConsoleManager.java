package org.aion.wallet.console;

import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ConsoleManager {
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("dd-MM-YY - HH.mm.ss");

    private static final TextArea LOGS_TEXT_FIELD = new TextArea();
    private static Stage CONSOLE_LOG_WINDOW;
    private static StringBuilder logs = new StringBuilder();

    static {
        LOGS_TEXT_FIELD.setEditable(false);
        StackPane root = new StackPane();
        root.getChildren().add(ConsoleManager.LOGS_TEXT_FIELD);
        Scene scene = new Scene(root, 600, 350);

        CONSOLE_LOG_WINDOW = new Stage();
        CONSOLE_LOG_WINDOW.setTitle("Console");
        CONSOLE_LOG_WINDOW.setScene(scene);
    }

    public static void addLog(String log, LogType type, LogLevel level) {
        logs.append(SIMPLE_DATE_FORMAT.format(new Date()));
        logs.append(" ");
        logs.append(level.toString());
        logs.append(" [");
        logs.append(type.toString());
        logs.append("]: ");
        logs.append(log);
        logs.append("\n");
        LOGS_TEXT_FIELD.setText(logs.toString());
        LOGS_TEXT_FIELD.setScrollTop(Double.MAX_VALUE);
    }

    public static void addLog(String log, LogType type) {
        addLog(log, type, LogLevel.INFO);
    }

    public static void show() {
        CONSOLE_LOG_WINDOW.show();
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
