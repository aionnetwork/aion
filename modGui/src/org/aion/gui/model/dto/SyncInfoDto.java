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

package org.aion.gui.model.dto;

import org.aion.api.type.ApiMsg;
import org.aion.api.type.SyncInfo;
import org.aion.gui.model.ApiDataRetrievalException;
import org.aion.gui.model.IApiMsgErrorHandler;
import org.aion.gui.model.KernelConnection;
import org.aion.gui.model.SimpleApiMsgErrorHandler;
import org.aion.log.AionLoggerFactory;
import org.slf4j.Logger;

public class SyncInfoDto extends AbstractDto {
    private long chainBestBlkNumber;
    private long networkBestBlkNumber;

    private static final Logger LOG = AionLoggerFactory.getLogger(org.aion.log.LogEnum.GUI.name());

    /**
     * Constructor
     *
     * @param kernelConnection connection containing the API instance to interact with
     */
    public SyncInfoDto(KernelConnection kernelConnection) {
        super(kernelConnection, SimpleApiMsgErrorHandler.INSTANCE);
    }

    /**
     * Constructor
     *
     * @param kernelConnection connection containing the API instance to interact with
     */
    public SyncInfoDto(KernelConnection kernelConnection, IApiMsgErrorHandler handler) {
        super(kernelConnection, handler);
    }

    public long getNetworkBestBlkNumber() {
        return networkBestBlkNumber;
    }

    public void setNetworkBestBlkNumber(long networkBestBlkNumber) {
        this.networkBestBlkNumber = networkBestBlkNumber;
    }

    public long getChainBestBlkNumber() {
        return chainBestBlkNumber;
    }

    public void setChainBestBlkNumber(long chainBestBlkNumber) {
        this.chainBestBlkNumber = chainBestBlkNumber;
    }

    public void loadFromApiInternal() throws ApiDataRetrievalException {
        Long chainBest;
        long netBest;
        SyncInfo syncInfo;
        if (!apiIsConnected()) {
            LOG.warn("Tried to call API, but API is not connected, so aborting the call");
            return;
        }
        try {
            ApiMsg msg = callApi(api -> api.getNet().syncInfo());
            syncInfo = msg.getObject();
            chainBest = syncInfo.getChainBestBlock();
            netBest = syncInfo.getNetworkBestBlock();
        } catch (Exception e) {
            chainBest = getLatest();
            netBest = chainBest;
        }

        setChainBestBlkNumber(chainBest);
        setNetworkBestBlkNumber(netBest);
    }

    private Long getLatest() throws ApiDataRetrievalException {
        if (!apiIsConnected()) {
            return 0l;
        } else {
            ApiMsg msg = callApi(api -> api.getChain().blockNumber());
            return msg.getObject();
        }
    }
}
