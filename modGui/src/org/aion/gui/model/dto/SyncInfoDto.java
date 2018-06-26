package org.aion.gui.model.dto;

import org.aion.api.type.ApiMsg;
import org.aion.api.type.SyncInfo;
import org.aion.gui.model.ApiDataRetrievalException;
import org.aion.gui.model.KernelConnection;
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
        super(kernelConnection);
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


    public void loadFromApiInternal() throws ApiDataRetrievalException  {
        Long chainBest;
        long netBest;
        SyncInfo syncInfo;
        try {
            ApiMsg msg = callApi(api -> api.getNet().syncInfo());
            throwAndLogIfError(msg);
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
        if(!apiIsConnected()) {
            return 0l;
        } else {
            ApiMsg msg = callApi(api -> api.getChain().blockNumber());
            throwAndLogIfError(msg);
            return msg.getObject();
        }
    }
}
