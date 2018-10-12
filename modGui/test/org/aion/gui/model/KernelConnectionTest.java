package org.aion.gui.model;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.io.CharSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import org.aion.api.IAionAPI;
import org.aion.api.type.ApiMsg;
import org.aion.gui.events.EventPublisher;
import org.aion.mcf.config.CfgApi;
import org.aion.wallet.console.ConsoleManager;
import org.junit.Before;
import org.junit.Test;

public class KernelConnectionTest {

    private final static int EXECUTOR_SERVICE_TIMEOUT_SEC = 2;
    private IAionAPI api;
    private CfgApi cfgApi;
    private EventPublisher eventPublisher;
    private ExecutorService executorService;
    private KernelConnection unit;

    @Before
    public void before() throws Exception {
        api = mock(IAionAPI.class);

        cfgApi = new CfgApi();
        String cfgXml = "<java ip=\"someIpAddress\" port=\"12345\" />";
        XMLStreamReader xmlStream = XMLInputFactory.newInstance()
            .createXMLStreamReader(CharSource.wrap(cfgXml).openStream());
        cfgApi.fromXML(xmlStream);

        eventPublisher = mock(EventPublisher.class);
        executorService = Executors.newSingleThreadExecutor();
        unit = new KernelConnection(api, cfgApi, eventPublisher, mock(ConsoleManager.class),
            executorService);
    }

    @Test
    public void testConnect() {
        boolean expectedReconnect = true;
        String expectedConnectionString = "tcp://someIpAddress:12345";
        ApiMsg msg = mock(ApiMsg.class);
        when(api.connect(anyString(), anyBoolean())).thenReturn(msg);
        when(msg.isError()).thenReturn(false);
        unit.connect();
        try {
            executorService.awaitTermination(EXECUTOR_SERVICE_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("Execution took too long.");
        }
        verify(api).connect(expectedConnectionString, expectedReconnect);
        verify(eventPublisher).fireConnectionEstablished();
    }

    @Test
    public void testDisconnect() {
        when(api.isConnected()).thenReturn(true);
        unit.disconnect();
        try {
            executorService.awaitTermination(EXECUTOR_SERVICE_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("Execution took too long.");
        }
        verify(api).destroyApi();
        verify(eventPublisher).fireDisconnected();
    }

    @Test
    public void testDisconnectWhenNotConnected() {
        when(api.isConnected()).thenReturn(false);
        unit.disconnect();
        try {
            executorService.awaitTermination(EXECUTOR_SERVICE_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("Execution took too long.");
        }
        verify(api, never()).destroyApi();
    }

    @Test
    public void getApi() {
        assertThat(unit.getApi(), is(api));
    }
}