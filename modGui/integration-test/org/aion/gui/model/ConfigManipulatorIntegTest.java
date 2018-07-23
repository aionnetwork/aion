package org.aion.gui.model;

import org.aion.zero.impl.config.dynamic.InFlightConfigReceiver;
import org.aion.zero.impl.config.dynamic.InFlightConfigReceiverMBean;
import org.junit.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ConfigManipulatorIntegTest {
    // should probably have some logic to randomly try ports in case some other process is using this one
    private final int JMX_PORT = 31234;

    /**
     * Tests that {@link ConfigManipulator.JmxCaller#sendConfigProposal(String)} ()}
     * creates a proxy that can talk to JMX.
     */
    @Test
    public void testJmxCallerGetInFlightConfigReceiver() throws Exception {
        // set up the jmx "server" that the proxy will connect to
        LocateRegistry.createRegistry(JMX_PORT);
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        JMXServiceURL url = new JMXServiceURL(String.format(
                "service:jmx:rmi://localhost/jndi/rmi://localhost:%d/jmxrmi", JMX_PORT));
        JMXConnectorServer svr = JMXConnectorServerFactory.newJMXConnectorServer(url, null, server);
        svr.start();
        InFlightConfigReceiverMBean proxyTarget = mock(InFlightConfigReceiver.class);
        ObjectName objectName = new ObjectName(InFlightConfigReceiver.DEFAULT_JMX_OBJECT_NAME);
        server.registerMBean(proxyTarget, objectName);

        ConfigManipulator.JmxCaller client = new ConfigManipulator.JmxCaller();
        client.sendConfigProposal(JMX_PORT, "<myXml/>");
        verify(proxyTarget).propose("<myXml/>");
    }
}