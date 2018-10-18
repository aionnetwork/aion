package org.aion.gui.model;

import com.google.common.base.Preconditions;
import org.aion.api.type.ApiMsg;

public class ApiDataRetrievalException extends RuntimeException {
    private final int apiMsgCode;
    private final String apiMsgString;

    public ApiDataRetrievalException(String message, ApiMsg apiMsg) {
        super(message);
        Preconditions.checkArgument(apiMsg.isError());
        apiMsgCode = apiMsg.getErrorCode();
        apiMsgString = apiMsg.getErrString();
    }

    public int getApiMsgCode() {
        return apiMsgCode;
    }

    public String getApiMsgString() {
        return apiMsgString;
    }
}
