package org.aion.gui.model;

import org.aion.api.IAionAPI;
import org.aion.api.type.ApiMsg;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        unit = new AbstractAionApiClient(kernelConnection, errorHandler) {};
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