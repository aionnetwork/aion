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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;
import org.aion.api.IAionAPI;
import org.aion.api.IMine;
import org.aion.api.INet;
import org.aion.api.type.ApiMsg;
import org.junit.Before;
import org.junit.Test;

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
        when(kc.isConnected()).thenReturn(true);
        IAionAPI api = mock(IAionAPI.class);
        when(api.isConnected()).thenReturn(true);
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

    @Test
    public void testIsMiningWhenNull() throws Exception {
        KernelConnection kc = mock(KernelConnection.class);
        when(kc.isConnected()).thenReturn(false);
        IAionAPI api = mock(IAionAPI.class);
        when(api.isConnected()).thenReturn(false);
        when(kc.getApi()).thenReturn(api);
        IMine mine = mock(IMine.class);
        when(api.getMine()).thenReturn(mine);
        ApiMsg msg = mock(ApiMsg.class);
        when(mine.isMining()).thenReturn(msg);
        when(msg.isError()).thenReturn(false);
        when(msg.getObject()).thenReturn(Boolean.valueOf(true));

        GeneralKernelInfoRetriever unit = new GeneralKernelInfoRetriever(kc, null);
        assertThat(unit.isMining(), is(nullValue()));
    }

    @Test(expected = ApiDataRetrievalException.class)
    public void testIsMiningReturnedError() throws Exception {
        KernelConnection kc = mock(KernelConnection.class);
        when(kc.isConnected()).thenReturn(true);
        IAionAPI api = mock(IAionAPI.class);
        when(api.isConnected()).thenReturn(true);
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
        when(kc.isConnected()).thenReturn(true);
        IAionAPI api = mock(IAionAPI.class);
        when(kc.getApi()).thenReturn(api);
        when(api.isConnected()).thenReturn(true);
        INet net = mock(INet.class);
        when(api.getNet()).thenReturn(net);
        ApiMsg msg = mock(ApiMsg.class);
        when(net.getActiveNodes()).thenReturn(msg);
        when(msg.isError()).thenReturn(false);
        List<Object> peerList =
                new LinkedList<>() {
                    {
                        for (int ix = 0; ix < peerCount; ++ix) {
                            add(new Object());
                        }
                    }
                };
        when(msg.getObject()).thenReturn(peerList);

        GeneralKernelInfoRetriever unit = new GeneralKernelInfoRetriever(kc, null);
        assertThat(unit.getPeerCount(), is(peerCount));
    }

    @Test
    public void testGetPeerCountWhenNotConnected() throws Exception {
        int peerCount = 13;

        KernelConnection kc = mock(KernelConnection.class);
        when(kc.isConnected()).thenReturn(false);
        IAionAPI api = mock(IAionAPI.class);
        when(kc.getApi()).thenReturn(api);
        when(api.isConnected()).thenReturn(false);
        INet net = mock(INet.class);
        when(api.getNet()).thenReturn(net);
        ApiMsg msg = mock(ApiMsg.class);
        when(net.getActiveNodes()).thenReturn(msg);
        when(msg.isError()).thenReturn(false);
        List<Object> peerList =
                new LinkedList<>() {
                    {
                        for (int ix = 0; ix < peerCount; ++ix) {
                            add(new Object());
                        }
                    }
                };
        when(msg.getObject()).thenReturn(peerList);

        GeneralKernelInfoRetriever unit = new GeneralKernelInfoRetriever(kc, null);
        assertThat(unit.getPeerCount(), is(nullValue()));
    }

    @Test(expected = ApiDataRetrievalException.class)
    public void testGetPeerCountReturnedError() throws Exception {
        KernelConnection kc = mock(KernelConnection.class);
        when(kc.isConnected()).thenReturn(true);
        IAionAPI api = mock(IAionAPI.class);
        when(api.isConnected()).thenReturn(true);
        when(kc.getApi()).thenReturn(api);
        INet net = mock(INet.class);
        when(api.getNet()).thenReturn(net);
        ApiMsg msg = mock(ApiMsg.class);
        when(net.getActiveNodes()).thenReturn(apiMsgWithError);
        GeneralKernelInfoRetriever unit = new GeneralKernelInfoRetriever(kc);
        unit.getPeerCount();
    }
}
