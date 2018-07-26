package org.aion.gui.model.dto;

import org.aion.api.IAionAPI;
import org.aion.api.IChain;
import org.aion.api.INet;
import org.aion.api.type.ApiMsg;
import org.aion.api.type.SyncInfo;
import org.aion.gui.model.KernelConnection;
import org.aion.gui.model.KernelConnectionMockSetter;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SyncInfoDtoTest {
    private IAionAPI api;
    private KernelConnection kernelConnection;
    private ApiMsg msg;
    private SyncInfoDto unit;
    private IChain chain;


    @Before
    public void before() {
        kernelConnection = mock(KernelConnection.class);
        api = mock(IAionAPI.class);
        KernelConnectionMockSetter.setApiOfMockKernelConnection(kernelConnection, api);

        INet net = mock(INet.class);
        when(api.getNet()).thenReturn(net);
        when(net.syncInfo()).thenReturn(msg);

        chain = mock(IChain.class);
        when(api.getChain()).thenReturn(chain);

        msg = new ApiMsg();
        when(net.syncInfo()).thenReturn(msg);
        unit = new SyncInfoDto(kernelConnection, null /*IApiMsgErrorHandler*/);
    }

    @Test
    public void testGettersSetters() {
        long chainBest = 892231;
        long networkBest = 102910;
        unit.setChainBestBlkNumber(chainBest);
        unit.setNetworkBestBlkNumber(networkBest);
        assertThat(unit.getChainBestBlkNumber(), is(chainBest));
        assertThat(unit.getNetworkBestBlkNumber(), is(networkBest));
    }

    @Test
    public void testLoadFromApiInternalWhenGetSyncInfoSucceeds() {
        boolean syncing = true;
        long chainBest = 792531;
        long networkBest = 1141;

        SyncInfo syncInfo = new SyncInfo(syncing, networkBest, chainBest, 7 /*not used*/, 1 /* not used */);
        msg.set(syncInfo, ApiMsg.cast.OTHERS);
        when(api.isConnected()).thenReturn(true);
        unit = new SyncInfoDto(kernelConnection);

        unit.loadFromApiInternal();
        assertThat(unit.getChainBestBlkNumber(), is(chainBest));
        assertThat(unit.getNetworkBestBlkNumber(), is(networkBest));
    }

    @Test
    public void testLoadFromApiInternalWhenFallBackToLatestBlock() {
        when(api.isConnected()).thenReturn(true);
        msg.set(0 /*signal for error*/, null, ApiMsg.cast.NULL);


        long blockNum = 20531;
        ApiMsg secondMsg = new ApiMsg();
        when(chain.blockNumber()).thenReturn(secondMsg);
        secondMsg.set(Long.valueOf(blockNum), ApiMsg.cast.LONG);

        unit = new SyncInfoDto(kernelConnection, null /*IApiMsgErrorHandler*/);
        unit.loadFromApiInternal();

        assertThat(unit.getNetworkBestBlkNumber(), is(blockNum));
        assertThat(unit.getChainBestBlkNumber(), is(blockNum));
    }
}
