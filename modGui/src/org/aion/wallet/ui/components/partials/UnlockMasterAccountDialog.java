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
package org.aion.wallet.ui.components.partials;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Popup;
import org.aion.gui.controller.ControllerFactory;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.console.ConsoleManager;
import org.slf4j.Logger;

public class UnlockMasterAccountDialog implements Initializable {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GUI.name());
    private final AccountManager accountManager;
    private final ConsoleManager consoleManager;
    private final Popup popup = new Popup();
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label validationError;

    public UnlockMasterAccountDialog(AccountManager accountManager,
        ConsoleManager consoleManager) {
        this.accountManager = accountManager;
        this.consoleManager = consoleManager;
    }

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        Platform.runLater(() -> passwordField.requestFocus());
    }

    public void open(MouseEvent mouseEvent) {
        popup.setAutoHide(true);
        popup.setAutoFix(true);

        Pane addAccountDialog;
        try {
            FXMLLoader loader = new FXMLLoader(
                (getClass().getResource("UnlockMasterAccountDialog.fxml")));
            loader.setControllerFactory(new ControllerFactory()
                .withAccountManager(accountManager)
                .withConsoleManager(
                    consoleManager)/* TODO a specialization only has what we need */);
            addAccountDialog = loader.load();
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            return;
        }

        Node eventSource = (Node) mouseEvent.getSource();
        final double windowX = eventSource.getScene().getWindow().getX();
        final double windowY = eventSource.getScene().getWindow().getY();
        popup.setX(
            windowX + eventSource.getScene().getWidth() / 2 - addAccountDialog.getPrefWidth() / 2);
        popup.setY(windowY + eventSource.getScene().getHeight() / 2
            - addAccountDialog.getPrefHeight() / 2);
        popup.getContent().addAll(addAccountDialog);
        popup.show(eventSource.getScene().getWindow());
    }

    public void close(InputEvent eventSource) {
        ((Node) eventSource.getSource()).getScene().getWindow().hide();
    }

    @FXML
    private void submitOnEnterPressed(final KeyEvent event) {
        if (event.getCode().equals(KeyCode.ENTER)) {
            unlockMasterAccount(event);
        }
    }

    @FXML
    private void unlockMasterAccount(final InputEvent mouseEvent) {
        try {
            accountManager.unlockMasterAccount(passwordField.getText());
            consoleManager.addLog("Master account unlocked", ConsoleManager.LogType.ACCOUNT);
            close(mouseEvent);
        } catch (Exception e) {
            consoleManager.addLog("Could not unlock master account", ConsoleManager.LogType.ACCOUNT,
                ConsoleManager.LogLevel.WARNING);
            showInvalidFieldsError(e.getMessage());
        }
    }

    public void resetValidation() {
        validationError.setVisible(false);
    }

    private void showInvalidFieldsError(String message) {
        validationError.setVisible(true);
        validationError.setText(message);
    }
}
