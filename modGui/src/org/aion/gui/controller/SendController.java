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

import com.google.common.eventbus.Subscribe;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import org.aion.api.impl.internal.Message;
import org.aion.base.util.TypeConverter;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.EventPublisher;
import org.aion.gui.events.HeaderPaneButtonEvent;
import org.aion.gui.events.RefreshEvent;
import org.aion.gui.model.BalanceRetriever;
import org.aion.gui.model.TransactionProcessor;
import org.aion.gui.util.AionConstants;
import org.aion.gui.util.BalanceUtils;
import org.aion.gui.util.UIUtils;
import org.aion.log.AionLoggerFactory;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.connector.dto.SendTransactionDTO;
import org.aion.wallet.connector.dto.TransactionResponseDTO;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.events.AccountEvent;
import org.aion.wallet.events.TransactionEvent;
import org.aion.wallet.exception.ValidationException;
import org.aion.wallet.ui.components.partials.TransactionResubmissionDialog;
import org.aion.wallet.util.AddressUtils;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class SendController extends AbstractController {
    private static final Logger LOGGER = AionLoggerFactory.getLogger(org.aion.log.LogEnum.GUI.name());
    private static final String PENDING_MESSAGE = "Sending transaction...";
    private static final String SUCCESS_MESSAGE = "Transaction finished";
    private static final Tooltip NRG_LIMIT_TOOLTIP = new Tooltip("NRG limit");
    private static final Tooltip NRG_PRICE_TOOLTIP = new Tooltip("NRG price");

    @FXML
    private PasswordField passwordInput;
    @FXML
    private TextField toInput;
    @FXML
    private TextField nrgInput;
    @FXML
    private TextField nrgPriceInput;
    @FXML
    private TextField valueInput;
    @FXML
    private Label txStatusLabel;
    @FXML
    private TextArea accountAddress;
    @FXML
    private TextField accountBalance;
    @FXML
    private Button sendButton;
    @FXML
    private Label timedoutTransactionsLabel;

    private BalanceRetriever balanceRetriever;
    private AccountDTO account;
    private boolean connected;
    private final TransactionResubmissionDialog transactionResubmissionDialog;
    private SendTransactionDTO transactionToResubmit;

    private final AccountManager accountManager;
    private final TransactionProcessor transactionProcessor;
    private final ConsoleManager consoleManager;

    public SendController(AccountManager accountManager,
                          TransactionProcessor transactionProcessor,
                          ConsoleManager consoleManager,
                          BalanceRetriever balanceRetriever) {
        super();
        this.accountManager = accountManager;
        this.transactionProcessor = transactionProcessor;
        this.transactionResubmissionDialog = new TransactionResubmissionDialog(
                accountManager, consoleManager);
        this.consoleManager = consoleManager;
        this.balanceRetriever = balanceRetriever;
    }

    @Override
    protected void registerEventBusConsumer() {
        super.registerEventBusConsumer();
        EventBusRegistry.INSTANCE.getBus(HeaderPaneButtonEvent.ID).register(this);
        EventBusRegistry.INSTANCE.getBus(AccountEvent.ID).register(this);
        EventBusRegistry.INSTANCE.getBus(TransactionEvent.ID).register(this);
        EventBusRegistry.INSTANCE.getBus(RefreshEvent.ID).register(this);
    }

    @Override
    protected void internalInit(final URL location, final ResourceBundle resources) {
        nrgInput.setTooltip(NRG_LIMIT_TOOLTIP);
        nrgPriceInput.setTooltip(NRG_PRICE_TOOLTIP);
        setDefaults();
//        if (!ConfigUtils.isEmbedded()) {
//            passwordInput.setVisible(false);
//            passwordInput.setManaged(false);
//        }
        // TODO don't actually have code that checks pw input right now, so don't bother showing it
        passwordInput.setVisible(false);
        passwordInput.setManaged(false);

        toInput.textProperty().addListener(event -> transactionToResubmit = null);
        nrgInput.textProperty().addListener(event -> transactionToResubmit = null);
        nrgPriceInput.textProperty().addListener(event -> transactionToResubmit = null);
        valueInput.textProperty().addListener(event -> transactionToResubmit = null);
    }

    @Override
    protected void refreshView(final RefreshEvent event) {
        switch (event.getType()) {
            case CONNECTED:
                connected = true;
                if (account != null) {
                    sendButton.setDisable(false);
                }
//                transactionProcessor.processTransactionsOnReconnect();
                break;
            case DISCONNECTED:
                connected = false;
                sendButton.setDisable(true);
                break;
            case TRANSACTION_FINISHED:
                setDefaults();
                break;
            case TIMER:
                refreshAccountBalance();
            default:
        }
        setTimedoutTransactionsLabelText();
    }

    public void onSendAionClicked() {
        if (account == null) {
            return;
        }
        final SendTransactionDTO dto;
        try {
            if(transactionToResubmit != null) {
                dto = transactionToResubmit;
            }
            else {
                dto = mapFormData();
            }
        } catch (ValidationException e) {
            LOGGER.error(e.getMessage(), e);
            displayStatus(e.getMessage(), true);
            return;
        }
        displayStatus(PENDING_MESSAGE, false);

        final Task<TransactionResponseDTO> sendTransactionTask = getApiTask(this::sendTransaction, dto);

        runApiTask(
                sendTransactionTask,
                evt -> {
                    handleTransactionFinished(sendTransactionTask.getValue());
                    sendButton.setDisable(true);
                },
                getErrorEvent(t -> Optional.ofNullable(t.getCause()).ifPresent(cause -> displayStatus(cause.getMessage(), true)), sendTransactionTask),
                getEmptyEvent()
        );
    }

    public void onTimedoutTransactionsClick(final MouseEvent mouseEvent) {
        transactionResubmissionDialog.open(mouseEvent);
    }

    private void handleTransactionFinished(final TransactionResponseDTO response) {
        setTimedoutTransactionsLabelText();
        final String error = response.getError();
        if (error != null) {
            final String failReason;
            final int responseStatus = response.getStatus();
            if (Message.Retcode.r_tx_Dropped_VALUE == responseStatus) {
                failReason = String.format("dropped: %s", error);
            } else {
                failReason = "timeout";
            }
            final String errorMessage = "Transaction " + failReason;
            consoleManager.addLog(errorMessage, ConsoleManager.LogType.TRANSACTION, ConsoleManager.LogLevel.WARNING);
            SendController.LOGGER.error("{}: {}", errorMessage, response);
            displayStatus(errorMessage, false);
        } else {
            LOGGER.info("{}: {}", SUCCESS_MESSAGE, response);
            consoleManager.addLog("Transaction sent", ConsoleManager.LogType.TRANSACTION, ConsoleManager.LogLevel.INFO);
            displayStatus(SUCCESS_MESSAGE, false);
            EventPublisher.fireTransactionFinished();
        }
    }

    private void displayStatus(final String message, final boolean isError) {
        if (isError) {
            txStatusLabel.getStyleClass().add(ERROR_STYLE);
        } else {
            txStatusLabel.getStyleClass().removeAll(ERROR_STYLE);
        }
        txStatusLabel.setText(message);
    }

    private TransactionResponseDTO sendTransaction(final SendTransactionDTO sendTransactionDTO) {
        try {
            return transactionProcessor.sendTransaction(sendTransactionDTO);
        } catch (ValidationException e) {
            throw new RuntimeException(e);
        }
    }

    private void setDefaults() {
        nrgInput.setText(AionConstants.DEFAULT_NRG);
        nrgPriceInput.setText(AionConstants.DEFAULT_NRG_PRICE.toString());

        toInput.setText("");
        valueInput.setText("");
        passwordInput.setText("");

        setTimedoutTransactionsLabelText();
    }

    private void setTimedoutTransactionsLabelText() {
        if(account != null) {
            final List<SendTransactionDTO> timedoutTransactions = accountManager.getTimedOutTransactions(account.getPublicAddress());
            if(!timedoutTransactions.isEmpty()) {
                timedoutTransactionsLabel.setVisible(true);
                timedoutTransactionsLabel.getStyleClass().add("warning-link-style");
                timedoutTransactionsLabel.setText("You have transactions that require your attention!");
            }
        }
    }

    @Subscribe
    private void handleAccountEvent(final AccountEvent event) {
        final AccountDTO account = event.getPayload();
        if (AccountEvent.Type.CHANGED.equals(event.getType())) {
            if (account.isActive()) {
                this.account = account;
                sendButton.setDisable(!connected);
                accountAddress.setText(this.account.getPublicAddress());
                accountBalance.setVisible(true);
                setAccountBalanceText();
            }
        } else if (AccountEvent.Type.LOCKED.equals(event.getType())) {
            if (account.equals(this.account)) {
                sendButton.setDisable(true);
                accountAddress.setText("");
                accountBalance.setVisible(false);
                this.account = null;
            }
        }
    }

    @Subscribe
    private void handleHeaderPaneButtonEvent(final HeaderPaneButtonEvent event) {
        if (event.getType().equals(HeaderPaneButtonEvent.Type.SEND)) {
            refreshAccountBalance();
        }
    }

    @Subscribe
    private void handleTransactionResubmitEvent(final TransactionEvent event) {
        SendTransactionDTO sendTransaction = event.getTransaction();
        sendTransaction.setNrgPrice(BigInteger.valueOf(sendTransaction.getNrgPrice() * 2));
        toInput.setText(sendTransaction.getTo());
        nrgInput.setText(sendTransaction.getNrg().toString());
        nrgPriceInput.setText(String.valueOf(sendTransaction.getNrgPrice()));
        valueInput.setText(BalanceUtils.formatBalance(sendTransaction.getValue()));
        txStatusLabel.setText("");
        timedoutTransactionsLabel.setVisible(false);
        transactionToResubmit = sendTransaction;
    }

    private void setAccountBalanceText() {
        accountBalance.setText(account.getBalance() + " " + AionConstants.CCY);
        UIUtils.setWidth(accountBalance);

    }

    private void refreshAccountBalance() {
        if (account == null) {
            return;
        }
        Task<BigInteger> getBalanceTask = getApiTask(balanceRetriever::getBalance, account.getPublicAddress());
        runApiTask(
                getBalanceTask,
                evt -> Platform.runLater(() -> {
                    account.setBalance(BalanceUtils.formatBalance(getBalanceTask.getValue()));
                    setAccountBalanceText();
                }),
                getErrorEvent(throwable -> throwable.printStackTrace(), getBalanceTask),
                getEmptyEvent()
        );
    }

    private SendTransactionDTO mapFormData() throws ValidationException {
        final SendTransactionDTO dto = new SendTransactionDTO();
        dto.setFrom(account.getPublicAddress());

        if (!AddressUtils.isValid(toInput.getText())) {
            throw new ValidationException("Address is not a valid AION address!");
        }
        dto.setTo(toInput.getText());

        try {
            final long nrg = TypeConverter.StringNumberAsBigInt(nrgInput.getText()).longValue();
            if (nrg <= 0) {
                throw new ValidationException("Nrg must be greater than 0!");
            }
            dto.setNrg(nrg);
        } catch (NumberFormatException e) {
            throw new ValidationException("Nrg must be a valid number!");
        }

        try {
            final BigInteger nrgPrice = TypeConverter.StringNumberAsBigInt(nrgPriceInput.getText());
            dto.setNrgPrice(nrgPrice);
            if (nrgPrice.compareTo(AionConstants.DEFAULT_NRG_PRICE) < 0) {
                throw new ValidationException(String.format("Nrg price must be greater than %s!", AionConstants.DEFAULT_NRG_PRICE));
            }
        } catch (NumberFormatException e) {
            throw new ValidationException("Nrg price must be a valid number!");
        }

        try {
            final BigInteger value = BalanceUtils.extractBalance(valueInput.getText());
            if (value.compareTo(BigInteger.ZERO) <= 0) {
                throw new ValidationException("Amount must be greater than 0");
            }
            dto.setValue(value);
        } catch (NumberFormatException e) {
            throw new ValidationException("Amount must be a number");
        }

        return dto;
    }
}
