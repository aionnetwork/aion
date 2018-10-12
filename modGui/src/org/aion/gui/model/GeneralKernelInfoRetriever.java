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