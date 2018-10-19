package org.aion.gui.model;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.eventbus.Subscribe;
import org.aion.api.type.ApiMsg;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.UnexpectedApiDisconnectedEvent;
import org.junit.Test;

public class SimpleApiMsgErrorHandlerTest {
    @Test
    public void testHandleError() {
        boolean gotException = false;
        Listener listner = new Listener();
        SimpleApiMsgErrorHandler unit = new SimpleApiMsgErrorHandler();
        ApiMsg msg = new ApiMsg();
        msg.set(-1003);
        try {
            unit.handleError(msg);
        } catch (ApiDataRetrievalException ex) {
            assertThat(ex.getApiMsgCode(), is(-1003));
            gotException = true;
        }
        assertThat(listner.gotDisconnect, is(true));
    }

    class Listener {
        boolean gotDisconnect = false;

        public Listener() {
            EventBusRegistry.INSTANCE.getBus(EventBusRegistry.KERNEL_BUS).register(this);
        }

        @Subscribe
        public void handleDisconnect(UnexpectedApiDisconnectedEvent ev) {
            gotDisconnect = true;
        }
    }
}
