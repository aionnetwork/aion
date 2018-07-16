package org.aion.wallet.ui.components.partials;

import com.google.common.eventbus.Subscribe;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.input.InputEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.aion.gui.events.EventBusRegistry;
import org.aion.wallet.events.UiMessageEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class MnemonicDialog implements Initializable{
    private static final Logger LOG = org.aion.log.AionLoggerFactory
            .getLogger(org.aion.log.LogEnum.GUI.name());
    @FXML
    private TextArea mnemonicTextArea;

    public void open(InputEvent mouseEvent) {
        StackPane pane = new StackPane();
        Pane mnemonicDialog;
        try {
            mnemonicDialog = FXMLLoader.load(getClass().getResource("MnemonicDialog.fxml"));
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            return;
        }
        pane.getChildren().add(mnemonicDialog);
        Scene secondScene = new Scene(pane, mnemonicDialog.getPrefWidth(), mnemonicDialog.getPrefHeight());
        secondScene.setFill(Color.TRANSPARENT);

        Stage popup = new Stage();
        popup.setTitle("Mnemonic");
        popup.setScene(secondScene);

        Node eventSource = (Node) mouseEvent.getSource();
        popup.setX(eventSource.getScene().getWindow().getX() + eventSource.getScene().getWidth() / 2 - mnemonicDialog.getPrefWidth() / 2);
        popup.setY(eventSource.getScene().getWindow().getY() + eventSource.getScene().getHeight() / 2 - mnemonicDialog.getPrefHeight() / 2);
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.initStyle(StageStyle.TRANSPARENT);

        popup.show();
    }

    public void close(InputEvent eventSource) {
        ((Node) eventSource.getSource()).getScene().getWindow().hide();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        registerEventBusConsumer();
    }

    @Subscribe
    private void handleReceivedMnemonic(UiMessageEvent event) {
        if (UiMessageEvent.Type.MNEMONIC_CREATED.equals(event.getType())) {
            mnemonicTextArea.setText(event.getMessage());
            mnemonicTextArea.setEditable(false);
        }
    }

    private void registerEventBusConsumer() {
        EventBusRegistry.INSTANCE.getBus(UiMessageEvent.ID).register(this);
    }
}
