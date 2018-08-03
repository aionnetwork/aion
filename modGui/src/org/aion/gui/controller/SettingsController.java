package org.aion.gui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.HeaderPaneButtonEvent;
import org.aion.gui.model.ApplyConfigResult;
import org.aion.gui.model.ConfigManipulator;
import org.aion.gui.views.XmlArea;
import org.aion.log.AionLoggerFactory;
import org.slf4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;

public class SettingsController extends AbstractController {
    private final ConfigManipulator configManip;

    @FXML
    private XmlArea xmlArea;

    private static final Logger LOGGER = AionLoggerFactory.getLogger(org.aion.log.LogEnum.GUI.name());

    public SettingsController(ConfigManipulator configManipulator) {
        this.configManip = configManipulator;
        System.out.println("Settings controller ctor");
    }

    @Override
    protected void internalInit(final URL location, final ResourceBundle resources) {
        xmlArea.setText(configManip.loadFromConfigFile());
    }

    @Override
    protected void registerEventBusConsumer() {
        EventBusRegistry.INSTANCE.getBus(HeaderPaneButtonEvent.ID).register(this);
    }

    public void resetXml(MouseEvent mouseEvent) {
        System.out.println("reset XML");
        System.out.println(configManip.getLastLoadedContent());
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
