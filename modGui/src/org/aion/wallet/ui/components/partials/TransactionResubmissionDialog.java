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
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.InputEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.aion.gui.events.EventPublisher;
import org.aion.gui.util.BalanceUtils;
import org.aion.log.AionLoggerFactory;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.connector.dto.SendTransactionDTO;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.dto.AccountDTO;
import org.slf4j.Logger;

public class TransactionResubmissionDialog implements Initializable {

    private static final Logger LOGGER = AionLoggerFactory
        .getLogger(org.aion.log.LogEnum.GUI.name());
    private final Popup popup = new Popup();
    private ConsoleManager consoleManager;
    private AccountManager accountManager;

    @FXML
    private VBox transactions;

    public TransactionResubmissionDialog(AccountManager accountManager,
        ConsoleManager consoleManager) {
        this.consoleManager = consoleManager;
        this.accountManager = accountManager;
    }

    public void open(final MouseEvent mouseEvent) {
        popup.setAutoHide(true);
        popup.setAutoFix(true);

        Pane resubmitTransactionDialog;
        try {
            resubmitTransactionDialog = FXMLLoader
                .load(getClass().getResource("TransactionResubmissionDialog.fxml"));
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            return;
        }

        Node eventSource = (Node) mouseEvent.getSource();
        final double windowX = eventSource.getScene().getWindow().getX();
        final double windowY = eventSource.getScene().getWindow().getY();
        popup.setX(windowX + eventSource.getScene().getWidth() / 2
            - resubmitTransactionDialog.getPrefWidth() / 2);
        popup.setY(windowY + eventSource.getScene().getHeight() / 2
            - resubmitTransactionDialog.getPrefHeight() / 2);
        popup.getContent().addAll(resubmitTransactionDialog);
        popup.show(eventSource.getScene().getWindow());
    }

    public void close(InputEvent eventSource) {
        ((Node) eventSource.getSource()).getScene().getWindow().hide();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        displayTransactions();
    }

    private void displayTransactions() {
        addHeaderForTable();
        final Optional<AccountDTO> first = accountManager.getAccounts().stream()
            .filter(AccountDTO::isActive).findFirst();
        final String publicAddress;
        if (first.isPresent()) {
            publicAddress = first.get().getPublicAddress();
            for (SendTransactionDTO unsentTransaction : accountManager
                .getTimedOutTransactions(publicAddress)) {
                HBox row = new HBox();
                row.setSpacing(10);
                row.setAlignment(Pos.CENTER);
                row.setPrefWidth(600);

                Label to = new Label(unsentTransaction.getTo());
                to.setPrefWidth(350);
                to.getStyleClass().add("transaction-row-text");
                row.getChildren().add(to);

                Label value = new Label(BalanceUtils.formatBalance(unsentTransaction.getValue()));
                value.setPrefWidth(100);
                value.setPadding(new Insets(0.0, 0.0, 0.0, 10.0));
                value.getStyleClass().add("transaction-row-text");
                row.getChildren().add(value);

                Label nonce = new Label(unsentTransaction.getNonce().toString());
                nonce.setPrefWidth(50);
                nonce.setPadding(new Insets(0.0, 0.0, 0.0, 5.0));
                nonce.getStyleClass().add("transaction-row-text");
                row.getChildren().add(nonce);

                Button resubmitTransaction = new Button();
                resubmitTransaction.setText("Resubmit");
                resubmitTransaction.setPrefWidth(100);
                resubmitTransaction.getStyleClass().add("submit-button-small");
                resubmitTransaction.setOnMouseClicked(event -> {
                    close(event);
                    accountManager.removeTimedOutTransaction(unsentTransaction);
                    consoleManager
                        .addLog("Transaction timeout treated", ConsoleManager.LogType.TRANSACTION);
                    EventPublisher.fireTransactionResubmited(unsentTransaction);
                });
                row.getChildren().add(resubmitTransaction);

                transactions.getChildren().add(row);
            }
        }
    }

    private void addHeaderForTable() {
        HBox header = new HBox();
        header.setSpacing(10);
        header.setPrefWidth(400);
        header.setAlignment(Pos.CENTER);
        header.getStyleClass().add("transaction-row");

        Label to = new Label("To address");
        to.setPrefWidth(250);
        to.getStyleClass().add("transaction-table-header-text");
        header.getChildren().add(to);

        Label value = new Label("Value");
        value.setPrefWidth(75);
        value.getStyleClass().add("transaction-table-header-text");
        header.getChildren().add(value);

        Label nonce = new Label("Nonce");
        nonce.setPrefWidth(75);
        nonce.getStyleClass().add("transaction-table-header-text");
        header.getChildren().add(nonce);

        transactions.getChildren().add(header);
    }
}
