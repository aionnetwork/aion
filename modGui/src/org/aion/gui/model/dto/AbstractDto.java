package org.aion.gui.model.dto;

import org.aion.gui.model.AbstractAionApiClient;
import org.aion.gui.model.ApiDataRetrievalException;
import org.aion.gui.model.IApiMsgErrorHandler;
import org.aion.gui.model.KernelConnection;

public abstract class AbstractDto extends AbstractAionApiClient {

    /**
     * Constructor
     *
     * @param kernelConnection connection containing the API instance to interact with
     */
    protected AbstractDto(KernelConnection kernelConnection, IApiMsgErrorHandler errorHandler) {
        super(kernelConnection, errorHandler);
    }

    /**
     * Populate this DTO by calling the Aion API.
     *
     * @throws ApiDataRetrievalException if data could not be retrieved
     * @returns null
     */
    public Void loadFromApi() throws ApiDataRetrievalException {
        loadFromApiInternal();
        return null;
    }

    abstract protected void loadFromApiInternal();
}
