package org.aion.gui.model;

import org.aion.api.IAionAPI;
import org.aion.api.IMine;
import org.aion.api.INet;
import org.aion.api.type.ApiMsg;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeneralKernelInfoRetrieverTest {
    private ApiMsg apiMsgWithError;

    @Before
    public void before() throws Exception {
        apiMsgWithError = mock(ApiMsg.class);
        when(apiMsgWithError.isError()).thenReturn(true);
        when(apiMsgWithError.getErrorCode()).thenReturn(31337);
        when(apiMsgWithError.isError()).thenReturn(true);
    }

    @Test
    public void testIsMining() throws Exception {
        KernelConnection kc = mock(KernelConnection.class);
        IAionAPI api = mock(IAionAPI.class);
        when(kc.getApi()).thenReturn(api);
        IMine mine = mock(IMine.class);
        when(api.getMine()).thenReturn(mine);
        ApiMsg msg = mock(ApiMsg.class);
        when(mine.isMining()).thenReturn(msg);
        when(msg.isError()).thenReturn(false);
        when(msg.getObject()).thenReturn(Boolean.valueOf(true));

        GeneralKernelInfoRetriever unit = new GeneralKernelInfoRetriever(kc, null);
        assertThat(unit.isMining(), is(true));
    }

    @Test(expected = ApiDataRetrievalException.class)
    public void testIsMiningReturnedError() throws Exception {
        KernelConnection kc = mock(KernelConnection.class);
        IAionAPI api = mock(IAionAPI.class);
        when(kc.getApi()).thenReturn(api);
        IMine mine = mock(IMine.class);
        when(api.getMine()).thenReturn(mine);
        when(mine.isMining()).thenReturn(apiMsgWithError);
        GeneralKernelInfoRetriever unit = new GeneralKernelInfoRetriever(kc);
        unit.isMining();
    }

    @Test
    public void testGetPeerCount() throws Exception {
        int peerCount = 13;

        KernelConnection kc = mock(KernelConnection.class);
        IAionAPI api = mock(IAionAPI.class);
        when(kc.getApi()).thenReturn(api);
        INet net = mock(INet.class);
        when(api.getNet()).thenReturn(net);
        ApiMsg msg = mock(ApiMsg.class);
        when(net.getActiveNodes()).thenReturn(msg);
        when(msg.isError()).thenReturn(false);
        List<Object> peerList = new LinkedList<>() {{
            for(int ix = 0; ix < peerCount; ++ix) {
                add(new Object());
            }
        }};
        when(msg.getObject()).thenReturn(peerList);

        GeneralKernelInfoRetriever unit = new GeneralKernelInfoRetriever(kc, null);
        assertThat(unit.getPeerCount(), is(peerCount));
    }

    @Test(expected = ApiDataRetrievalException.class)
    public void testGetPeerCountReturnedError() throws Exception {
        KernelConnection kc = mock(KernelConnection.class);
        IAionAPI api = mock(IAionAPI.class);
        when(kc.getApi()).thenReturn(api);
        INet net = mock(INet.class);
        when(api.getNet()).thenReturn(net);
        ApiMsg msg = mock(ApiMsg.class);
        when(net.getActiveNodes()).thenReturn(apiMsgWithError);
        GeneralKernelInfoRetriever unit = new GeneralKernelInfoRetriever(kc);
        unit.getPeerCount();
    }
}