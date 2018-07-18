package org.aion.wallet.ui.components.account;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import org.aion.gui.events.EventPublisher;
import org.aion.gui.util.BalanceUtils;
import org.aion.gui.util.UIUtils;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.ui.components.partials.SaveKeystoreDialog;
import org.aion.wallet.ui.components.partials.UnlockAccountDialog;

import java.io.IOException;
import java.io.InputStream;

public class AccountCellItem extends ListCell<AccountDTO> {
    private final UnlockAccountDialog accountUnlockDialog;
    private final SaveKeystoreDialog saveKeystoreDialog;

    @FXML
    private TextField importedLabel;
    @FXML
    private HBox nameBox;
    @FXML
    private TextField name;
    @FXML
    private TextField publicAddress;
    @FXML
    private TextField balance;
    @FXML
    private ImageView accountSelectButton;
    @FXML
    private ImageView editNameButton;
    @FXML
    private ImageView accountExportButton;

    private boolean nameInEditMode;

    private static final String ICON_CONNECTED = "/org/aion/wallet/ui/components/icons/icon-connected-50.png";
    private static final String ICON_DISCONNECTED = "/org/aion/wallet/ui/components/icons/icon-disconnected-50.png";
    private static final String ICON_EDIT = "/org/aion/wallet/ui/components/icons/pencil-edit-button.png";
    private static final String ICON_CONFIRM = "/org/aion/wallet/ui/components/icons/icons8-checkmark-50.png";
    private static final String NAME_INPUT_FIELDS_SELECTED_STYLE = "name-input-fields-selected";
    private static final String NAME_INPUT_FIELDS_STYLE = "name-input-fields";
    private static final Tooltip CONNECT_ACCOUNT_TOOLTIP = new Tooltip("Connect with this account");
    private static final Tooltip CONNECTED_ACCOUNT_TOOLTIP = new Tooltip("Connected account");
    private static final Tooltip EDIT_NAME_TOOLTIP = new Tooltip("Edit account name");
    private static final Tooltip EXPORT_ACCOUNT_TOOLTIP = new Tooltip("Export to keystore");

    public AccountCellItem(UnlockAccountDialog unlockAccountDialog, SaveKeystoreDialog saveKeystoreDialog) {
        this.accountUnlockDialog = unlockAccountDialog;
        this.saveKeystoreDialog = saveKeystoreDialog;
        loadFXML();
        addToolTips();
        publicAddress.setPrefWidth(575);
    }

    private void loadFXML() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("AccountListViewItem.fxml"));
            loader.setController(this);
            loader.setRoot(this);
            loader.load();
            name.setOnKeyPressed(this::submitNameOnEnterPressed);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void addToolTips() {
        Tooltip.install(editNameButton, EDIT_NAME_TOOLTIP);
        Tooltip.install(accountExportButton, EXPORT_ACCOUNT_TOOLTIP);
    }

    private void submitNameOnEnterPressed(final KeyEvent event) {
        if (event.getCode().equals(KeyCode.ENTER)) {
            updateNameFieldOnSave();
        }
    }

    private void submitName() {
        name.setEditable(false);
        final AccountDTO accountDTO = getItem();
        accountDTO.setName(name.getText());
        updateItem(accountDTO, false);
        new EventPublisher().fireAccountChanged(accountDTO);
    }

    @Override
    protected void updateItem(AccountDTO item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setText(null);
            setContentDisplay(ContentDisplay.TEXT_ONLY);
        } else {
            name.setText(item.getName());
            UIUtils.setWidth(name);

            final ObservableList<Node> children = nameBox.getChildren();
            children.removeAll(importedLabel, name);
            if (item.isImported()) {
                children.addAll(importedLabel, name);
            } else {
                children.add(name);
            }

            publicAddress.setText(item.getPublicAddress());
            publicAddress.setPadding(new Insets(5, 0, 0, 10));

            balance.setText(item.getBalance() + BalanceUtils.CCY_SEPARATOR + item.getCurrency());
            UIUtils.setWidth(balance);

            if (item.isActive()) {
                final InputStream resource = getClass().getResourceAsStream(ICON_CONNECTED);
                accountSelectButton.setImage(new Image(resource));
                Tooltip.uninstall(accountSelectButton, CONNECT_ACCOUNT_TOOLTIP);
                Tooltip.install(accountSelectButton, CONNECTED_ACCOUNT_TOOLTIP);
            } else {
                final InputStream resource = getClass().getResourceAsStream(ICON_DISCONNECTED);
                accountSelectButton.setImage(new Image(resource));
                Tooltip.uninstall(accountSelectButton, CONNECTED_ACCOUNT_TOOLTIP);
                Tooltip.install(accountSelectButton, CONNECT_ACCOUNT_TOOLTIP);
            }

            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }
    }

    @FXML
    public void onDisconnectedClicked(final MouseEvent mouseEvent) {
        final AccountDTO modifiedAccount = this.getItem();
        if (!modifiedAccount.isUnlocked()) {
            accountUnlockDialog.open(mouseEvent);
            EventPublisher.fireAccountUnlocked(modifiedAccount);
        } else {
            modifiedAccount.setActive(true);
            EventPublisher.fireAccountChanged(modifiedAccount);
        }
    }

    @FXML
    public void onNameFieldClicked() {
        if (!nameInEditMode) {
            name.setEditable(true);
            name.getStyleClass().clear();
            name.getStyleClass().add(NAME_INPUT_FIELDS_SELECTED_STYLE);

            final InputStream resource = getClass().getResourceAsStream(ICON_CONFIRM);
            editNameButton.setImage(new Image(resource));

            name.requestFocus();
            nameInEditMode = true;
        } else {
            updateNameFieldOnSave();
        }
    }

    @FXML
    public void onExportClicked(final MouseEvent mouseEvent){
        System.out.println("AccountCellItem#onExportClicked");
        final AccountDTO account = getItem();
        if (!account.isUnlocked()) {
            accountUnlockDialog.open(mouseEvent);
            EventPublisher.fireAccountUnlocked(account);
        } else {
            saveKeystoreDialog.open(mouseEvent);
            EventPublisher.fireAccountExport(account);
        }
    }

    private void updateNameFieldOnSave() {
        if (name.getText() != null && getItem() != null && getItem().getName() != null) {
            name.getStyleClass().clear();
            name.getStyleClass().add(NAME_INPUT_FIELDS_STYLE);

            final InputStream resource = getClass().getResourceAsStream(ICON_EDIT);
            editNameButton.setImage(new Image(resource));

            name.setEditable(false);
            nameInEditMode = false;
            submitName();
        }
    }
}
