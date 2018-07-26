package org.aion.wallet.ui.components.partials;

import com.google.common.eventbus.Subscribe;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.aion.gui.controller.AbstractController;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.KernelProcEvent;
import org.aion.gui.events.RefreshEvent;
import org.aion.gui.model.KernelConnection;

import java.net.URL;
import java.util.ResourceBundle;

public class ConnectivityStatusController extends AbstractController {
    private KernelConnection kc;

    private static final String STATUS_DISCONNECTED = "DISCONNECTED";
    private static final String STATUS_CONNECTED = "CONNECTED";
    private static final String STATUS_CONNECTING = "CONNECTING";
    private static final String STATUS_NOT_RUNNING = "NOT RUNNING";

    @FXML
    private Label connectivityLabel;

    public ConnectivityStatusController(KernelConnection kc) {
        this.kc = kc;
    }

    @Override
    public void internalInit(final URL location, final ResourceBundle resources) {
        EventBusRegistry.INSTANCE.getBus(EventBusRegistry.KERNEL_BUS).register(this);
    }

    @Override
    protected void refreshView(final RefreshEvent event) {
        switch (event.getType()) {
            case CONNECTED:
                Platform.runLater(() -> connectivityLabel.setText(STATUS_CONNECTED));
                break;
            case DISCONNECTED:
                Platform.runLater(() -> connectivityLabel.setText(STATUS_DISCONNECTED));
                break;
        }
    }

    @Subscribe
    protected void refreshView(final KernelProcEvent.KernelLaunchedEvent ev) {
        Platform.runLater(() -> connectivityLabel.setText(STATUS_CONNECTING));
    }

    @Subscribe
    protected void refreshView(final KernelProcEvent.KernelTerminatedEvent ev) {
        Platform.runLater(() -> connectivityLabel.setText(STATUS_NOT_RUNNING));
    }
}
