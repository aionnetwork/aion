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
     * @throws ApiDataRetrievalException
     */
    private void throwAndLogIfError(ApiMsg msg) throws ApiDataRetrievalException {
        if(msg.isError()) {
            String log = String.format("Error in API call.  Code = %s.  Error = %s.",
                    msg.getErrorCode(), msg.getErrString());
            LOG.error(log);
            throw new ApiDataRetrievalException(log, msg);
        }
    }

    private void notifyIfApiNotConnected(ApiMsg msg) {
        if(API_NOT_CONNECTED_ERROR == (long)msg.getErrorCode()) {
            EventPublisher.fireUnexpectedApiDisconnection();
        }
    }
}
