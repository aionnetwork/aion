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
package org.aion.gui.controller;


import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.HeaderPaneButtonEvent;
import org.aion.gui.model.TransactionProcessor;
import org.aion.gui.model.dto.SyncInfoDto;
import org.aion.gui.util.AionConstants;
import org.aion.gui.util.BalanceUtils;
import org.aion.log.AionLoggerFactory;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.dto.TransactionDTO;
import org.aion.wallet.events.AccountEvent;
import org.aion.wallet.util.AddressUtils;
import org.aion.wallet.util.URLManager;
import org.slf4j.Logger;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class HistoryController extends AbstractController {

    private static final Logger LOGGER = AionLoggerFactory.getLogger(org.aion.log.LogEnum.GUI.name());

    private static final String COPY_MENU = "Copy";

    private static final String LINK_STYLE = "link-style";

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd - HH.mm.ss");

    private final TransactionProcessor transactionProcessor;
    private final AccountManager accountManager;
    private final SyncInfoDto syncInfoDto;

    public HistoryController(TransactionProcessor transactionProcessor,
                             AccountManager accountManager,
                             SyncInfoDto syncInfoDto) {
        this.transactionProcessor = transactionProcessor;
        this.accountManager = accountManager;
        this.syncInfoDto = syncInfoDto;
    }

    @FXML
    private TableView<TxRow> txTable;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> searchItem;

    private AccountDTO account;

    private List<TxRow> completeTransactionList = Lists.newArrayList();

    protected void internalInit(final URL location, final ResourceBundle resources) {
        initSearchItemDropDown();
        buildTableModel();
        setEventHandlers();
        reloadWalletView();
    }

    private void initSearchItemDropDown() {
        searchItem.setItems(FXCollections.observableArrayList(
                "Type",
                "Date",
                "Transaction hash",
                "Value",
                "Status"
        ));
        searchItem.getSelectionModel().select(0);
    }

    @Subscribe
    private void handleHeaderPaneButtonEvent(final HeaderPaneButtonEvent event) {
        if (event.getType().equals(HeaderPaneButtonEvent.Type.HISTORY)) {
            reloadWalletView();
        }
    }

    @Subscribe
    private void handleAccountChanged(final AccountEvent event) {
        if (EnumSet.of(AccountEvent.Type.CHANGED, AccountEvent.Type.ADDED).contains(event.getType())) {
            this.account = event.getPayload();
            if (isInView()) {
                reloadWalletView();
            } else {
                txTable.setItems(FXCollections.emptyObservableList());
            }
        } else if (AccountEvent.Type.LOCKED.equals(event.getType())) {
            if (event.getPayload().equals(account)) {
                account = null;
                txTable.setItems(FXCollections.emptyObservableList());
            }
        }
    }

    protected void registerEventBusConsumer() {
        super.registerEventBusConsumer();
        EventBusRegistry.INSTANCE.getBus(HeaderPaneButtonEvent.ID).register(this);
        EventBusRegistry.INSTANCE.getBus(AccountEvent.ID).register(this);
    }

    private void reloadWalletView() {
        if (account == null) {
            return;
        }
        final Task<List<TxRow>> getTransactionsTask = getApiTask(
                address -> transactionProcessor.getLatestTransactions(address).stream()
                        .map(t -> new TxRow(address, t)).collect(Collectors.toList()),
                account.getPublicAddress()
        );

        runApiTask(
                getTransactionsTask,
                event -> {
                    final List<TxRow> transactions = getTransactionsTask.getValue();
                    completeTransactionList = new ArrayList<>(transactions);
                    txTable.setItems(FXCollections.observableList(transactions));
                },
                getEmptyEvent(),
                getEmptyEvent()
        );
    }

    private void buildTableModel() {
        final TableColumn<TxRow, String> typeCol = getTableColumn("Type", "type", 0.09);
        final TableColumn<TxRow, String> nameCol = getTableColumn("Date", "date", 0.2);
        final TableColumn<TxRow, String> hashCol = getTableColumn("Tx Hash", "txHash", 0.5);
        final TableColumn<TxRow, String> valueCol = getTableColumn("Value", "value", 0.12);
        final TableColumn<TxRow, String> statusCol = getTableColumn("Status", "status", 0.1);

        hashCol.setCellFactory(column -> new TransactionHashCell());

        txTable.getColumns().addAll(Arrays.asList(typeCol, nameCol, hashCol, valueCol, statusCol));
    }

    private TableColumn<TxRow, String> getTableColumn(final String header, final String property, final double sizePercent) {
        final TableColumn<TxRow, String> valueCol = new TableColumn<>(header);
        valueCol.setCellValueFactory(new PropertyValueFactory<>(property));
        valueCol.prefWidthProperty().bind(txTable.widthProperty().multiply(sizePercent));
        return valueCol;
    }

    private void setEventHandlers() {
        txTable.setOnKeyPressed(new KeyTableCopyEventHandler());
        txTable.setOnMouseClicked(new MouseLinkEventHandler());

        ContextMenu menu = new ContextMenu();
        final MenuItem copyItem = new MenuItem(COPY_MENU);
        copyItem.setOnAction(new ContextMenuTableCopyEventHandler(txTable));
        menu.getItems().add(copyItem);
        txTable.setContextMenu(menu);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            final FilteredList<TxRow> filteredData = new FilteredList<>(FXCollections.observableList(completeTransactionList), s -> true);
            if (!newValue.isEmpty()) {
                filteredData.setPredicate(s -> anyFieldHasString(s, newValue));
                SortedList<TxRow> sortedData = new SortedList<>(filteredData);
                sortedData.comparatorProperty().bind(txTable.comparatorProperty());
                txTable.setItems(sortedData);
            }
        });

        searchItem.valueProperty().addListener((observable, oldValue, newValue) -> {
            final FilteredList<TxRow> filteredData = new FilteredList<>(FXCollections.observableList(completeTransactionList), s -> true);
            if(!String.valueOf(newValue).equals(String.valueOf(oldValue))) {
                filteredData.setPredicate(s -> anyFieldHasString(s, searchField.getText()));
                SortedList<TxRow> sortedData = new SortedList<>(filteredData);
                sortedData.comparatorProperty().bind(txTable.comparatorProperty());
                txTable.setItems(sortedData);
            }
        });
    }

    private boolean anyFieldHasString(final TxRow currentRow, final String searchString) {
        switch (searchItem.getSelectionModel().getSelectedIndex()) {
            case 0:
                return currentRow.getType().toLowerCase().contains(searchString.toLowerCase());
            case 1:
                return currentRow.getDate().toLowerCase().contains(searchString.toLowerCase());
            case 2:
                return currentRow.getTxHash().toLowerCase().contains(searchString.toLowerCase());
            case 3:
                return currentRow.getValue().toLowerCase().contains(searchString.toLowerCase());
            case 4:
                return currentRow.getStatus().toLowerCase().contains(searchString.toLowerCase());
        }
        return true;
    }

    private static class KeyTableCopyEventHandler extends TableCopyEventHandler<KeyEvent> {
        private final KeyCodeCombination copyKeyCodeCombination = new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_ANY);

        public void handle(final KeyEvent keyEvent) {
            if (copyKeyCodeCombination.match(keyEvent)) {
                if (keyEvent.getSource() instanceof TableView) {
                    copySelectionToClipboard((TableView<?>) keyEvent.getSource());
                    keyEvent.consume();
                }
            }
        }
    }

    private static class ContextMenuTableCopyEventHandler extends TableCopyEventHandler<ActionEvent> {


        private final TableView<TxRow> txTable;

        public ContextMenuTableCopyEventHandler(final TableView<TxRow> txTable) {
            this.txTable = txTable;
        }

        public void handle(final ActionEvent keyEvent) {
            copySelectionToClipboard(txTable);
            keyEvent.consume();
        }
    }

    private static abstract class TableCopyEventHandler<T extends Event> implements EventHandler<T> {

        private static final char TAB = '\t';
        private static final char NEWLINE = '\n';

        protected final void copySelectionToClipboard(TableView<?> table) {
            StringBuilder clipboardString = new StringBuilder();
            ObservableList<TablePosition> positionList = table.getSelectionModel().getSelectedCells();
            int prevRow = -1;
            for (TablePosition position : positionList) {
                int row = position.getRow();
                int col = position.getColumn();
                Object cell = table.getColumns().get(col).getCellData(row);
                if (cell == null) {
                    cell = "";
                }
                if (prevRow == row) {
                    clipboardString.append(TAB);
                } else if (prevRow != -1) {
                    clipboardString.append(NEWLINE);
                }
                String text = cell.toString();
                clipboardString.append(text);
                prevRow = row;
            }
            final ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(clipboardString.toString());
            Clipboard.getSystemClipboard().setContent(clipboardContent);
        }
    }

    private static class MouseLinkEventHandler implements EventHandler<MouseEvent> {

        @Override
        public void handle(final MouseEvent mouseEvent) {
            if (MouseEvent.MOUSE_CLICKED.equals(mouseEvent.getEventType()) && MouseButton.PRIMARY.equals(mouseEvent.getButton())) {
                if (mouseEvent.getSource() instanceof TableView) {
                    redirect((TableView<?>) mouseEvent.getSource());
                    mouseEvent.consume();
                }
            }
        }

        private void redirect(final TableView<?> table) {
            ObservableList<TablePosition> positionList = table.getSelectionModel().getSelectedCells();
            for (TablePosition position : positionList) {
                int row = position.getRow();
                int col = position.getColumn();
                if (table.getColumns().get(col).getText().equals("Tx Hash")) {
                    Object cell = table.getColumns().get(col).getCellData(row);
                    URLManager.openTransaction(cell.toString());
                }
            }
        }
    }

    public class TxRow {

        private static final String TO = "outgoing";
        private static final String FROM = "incoming";

        private final TransactionDTO transaction;
        private final SimpleStringProperty type;
        private final SimpleStringProperty date;
        private final SimpleStringProperty status;
        private final SimpleStringProperty value;

        private final SimpleStringProperty txHash;

        private TxRow(final String requestingAddress, final TransactionDTO dto) {
            transaction = dto;
            final AccountDTO fromAccount = accountManager.getAccount(dto.getFrom());
            final String balance = BalanceUtils.formatBalance(dto.getValue());
            boolean isFromTx = AddressUtils.equals(requestingAddress, fromAccount.getPublicAddress());
            this.type = new SimpleStringProperty(isFromTx ? TO : FROM);
            this.date = new SimpleStringProperty(SIMPLE_DATE_FORMAT.format(new Date(dto.getTimeStamp() * 1000)));
            this.status = new SimpleStringProperty(getTransactionStatus(dto));
            this.value = new SimpleStringProperty(balance);
            this.txHash = new SimpleStringProperty(dto.getHash());
        }

        private String getTransactionStatus(TransactionDTO dto) {
            syncInfoDto.loadFromApi();
            final long diff = dto.getBlockNumber() + AionConstants.VALIDATION_BLOCKS_FOR_TRANSACTIONS - syncInfoDto.getNetworkBestBlkNumber();
//            final long diff = dto.getBlockNumber() + AionConstants.VALIDATION_BLOCKS_FOR_TRANSACTIONS - blockchainConnector.getSyncInfo().getNetworkBestBlkNumber();
            if (diff <= 0) {
                return "Finished";
            } else {
                return Math.abs(diff) + " blocks left";
            }
        }

        public String getType() {
            return type.get();
        }

        public void setType(final String type) {
            this.type.setValue(type);
        }

        public String getDate() {
            return date.get();
        }

        public void setName(final String name) {
            this.date.setValue(name);
        }

        public String getStatus() {
            return status.get();
        }

        public void setStatus(final String status) {
            this.status.setValue(status);
        }

        public String getValue() {
            return value.get();
        }

        public void setValue(final String value) {
            this.value.setValue(value);
        }

        public String getTxHash() {
            return txHash.get();
        }

        public void setHash(final String hash) {
            txHash.setValue(hash);
        }

        public TransactionDTO getTransaction() {
            return transaction;
        }
    }

    private class TransactionHashCell extends TableCell<TxRow, String> {
        @Override
        protected void updateItem(final String item, final boolean empty) {
            super.updateItem(item, empty);

            setText(empty ? "" : item);

            getStyleClass().clear();
            updateStyles(empty ? null : item);
        }

        private void updateStyles(final String item) {
            if (item == null) {
                return;
            }
            getStyleClass().add(LINK_STYLE);
        }
    }
}
