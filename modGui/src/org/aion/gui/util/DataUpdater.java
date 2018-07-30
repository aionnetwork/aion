package org.aion.gui.util;

import com.google.common.eventbus.EventBus;
import javafx.application.Platform;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.RefreshEvent;
import org.aion.log.AionLoggerFactory;
import org.slf4j.Logger;

import java.util.TimerTask;

public class DataUpdater extends TimerTask {
    private static final Logger LOG = AionLoggerFactory.getLogger(org.aion.log.LogEnum.GUI.name());

    public static final String UI_DATA_REFRESH = "gui.data_refresh";

    private final EventBus eventBus = EventBusRegistry.INSTANCE.getBus(UI_DATA_REFRESH);

    @Override
    public void run() {
        Platform.runLater(() -> {
            eventBus.post(new RefreshEvent(RefreshEvent.Type.TIMER));
        });

    }
}
