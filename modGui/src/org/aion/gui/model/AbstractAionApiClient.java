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

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GUI.name());
    private final IAionAPI api;
    private final IApiMsgErrorHandler errorHandler;

    /**
     * Constructor
     *
     * @param kernelConnection connection containing the API instance to interact with
     * @param errorHander error handler that executes whenever API call is made.  use null if no
     * error handling needed.
     */
    protected AbstractAionApiClient(KernelConnection kernelConnection,
        IApiMsgErrorHandler errorHander) {
        this.api = kernelConnection.getApi();
        this.errorHandler = errorHander;
    }

    protected AbstractAionApiClient(KernelConnection kernelConnection) {
        this(kernelConnection, null);
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
        if (errorHandler != null) {
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

    @FunctionalInterface
    protected interface ApiFunction {

        ApiMsg call(IAionAPI api);
    }
}
