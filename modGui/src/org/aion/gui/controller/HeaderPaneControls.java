package org.aion.gui.controller;

import com.google.common.eventbus.Subscribe;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.EventPublisher;
import org.aion.gui.events.HeaderPaneButtonEvent;
import org.aion.gui.events.RefreshEvent;
import org.aion.gui.model.dto.BalanceDto;
import org.aion.gui.util.BalanceUtils;
import org.aion.gui.util.SyncStatusFormatter;
import org.aion.gui.util.UIUtils;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.events.AccountEvent;
import org.aion.wallet.util.URLManager;
import org.slf4j.Logger;

import java.awt.*;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class HeaderPaneControls extends AbstractController {

    private static final Logger log = AionLoggerFactory.getLogger(LogEnum.GUI.name());

    private static final String STYLE_DEFAULT = "default";

    private static final String STYLE_PRESSED = "pressed";

    private final BalanceDto balanceDto;

    private final Map<Node, HeaderPaneButtonEvent> headerButtons = new HashMap<>();

    public HeaderPaneControls(BalanceDto balanceDto) {
        this.balanceDto = balanceDto;
    }

    @FXML
    private TextField accountBalance;
    @FXML
    private TextField activeAccount;
    @FXML
    private Label activeAccountLabel;
    @FXML
    private VBox homeButton;
    @FXML
    private VBox accountsButton;
    @FXML
    private VBox settingsButton;

    private String accountAddress;

    @Override
    public void internalInit(URL location, ResourceBundle resources) {
        headerButtons.put(homeButton, new HeaderPaneButtonEvent(HeaderPaneButtonEvent.Type.OVERVIEW));
        headerButtons.put(accountsButton, new HeaderPaneButtonEvent(HeaderPaneButtonEvent.Type.ACCOUNTS));
        headerButtons.put(settingsButton, new HeaderPaneButtonEvent(HeaderPaneButtonEvent.Type.SETTINGS));

        clickButton(homeButton);
    }

    @Override
    protected void registerEventBusConsumer() {
        super.registerEventBusConsumer();
        EventBusRegistry.INSTANCE.getBus(AccountEvent.ID).register(this);
    }

    public void openAionWebSite() {
        URLManager.openDashboard();
    }

    public void handleButtonPressed(final MouseEvent pressed) {
        for (final Node headerButton : headerButtons.keySet()) {
            ObservableList<String> styleClass = headerButton.getStyleClass();
            styleClass.clear();
            if (pressed.getSource().equals(headerButton)) {
                styleClass.add(STYLE_PRESSED);
                setStyleToChildren(headerButton, "header-button-label-pressed");
                HeaderPaneButtonEvent headerPaneButtonEvent = headerButtons.get(headerButton);
                sendPressedEvent(headerPaneButtonEvent);
            } else {
                styleClass.add(STYLE_DEFAULT);
                setStyleToChildren(headerButton, "header-button-label");
            }
        }
    }

    private void setStyleToChildren(Node headerButton, String styleClassToSet) {
        VBox vbox = (VBox) ((VBox) headerButton).getChildren().get(0);
        ObservableList<String> styleClass = vbox.getChildren().get(0).getStyleClass();
        styleClass.clear();
        styleClass.add(styleClassToSet);
    }

    private void clickButton(final Node button) {
        final double layoutX = button.getLayoutX();
        final double layoutY = button.getLayoutY();
        final MouseEvent clickOnButton = new MouseEvent(MouseEvent.MOUSE_CLICKED,
                layoutX, layoutY, layoutX, layoutY, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null);
        Event.fireEvent(button, clickOnButton);
    }

    private void sendPressedEvent(final HeaderPaneButtonEvent event) {
        EventBusRegistry.INSTANCE.getBus(HeaderPaneButtonEvent.ID).post(event);
    }

    @Subscribe
    private void handleAccountEvent(final AccountEvent event) {
        final AccountDTO account = event.getPayload();
        if (EnumSet.of(AccountEvent.Type.CHANGED, AccountEvent.Type.ADDED).contains(event.getType())) {
            if (account.isActive()) {
                accountBalance.setText(account.getBalance() + BalanceUtils.CCY_SEPARATOR + account.getCurrency());
                accountBalance.setVisible(true);
                activeAccount.setText(account.getName());
                activeAccountLabel.setVisible(true);
                accountAddress = account.getPublicAddress();
                UIUtils.setWidth(activeAccount);
                UIUtils.setWidth(accountBalance);
            }
        } else if (AccountEvent.Type.LOCKED.equals(event.getType())){
            if (account.getPublicAddress().equals(accountAddress)){
                accountAddress = "";
                activeAccountLabel.setVisible(false);
                accountBalance.setVisible(false);
                activeAccount.setText("");
            }
        }
    }

    @Override
    protected final void refreshView(final RefreshEvent event) {
        if (!accountAddress.isEmpty()) {
            final String[] text = accountBalance.getText().split(BalanceUtils.CCY_SEPARATOR);
            final String currency = text[1];

            Task<Void> getSyncInfoTask = getApiTask(o -> {
                balanceDto.setAddress(accountAddress); // TODO ugly
                balanceDto.loadFromApi();
                return null;
            }, null);
            runApiTask(
                    getSyncInfoTask,
                    evt -> Platform.runLater(() -> updateNewBalance(currency, balanceDto.getBalance())),
                    getErrorEvent(throwable -> {}, getSyncInfoTask),
                    getEmptyEvent()
            );
//            final Task<BigInteger> getBalanceTask = getApiTask(blockchainConnector::getBalance, accountAddress);
//            runApiTask(
//                    getBalanceTask,
//                    evt -> updateNewBalance(currency, getBalanceTask.getValue()),
//                    getErrorEvent(t -> {}, getBalanceTask),
//                    getEmptyEvent()
//            );
        }
    }

    private void updateNewBalance(final String currency, final BigInteger bigInteger) {
        final String newBalance = BalanceUtils.formatBalance(bigInteger) + BalanceUtils.CCY_SEPARATOR + currency;
        if (!newBalance.equalsIgnoreCase(accountBalance.getText())) {
            accountBalance.setText(newBalance);
            UIUtils.setWidth(accountBalance);
        }
    }
}
