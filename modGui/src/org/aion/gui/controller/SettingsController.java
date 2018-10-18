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

import java.net.URL;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.HeaderPaneButtonEvent;
import org.aion.gui.model.ApplyConfigResult;
import org.aion.gui.model.ConfigManipulator;
import org.aion.gui.views.XmlArea;
import org.aion.log.AionLoggerFactory;
import org.slf4j.Logger;

public class SettingsController extends AbstractController {

    private static final Logger LOGGER = AionLoggerFactory
        .getLogger(org.aion.log.LogEnum.GUI.name());
    private final ConfigManipulator configManip;
    @FXML
    private XmlArea xmlArea;
    @FXML
    private Label editingFileLabel;

    public SettingsController(ConfigManipulator configManipulator) {
        this.configManip = configManipulator;
    }

    @Override
    protected void internalInit(final URL location, final ResourceBundle resources) {
        xmlArea.setText(configManip.loadFromConfigFile());
        editingFileLabel.setText("Editing " + configManip.configFile().getAbsolutePath());
    }

    @Override
    protected void registerEventBusConsumer() {
        EventBusRegistry.INSTANCE.getBus(HeaderPaneButtonEvent.ID).register(this);
    }

    public void resetXml(MouseEvent mouseEvent) {
        Platform.runLater(() -> xmlArea.setText(configManip.getLastLoadedContent()));
    }

    public void applyAndSave(MouseEvent mouseEvent) {
        ApplyConfigResult result = configManip.applyNewConfig(xmlArea.getText());
        Alert alert = new Alert(
            (result.isSucceeded() ? Alert.AlertType.CONFIRMATION : Alert.AlertType.ERROR),
            result.getDisplayableError(),
            ButtonType.OK
        );
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.showAndWait();

        xmlArea.setText(configManip.loadFromConfigFile());
    }
}
