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

import org.aion.api.type.ApiMsg;
import org.aion.gui.events.EventPublisher;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

public class SimpleApiMsgErrorHandler implements IApiMsgErrorHandler {

    public static final SimpleApiMsgErrorHandler INSTANCE = new SimpleApiMsgErrorHandler();

    private static final long API_NOT_CONNECTED_ERROR = -1003L; // as defined by org.aion.api.impl.ErrId
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GUI.name());

    @Override
    public void handleError(ApiMsg msg) throws ApiDataRetrievalException {
        notifyIfApiNotConnected(msg);
        throwAndLogIfError(msg);
    }

    /**
     * Log and throw if msg is in error state.  Otherwise, do nothing.
     *
     * @param msg msg
     */
    private void throwAndLogIfError(ApiMsg msg) throws ApiDataRetrievalException {
        if (msg.isError()) {
            String log = String.format("Error in API call.  Code = %s.  Error = %s.",
                msg.getErrorCode(), msg.getErrString());
            LOG.error(log);
            throw new ApiDataRetrievalException(log, msg);
        }
    }

    private void notifyIfApiNotConnected(ApiMsg msg) {
        if (API_NOT_CONNECTED_ERROR == (long) msg.getErrorCode()) {
            new EventPublisher().fireUnexpectedApiDisconnection();
        }
    }
}
