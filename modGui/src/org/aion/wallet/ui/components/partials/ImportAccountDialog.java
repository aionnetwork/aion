package org.aion.wallet.ui.components.partials;

import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.aion.base.util.Hex;
import org.aion.gui.controller.ControllerFactory;
import org.aion.gui.events.EventPublisher;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.dto.AccountDTO;
import org.aion.wallet.exception.ValidationException;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ResourceBundle;

public class ImportAccountDialog implements Initializable {
    private final AccountManager accountManager;
    private final ConsoleManager consoleManager;

    @FXML
    public TextField privateKeyInput;
    @FXML
    private PasswordField privateKeyPassword;
    @FXML
    private TextField keystoreTextView;
    @FXML
    private PasswordField keystorePassword;
    @FXML
    private RadioButton privateKeyRadioButton;
    @FXML
    private RadioButton keystoreRadioButton;
    @FXML
    private ToggleGroup accountTypeToggleGroup;
    @FXML
    private VBox importKeystoreView;
    @FXML
    private VBox importPrivateKeyView;
    @FXML
    private CheckBox rememberAccount;
    @FXML
    private Label validationError;

    private byte[] keystoreFile;

    private static final Logger LOG = org.aion.log.AionLoggerFactory
            .getLogger(org.aion.log.LogEnum.GUI.name());

    private static final String PK_RADIO_BUTTON_ID = "PK_RB";

    private static final String KEYSTORE_RADIO_BUTTON_ID = "KEYSTORE_RB";

    public ImportAccountDialog(AccountManager accountManager,
                               ConsoleManager consoleManager) {
        this.accountManager = accountManager;
        this.consoleManager = consoleManager;
    }

    public void uploadKeystoreFile() throws IOException {
        resetValidation();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open UTC Keystore File");
        File file = fileChooser.showOpenDialog(null);
        if (file == null) {
            return;
        }
        keystoreTextView.setText(file.getName());
        keystorePassword.requestFocus();
        keystoreFile = Files.readAllBytes(file.toPath());
    }

    public void importAccount(InputEvent eventSource) {
        AccountDTO account = null;
        final boolean shouldKeep = rememberAccount.isSelected();
        if (importKeystoreView.isVisible()) {
            account = getAccountFromKeyStore(shouldKeep);
        } else if (importPrivateKeyView.isVisible()) {
            account = getAccountFromPrivateKey(shouldKeep);
        }

        if (account != null) {
            new EventPublisher().fireAccountChanged(account);
            this.close(eventSource);
        }
    }

    private AccountDTO getAccountFromKeyStore(final boolean shouldKeep) {
        String password = keystorePassword.getText();
        if (!password.isEmpty() && keystoreFile != null) {
            try {
                AccountDTO dto = accountManager.importKeystore(keystoreFile, password, shouldKeep);
                consoleManager.addLog("Keystore imported", ConsoleManager.LogType.ACCOUNT);
                return dto;
            } catch (final ValidationException e) {
                consoleManager.addLog("Keystore could not be imported", ConsoleManager.LogType.ACCOUNT, ConsoleManager.LogLevel.WARNING);
                LOG.error(e.getMessage(), e);
                displayError(e.getMessage());
                return null;
            }
        } else {
            displayError("Please complete the fields!");
            return null;
        }
    }

    private AccountDTO getAccountFromPrivateKey(final boolean shouldKeep) {
        String password = privateKeyPassword.getText();
        String privateKey = privateKeyInput.getText();
        if (password != null && !password.isEmpty() && privateKey != null && !privateKey.isEmpty()) {
            byte[] raw = Hex.decode(privateKey.startsWith("0x") ? privateKey.substring(2) : privateKey);
            if (raw == null) {
                final String errorMessage = "Invalid private key: " + privateKey;
                LOG.error(errorMessage);
                displayError(errorMessage);
                return null;
            }
            try {
                AccountDTO dto = accountManager.importPrivateKey(raw, password, shouldKeep);
                consoleManager.addLog("Private key imported", ConsoleManager.LogType.ACCOUNT);
                return dto;
            } catch (ValidationException e) {
                consoleManager.addLog("Private key could not be imported", ConsoleManager.LogType.ACCOUNT, ConsoleManager.LogLevel.WARNING);
                LOG.error(e.getMessage(), e);
                displayError(e.getMessage());
                return null;
            }
        } else {
            displayError("Please complete the fields!");
            return null;
        }
    }

    private void displayError(final String message) {
        validationError.setText(message);
        validationError.setVisible(true);
    }

    public void open(MouseEvent mouseEvent) {
        StackPane pane = new StackPane();
        Pane importAccountDialog;
        try {
            FXMLLoader loader = new FXMLLoader((getClass().getResource("ImportAccountDialog.fxml")));
            loader.setControllerFactory(new ControllerFactory().withAccountManager(accountManager).withConsoleManager(consoleManager) /* TODO a specialization only has what we need */);
            importAccountDialog = loader.load();
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            return;
        }
        pane.getChildren().add(importAccountDialog);
        Scene secondScene = new Scene(pane, importAccountDialog.getPrefWidth(), importAccountDialog.getPrefHeight());
        secondScene.setFill(Color.TRANSPARENT);

        Stage popup = new Stage();
        popup.setTitle("Import account");
        popup.setScene(secondScene);

        Node eventSource = (Node) mouseEvent.getSource();
        popup.setX(eventSource.getScene().getWindow().getX() + eventSource.getScene().getWidth() / 2 - importAccountDialog.getPrefWidth() / 2);
        popup.setY(eventSource.getScene().getWindow().getY() + eventSource.getScene().getHeight() / 2 - importAccountDialog.getPrefHeight() / 2);
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.initStyle(StageStyle.TRANSPARENT);

        popup.show();
    }

    public void close(InputEvent eventSource) {
        ((Node) eventSource.getSource()).getScene().getWindow().hide();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        privateKeyRadioButton.setUserData(PK_RADIO_BUTTON_ID);
        keystoreRadioButton.setUserData(KEYSTORE_RADIO_BUTTON_ID);
        accountTypeToggleGroup.selectedToggleProperty().addListener(this::radioButtonChanged);
    }

    @FXML
    private void submitOnEnterPressed(final KeyEvent event) {
        if (event.getCode().equals(KeyCode.ENTER)) {
            importAccount(event);
        }
    }

    public void resetValidation() {
        validationError.setVisible(false);
    }

    private void radioButtonChanged(ObservableValue<? extends Toggle> ov, Toggle oldToggle, Toggle newToggle) {
        if (accountTypeToggleGroup.getSelectedToggle() != null) {
            switch ((String) accountTypeToggleGroup.getSelectedToggle().getUserData()) {
                case PK_RADIO_BUTTON_ID:
                    importPrivateKeyView.setVisible(true);
                    importKeystoreView.setVisible(false);
                    break;
                case KEYSTORE_RADIO_BUTTON_ID:
                    importPrivateKeyView.setVisible(false);
                    importKeystoreView.setVisible(true);
                    break;
            }
        }
    }
}
