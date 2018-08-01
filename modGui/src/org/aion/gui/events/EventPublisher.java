package org.aion.gui.events;

import org.aion.gui.model.KernelConnection;
import org.aion.gui.util.DataUpdater;
import org.aion.log.AionLoggerFactory;
import org.aion.os.KernelLauncher;
import org.slf4j.Logger;

public class EventPublisher {
    public static final String ACCOUNT_CHANGE_EVENT_ID = "account.changed";
//    public static final String ACCOUNT_UNLOCK_EVENT_ID = "account.unlock";
//    public static final String SETTINGS_CHANGED_ID = "settings.changed";

    private static final Logger LOG = AionLoggerFactory.getLogger(org.aion.log.LogEnum.GUI.name());

//    public static void fireAccountChanged(final AccountDTO account) {
//        if (account != null) {
//            EventBusRegistry.INSTANCE.getBus(ACCOUNT_CHANGE_EVENT_ID).post(account);
//        }
//    }
//
//    public static void fireUnlockAccount(final AccountDTO account) {
//        if (account != null) {
//            EventBusRegistry.INSTANCE.getBus(ACCOUNT_UNLOCK_EVENT_ID).post(account);
//        }
//    }

    public static void fireOperationFinished(){
        LOG.trace("EventPublisher#fireOperationFinished");
        EventBusRegistry.INSTANCE.getBus(DataUpdater.UI_DATA_REFRESH).post(new RefreshEvent(RefreshEvent.Type.OPERATION_FINISHED));
    }

//    public static void fireApplicationSettingsChanged(final LightAppSettings settings){
//        EventBusRegistry.INSTANCE.getBus(SETTINGS_CHANGED_ID).post(settings);
//    }

    public static void fireUnexpectedApiDisconnection(){
        EventBusRegistry.INSTANCE.getBus(EventBusRegistry.KERNEL_BUS)
                .post(new UnexpectedApiDisconnectedEvent());
    }
}
