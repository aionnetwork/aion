package org.aion.gui.model;

import org.aion.api.type.ApiMsg;

public interface IApiMsgErrorHandler {
    void handleError(ApiMsg msg) throws ApiDataRetrievalException;
}
