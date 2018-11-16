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

import com.google.common.eventbus.Subscribe;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
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

public class MnemonicDialog implements Initializable {
    private static final Logger LOG =
            org.aion.log.AionLoggerFactory.getLogger(org.aion.log.LogEnum.GUI.name());
    @FXML private TextArea mnemonicTextArea;

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
        Scene secondScene =
                new Scene(pane, mnemonicDialog.getPrefWidth(), mnemonicDialog.getPrefHeight());
        secondScene.setFill(Color.TRANSPARENT);

        Stage popup = new Stage();
        popup.setTitle("Mnemonic");
        popup.setScene(secondScene);

        Node eventSource = (Node) mouseEvent.getSource();
        popup.setX(
                eventSource.getScene().getWindow().getX()
                        + eventSource.getScene().getWidth() / 2
                        - mnemonicDialog.getPrefWidth() / 2);
        popup.setY(
                eventSource.getScene().getWindow().getY()
                        + eventSource.getScene().getHeight() / 2
                        - mnemonicDialog.getPrefHeight() / 2);
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
