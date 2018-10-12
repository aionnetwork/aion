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