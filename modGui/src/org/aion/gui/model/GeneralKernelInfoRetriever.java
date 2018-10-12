package org.aion.gui.model;

import java.util.List;
import org.aion.api.type.ApiMsg;
import org.aion.gui.model.dto.SyncInfoDto;
import org.aion.log.AionLoggerFactory;
import org.slf4j.Logger;

/**
 * Class contains methods for retrieving info from the API that is too simple to warrant its own DTO
 * class, i.e. {@link SyncInfoDto}.
 */
public class GeneralKernelInfoRetriever extends AbstractAionApiClient {

    private static final Logger LOG = AionLoggerFactory.getLogger(org.aion.log.LogEnum.GUI.name());

    public GeneralKernelInfoRetriever(KernelConnection kernelConnection) {
        this(kernelConnection, SimpleApiMsgErrorHandler.INSTANCE);
    }

    public GeneralKernelInfoRetriever(KernelConnection kernelConnection,
        IApiMsgErrorHandler errorHandler) {
        super(kernelConnection, errorHandler);
    }

    public Boolean isMining() throws ApiDataRetrievalException {
        if (apiIsConnected()) {
            ApiMsg resp = callApi(api -> api.getMine().isMining());
            return (boolean) resp.getObject();
        } else {
            return null;
        }
    }

    public Integer getPeerCount() throws ApiDataRetrievalException {
        if (apiIsConnected()) {
            ApiMsg resp = callApi(api -> api.getNet().getActiveNodes());
            return ((List) resp.getObject()).size();
        } else {
            return null;
        }
    }
}