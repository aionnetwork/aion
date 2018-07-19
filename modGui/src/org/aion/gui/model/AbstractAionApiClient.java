package org.aion.gui.model;

import org.aion.api.IAionAPI;
import org.aion.api.type.ApiMsg;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.slf4j.Logger;

/**
 * Provides access to an {@link IAionAPI} instance in a thread-safe manner.
 *
 * Example implementation: {@link GeneralKernelInfoRetriever}.
 */
public abstract class AbstractAionApiClient {
    private final IAionAPI api;

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GUI.name());

    /**
     * Constructor
     *
     * @param kernelConnection connection containing the API instance to interact with
     */
    protected AbstractAionApiClient(KernelConnection kernelConnection) {
        this.api = kernelConnection.getApi();
    }

    @FunctionalInterface
    protected interface ApiFunction {
        ApiMsg call(IAionAPI api);
    }

    /**
     * Call the {@link IAionAPI} in a thread-safe manner.  Specifically, call the given function
     * within a synchronization block over the underlying API object.  Intention is for subclasses
     * to use this to execute critical sections that interact with the API.
     *
     * @param func a function that calls the Aion API
     * @param throwIfError if true, throw {@link ApiDataRetrievalException} if API call returns an error
     * @return object returned by Aion API.
     */
    protected ApiMsg callApi(ApiFunction func, boolean throwIfError) {
        final ApiMsg resp;
        synchronized (api) {
            resp = func.call(api);
        }
        if(throwIfError) {
            throwAndLogIfError(resp);
        }
        return resp;
    }

    protected ApiMsg callApi(ApiFunction func) {
        return callApi(func, true);
    }

    /**
     * Log and throw if msg is in error state.  Otherwise, do nothing.
     *
     * @param msg msg
     * @throws ApiDataRetrievalException
     */
    protected void throwAndLogIfError(ApiMsg msg) throws ApiDataRetrievalException {
        if(msg.isError()) {
            String log = String.format("Error in API call.  Code = %s.  Error = %s.",
                    msg.getErrorCode(), msg.getErrString());
            LOG.error(log);
            throw new ApiDataRetrievalException(log, msg);
        }
    }

    /**
     * @return whether API is connected
     */
    protected boolean apiIsConnected() {
        return api.isConnected();
    }
}
