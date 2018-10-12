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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.aion.api.IAionAPI;
import org.aion.api.type.ApiMsg;
import org.junit.Before;
import org.junit.Test;

public class AbstractAionApiClientTest {

    private IAionAPI api;
    private IApiMsgErrorHandler errorHandler;
    private KernelConnection kernelConnection;
    private AbstractAionApiClient unit;

    @Before
    public void before() {
        api = mock(IAionAPI.class);
        errorHandler = mock(IApiMsgErrorHandler.class);
        kernelConnection = mock(KernelConnection.class);
        when(kernelConnection.getApi()).thenReturn(api);
        unit = new AbstractAionApiClient(kernelConnection, errorHandler) {
        };
    }

    @Test
    public void testCallApi() {
        ApiMsg msg = mock(ApiMsg.class);
        AbstractAionApiClient.ApiFunction func = mock(AbstractAionApiClient.ApiFunction.class);
        when(func.call(api)).thenReturn(msg);

        unit.callApi(func);

        verify(func).call(api);
        verify(errorHandler).handleError(msg);
    }
}