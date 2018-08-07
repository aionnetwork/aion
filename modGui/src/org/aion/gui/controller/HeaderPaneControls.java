package org.aion.gui.controller;

import javafx.collections.ObservableList;
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
import org.aion.gui.util.BalanceUtils;
import org.aion.gui.util.UIUtils;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

import java.awt.*;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class HeaderPaneControls extends AbstractController {

    private static final Logger log = AionLoggerFactory.getLogger(LogEnum.GUI.name());

    private static final String AION_URL = "http://www.aion.network";

    private static final String STYLE_DEFAULT = "default";

    private static final String STYLE_PRESSED = "pressed";

//    private final BlockchainConnector blockchainConnector = BlockchainConnector.getInstance();

    private final Map<Node, HeaderPaneButtonEvent> headerButtons = new HashMap<>();

    @FXML
    private TextField accountBalance;
    @FXML
    private TextField activeAccount;
    @FXML
    private Label activeAccountLabel;
    @FXML
    private VBox homeButton;
    @FXML
    private VBox settingsButton;

    private String accountAddress;

    @Override
    public void internalInit(URL location, ResourceBundle resources) {
        headerButtons.put(homeButton, new HeaderPaneButtonEvent(HeaderPaneButtonEvent.Type.OVERVIEW));
        headerButtons.put(settingsButton, new HeaderPaneButtonEvent(HeaderPaneButtonEvent.Type.SETTINGS));

        clickButton(homeButton);
    }

    @Override
    protected void registerEventBusConsumer() {
        super.registerEventBusConsumer();
        EventBusRegistry.INSTANCE.getBus(EventPublisher.ACCOUNT_CHANGE_EVENT_ID).register(this);
    }

    public void openAionWebSite() {
        try {
            Desktop.getDesktop().browse(new URI(AION_URL));
        } catch (IOException | URISyntaxException e) {
            log.error("Exception occurred trying to open website: %s", e.getMessage(), e);
        }
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
//        EventBusRegistry.getBus(HeaderPaneButtonEvent.ID).post(event);
    }

//    @Subscribe
//    private void handleAccountChanged(final AccountDTO account) {
//        accountBalance.setVisible(true);
//        activeAccountLabel.setVisible(true);
//        activeAccount.setText(account.getName());
//        accountAddress = account.getPublicAddress();
//        accountBalance.setText(account.getBalance() + BalanceUtils.CCY_SEPARATOR + account.getCurrency());
//        UIUtils.setWidth(activeAccount);
//        UIUtils.setWidth(accountBalance);
//    }

    @Override
    protected final void refreshView(final RefreshEvent event) {
//        if (accountAddress != null && !accountAddress.isEmpty()) {
//            final String[] text = accountBalance.getText().split(BalanceUtils.CCY_SEPARATOR);
//            final String currency = text[1];
//            final Task<BigInteger> getBalanceTask = getApiTask(blockchainConnector::getBalance, accountAddress);
//            runApiTask(
//                    getBalanceTask,
//                    evt -> updateNewBalance(currency, getBalanceTask.getValue()),
//                    getErrorEvent(throwable -> {}, getBalanceTask),
//                    getEmptyEvent()
//            );
//        }
    }

    private void updateNewBalance(final String currency, final BigInteger bigInteger) {
        final String newBalance = BalanceUtils.formatBalance(bigInteger) + BalanceUtils.CCY_SEPARATOR + currency;
        if (!newBalance.equalsIgnoreCase(accountBalance.getText())) {
            accountBalance.setText(newBalance);
            UIUtils.setWidth(accountBalance);
        }
    }
}
