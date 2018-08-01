package org.aion.gui.model;

import org.aion.api.IAionAPI;
import org.aion.api.type.ApiMsg;
import org.aion.log.AionLoggerFactory;
import org.slf4j.Logger;

/**
 * Provides access to an {@link IAionAPI} instance in a thread-safe manner.
 *
 * Example implementation: {@link GeneralKernelInfoRetriever}.
 */
public abstract class AbstractAionApiClient {
    private final IAionAPI api;
    private final IApiMsgErrorHandler errorHandler;

    /**
     * Constructor
     *
     * @param kernelConnection connection containing the API instance to interact with
     * @param errorHander error handler that executes whenever API call is made.  use null if no
     *                    error handling needed.
     */
    protected AbstractAionApiClient(KernelConnection kernelConnection,
                                    IApiMsgErrorHandler errorHander) {
        this.api = kernelConnection.getApi();
        this.errorHandler = errorHander;
    }

    protected AbstractAionApiClient(KernelConnection kernelConnection) {
        this(kernelConnection, null);
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
     * @return object returned by Aion API.
     * @throws ApiDataRetrievalException if error handler decides to throw it
     */
    protected ApiMsg callApi(ApiFunction func) throws ApiDataRetrievalException {
        final ApiMsg msg;
        synchronized (api) {
            msg = func.call(api);
        }
        if(errorHandler != null) {
            errorHandler.handleError(msg);
        }
        return msg;
    }

    /**
     * @return whether API is connected
     */
    protected boolean apiIsConnected() {
        return api.isConnected();
    }
}
