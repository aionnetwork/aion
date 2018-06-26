package org.aion.gui.model;

import org.aion.api.IAionAPI;
import org.aion.api.type.ApiMsg;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AbstractAionApiClientTest {
    private IAionAPI api;
    private KernelConnection kernelConnection;
    private AbstractAionApiClient unit;

    @Before
    public void before() {
        api = mock(IAionAPI.class);
        kernelConnection = mock(KernelConnection.class);
        when(kernelConnection.getApi()).thenReturn(api);
        unit = new AbstractAionApiClient(kernelConnection) {};
    }

    @Test
    public void testCallApi() {
        AbstractAionApiClient.ApiFunction func = mock(AbstractAionApiClient.ApiFunction.class);
        unit.callApi(func);
        verify(func).call(api);
    }

    @Test
    public void testThrowAndLogIfError() {
        ApiMsg msg = mock(ApiMsg.class);
        when(msg.isError()).thenReturn(true);
        when(msg.getErrString()).thenReturn("myErrorString");
        when(msg.getErrorCode()).thenReturn(1337);
        try {
            unit.throwAndLogIfError(msg);
        } catch (ApiDataRetrievalException ex) {
            assertThat(ex.getMessage(), is("Error in API call.  Code = 1337.  Error = myErrorString."));
            assertThat(ex.getApiMsgCode(), is(1337));
            assertThat(ex.getApiMsgString(), is("myErrorString"));
            return;
        }
        fail("Expected exception wasn't thrown.");
    }
}