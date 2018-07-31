package org.aion.gui.controller.partials;

import com.google.common.eventbus.EventBus;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.WindowControlsEvent;

public class WindowControls {

    private final WindowControlsEvent closeEvent = new WindowControlsEvent(WindowControlsEvent.Type.CLOSE, null);
    private final EventBus eventBus = EventBusRegistry.INSTANCE.getBus(WindowControlsEvent.ID);

    @FXML
    private void minimize(final MouseEvent mouseEvent) {
        Node source = (Node) mouseEvent.getSource();
        final WindowControlsEvent minimizeEvent = new WindowControlsEvent(WindowControlsEvent.Type.MINIMIZE, source);
        eventBus.post(minimizeEvent);
    }

    @FXML
    private void close() {
        eventBus.post(closeEvent);
    }
}
