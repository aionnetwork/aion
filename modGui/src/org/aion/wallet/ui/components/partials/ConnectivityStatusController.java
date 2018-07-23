package org.aion.wallet.ui.components.partials;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.aion.gui.controller.AbstractController;
import org.aion.gui.events.RefreshEvent;
import org.aion.gui.model.KernelConnection;

import java.net.URL;
import java.util.EnumSet;
import java.util.ResourceBundle;

public class ConnectivityStatusController extends AbstractController {
    private KernelConnection kc;

    private static final String CONNECTIVITY_STATUS_CONNECTED = "CONNECTED";
    private static final String CONNECTIVITY_STATUS_DISCONNECTED = "DISCONNECTED";

    @FXML
    private Label connectivityLabel;

    public ConnectivityStatusController(KernelConnection kc) {
        this.kc = kc;
    }

    @Override
    public void internalInit(final URL location, final ResourceBundle resources) {
    }

    @Override
    protected final void refreshView(final RefreshEvent event) {
        if (EnumSet.of(RefreshEvent.Type.TIMER, RefreshEvent.Type.CONNECTED).contains(event.getType())) {
//            final Task<Boolean> getConnectedStatusTask = getApiTask(o -> blockchainConnector.getConnectionStatus(), null);
            final Task<Boolean> getConnectedStatusTask = getApiTask(o -> kc.isConnected(), null);
            runApiTask(
                    getConnectedStatusTask,
                    evt -> setConnectivityLabel(getConnectedStatusTask.getValue()),
                    getErrorEvent(t -> {}, getConnectedStatusTask),
                    getEmptyEvent());
        }
    }

    private void setConnectivityLabel(boolean connected) {
        if (connected) {
            connectivityLabel.setText(CONNECTIVITY_STATUS_CONNECTED);
        } else {
            connectivityLabel.setText(CONNECTIVITY_STATUS_DISCONNECTED);
        }
    }
}
