package org.aion.wallet.ui.components.partials;

import io.github.novacrypto.bip39.MnemonicValidator;
import io.github.novacrypto.bip39.Validation.InvalidChecksumException;
import io.github.novacrypto.bip39.Validation.InvalidWordCountException;
import io.github.novacrypto.bip39.Validation.UnexpectedWhiteSpaceException;
import io.github.novacrypto.bip39.Validation.WordNotFoundException;
import io.github.novacrypto.bip39.wordlists.English;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Popup;
import org.aion.gui.controller.ControllerFactory;
import org.aion.gui.events.EventPublisher;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.exception.ValidationException;
import org.slf4j.Logger;

import java.io.IOException;

public class AddAccountDialog {
    private final MnemonicDialog mnemonicDialog = new MnemonicDialog();
    private final AccountManager accountManager;
    private final ConsoleManager consoleManager;

    @FXML
    public TextField mnemonicTextField;
    @FXML
    public PasswordField mnemonicPasswordField;
    @FXML
    private TextField newAccountName;
    @FXML
    private PasswordField newPassword;
    @FXML
    private PasswordField retypedPassword;
    @FXML
    private Label validationError;

    private static final Logger LOG = org.aion.log.AionLoggerFactory
            .getLogger(org.aion.log.LogEnum.GUI.name());

    public AddAccountDialog(AccountManager accountManager,
                            ConsoleManager consoleManager) {
       this.accountManager = accountManager;
       this.consoleManager = consoleManager;
    }

    public void createAccount(final InputEvent mouseEvent) {
        resetValidation();

        if (!validateFields()) {
            String error = "";
            if (newPassword.getText().isEmpty() || retypedPassword.getText().isEmpty()) {
                error = "Please complete the fields!";
            } else if (!newPassword.getText().equals(retypedPassword.getText())) {
                error = "Passwords don't match!";
            }
            showInvalidFieldsError(error);
            return;
        }

        try {
//            String mnemonic = blockchainConnector.createMasterAccount(newPassword.getText(), newAccountName.getText());
            String mnemonic = accountManager.createMasterAccount(newPassword.getText(), newAccountName.getText());
            consoleManager.addLog("Master account created -> name: " + newAccountName.getText(), ConsoleManager.LogType.ACCOUNT);


            if (mnemonic != null) {
                mnemonicDialog.open(mouseEvent);
                EventPublisher.fireMnemonicCreated(mnemonic);
            }
        } catch (ValidationException e) {
            consoleManager.addLog("Master account could not be created", ConsoleManager.LogType.ACCOUNT, ConsoleManager.LogLevel.WARNING);
            showInvalidFieldsError(e.getMessage());
        }
    }

    public void importMnemonic(final InputEvent mouseEvent) {
        final String mnemonic = mnemonicTextField.getText();
        final String mnemonicPassword = mnemonicPasswordField.getText();
        if (mnemonic != null && !mnemonic.isEmpty() && mnemonicPassword != null && !mnemonicPassword.isEmpty()) {
            try {
                MnemonicValidator
                        .ofWordList(English.INSTANCE)
                        .validate(mnemonic);
                accountManager.importMasterAccount(mnemonic, mnemonicPassword);
                consoleManager.addLog("Master account imported", ConsoleManager.LogType.ACCOUNT);
                this.close(mouseEvent);
            } catch (UnexpectedWhiteSpaceException | InvalidWordCountException | InvalidChecksumException | WordNotFoundException | ValidationException e) {
                consoleManager.addLog("Could not import master account", ConsoleManager.LogType.ACCOUNT, ConsoleManager.LogLevel.WARNING);
                showInvalidFieldsError(getMnemonicValidationErrorMessage(e));
                LOG.error(e.getMessage(), e);
            }
        } else {
            showInvalidFieldsError("Please complete the fields!");
        }
    }

    private String getMnemonicValidationErrorMessage(Exception e) {
        if (e instanceof UnexpectedWhiteSpaceException) {
            return "There are spaces in the mnemonic!";
        } else if (e instanceof InvalidWordCountException) {
            return "Mnemonic word length is invalid!";
        } else if (e instanceof InvalidChecksumException) {
            return "Invalid mnemonic!";
        } else if (e instanceof WordNotFoundException) {
            return "Word in mnemonic was not found!";
        } else return e.getMessage();
    }

    private boolean validateFields() {
        if (newPassword == null || newPassword.getText() == null || retypedPassword == null || retypedPassword.getText() == null) {
            return false;
        }

        return newPassword.getText() != null && !newPassword.getText().isEmpty()
                && retypedPassword.getText() != null && !retypedPassword.getText().isEmpty()
                && newPassword.getText().equals(retypedPassword.getText());
    }

    public void resetValidation() {
        validationError.setVisible(false);
    }

    private void showInvalidFieldsError(final String message) {
        validationError.setVisible(true);
        validationError.setText(message);
    }

    public void open(final MouseEvent mouseEvent) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);

        Pane addAccountDialog;
        try {

//            addAccountDialog = FXMLLoader.load(getClass().getResource("AddAccountDialog.fxml"));
            FXMLLoader loader = new FXMLLoader((getClass().getResource("AddAccountDialog.fxml")));
            loader.setControllerFactory(new ControllerFactory().withAccountManager(accountManager) /* TODO a specialization only has what we need */);
            addAccountDialog = loader.load();
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            return;
        }

        Node eventSource = (Node) mouseEvent.getSource();
        final double windowX = eventSource.getScene().getWindow().getX();
        final double windowY = eventSource.getScene().getWindow().getY();
        popup.setX(windowX + eventSource.getScene().getWidth() / 2 - addAccountDialog.getPrefWidth() / 2);
        popup.setY(windowY + eventSource.getScene().getHeight() / 2 - addAccountDialog.getPrefHeight() / 2);
        popup.getContent().addAll(addAccountDialog);
        popup.show(eventSource.getScene().getWindow());
    }

    public void close(final InputEvent eventSource) {
        ((Node) eventSource.getSource()).getScene().getWindow().hide();
    }

    @FXML
    private void submitCreate(final KeyEvent event) {
        if (event.getCode().equals(KeyCode.ENTER)) {
            createAccount(event);
        }
    }

    @FXML
    private void submitImport(final KeyEvent event) {
        if (event.getCode().equals(KeyCode.ENTER)) {
            importMnemonic(event);
        }
    }
}
