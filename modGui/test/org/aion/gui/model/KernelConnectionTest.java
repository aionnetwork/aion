package org.aion.gui.model;

import com.google.common.eventbus.EventBus;
import com.google.common.io.CharSource;
import org.aion.api.IAionAPI;
import org.aion.api.type.ApiMsg;
import org.aion.api.type.Event;
import org.aion.gui.events.AbstractUIEvent;
import org.aion.gui.events.EventBusRegistry;
import org.aion.gui.events.RefreshEvent;
import org.aion.mcf.config.CfgApi;
import org.aion.mcf.config.CfgApiZmq;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.aion.gui.events.RefreshEvent.Type.OPERATION_FINISHED;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KernelConnectionTest {
    private IAionAPI api;
    private CfgApi cfgApi;
    private EventBus eventBus;
    private ExecutorService executorService;
    private KernelConnection unit;

    private final static int EXECUTOR_SERVICE_TIMEOUT_SEC = 2;

    @Before
    public void before() throws Exception {
        api = mock(IAionAPI.class);

        cfgApi = new CfgApi();
        String cfgXml = "<java ip=\"someIpAddress\" port=\"12345\" />";
        XMLStreamReader xmlStream = XMLInputFactory.newInstance()
                .createXMLStreamReader(CharSource.wrap(cfgXml).openStream());
        cfgApi.fromXML(xmlStream);

        eventBus = mock(EventBus.class);
        executorService = Executors.newSingleThreadExecutor();
        unit = new KernelConnection(api, cfgApi, eventBus, executorService);
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
        ArgumentCaptor<AbstractUIEvent> captor = ArgumentCaptor.forClass(AbstractUIEvent.class);
        verify(eventBus).post(captor.capture());
        assertThat((captor.getValue()).getType(), is(OPERATION_FINISHED));
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