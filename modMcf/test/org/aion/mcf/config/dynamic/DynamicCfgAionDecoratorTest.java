package org.aion.mcf.config.dynamic;

import com.google.common.io.CharSource;
import org.aion.mcf.config.Cfg;
import org.aion.mcf.config.dynamic2.ConfigProposalResult;
import org.aion.mcf.config.dynamic2.InFlightConfigReceiverMBean;
import org.aion.zero.impl.config.CfgAion;
import org.junit.Test;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

public class DynamicCfgAionDecoratorTest {
//
//    @Test
//    public void test() {
//        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
//        ObjectName objectName = null;
//        try {
//            objectName = new ObjectName("org.aion.mcf.config.dynamic:type=testing");
//        } catch (MalformedObjectNameException e) {
//            e.printStackTrace();
//        }
//
//        Cfg cfg = CfgAion.inst();
//        IDynamicConfig dc = new DynamicCfgAionDecorator(cfg);
//        try {
//            server.registerMBean(dc, objectName);
//        } catch (InstanceAlreadyExistsException e) {
//            e.printStackTrace();
//        } catch (MBeanRegistrationException e) {
//            e.printStackTrace();
//        } catch (NotCompliantMBeanException e) {
//            e.printStackTrace();
//        }
//
//        while (true) {
//        }
//    }

    @Test
    public void client() throws Exception {
        JMXServiceURL url =
                new JMXServiceURL("service:jmx:rmi:///jndi/rmi://127.0.0.1:11234/jmxrmi");
        JMXConnector jmxc = JMXConnectorFactory.connect(url, null);
        MBeanServerConnection mbeanServerConnection = jmxc.getMBeanServerConnection();
        ObjectName mbeanName = new ObjectName("org.aion.mcf.config.dynamic:type=testing");
        InFlightConfigReceiverMBean mbeanProxy = (InFlightConfigReceiverMBean) MBeanServerInvocationHandler.newProxyInstance(
                mbeanServerConnection, mbeanName, InFlightConfigReceiverMBean.class, true);


        Cfg cfg = new CfgAion();
        cfg.setId("testId");
        ConfigProposalResult result = mbeanProxy.propose(cfgExample);
        System.out.println("result = " + result.isSuccess());
    }

    @Test
    public void cfgParse() throws Exception {
        Cfg cfg = new CfgAion();
        XMLStreamReader xmlStream = XMLInputFactory.newInstance()
                .createXMLStreamReader(CharSource.wrap(cfgExample).openStream());
        ((CfgAion) cfg).fromXML(xmlStream);
        return;
    }

    private String cfgExample = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<aion>\n" +
            "\t<mode>aion</mode>\n" +
            "\t<id>hello-world</id>\n" +
            "\t<api>\n" +
            "\t\t<rpc active=\"false\" ip=\"127.0.0.1\" port=\"8545\">\n" +
            "\t\t\t<!--boolean, enable/disable cross origin requests (browser enforced)-->\n" +
            "\t\t\t<cors-enabled>true</cors-enabled>\n" +
            "\t\t\t<!--comma-separated list, APIs available: web3,net,debug,personal,eth,stratum-->\n" +
            "\t\t\t<apis-enabled>web3,eth,personal,stratum</apis-enabled>\n" +
            "\t\t\t<!--size of thread pool allocated for rpc requests-->\n" +
            "\t\t\t<threads>1</threads>\n" +
            "\t\t</rpc>\n" +
            "\t\t<java active=\"true\" ip=\"127.0.0.1\" port=\"8547\"></java>\n" +
            "\t\t<nrg-recommendation>\n" +
            "\t\t\t<!--default NRG price used by api if oracle disabled, minimum price recommended by oracle-->\n" +
            "\t\t\t<default>10000000000</default>\n" +
            "\t\t\t<!--max NRG price recommended by oracle-->\n" +
            "\t\t\t<max>100000000000</max>\n" +
            "\t\t\t<!--enable/diable nrg-oracle service. if disabled, api returns default NRG price if asked for nrgPrice-->\n" +
            "\t\t\t<oracle-enabled>false</oracle-enabled>\n" +
            "\t\t</nrg-recommendation>\n" +
            "\t</api>\n" +
            "\t<net>\n" +
            "\t\t<id>128</id>\n" +
            "        <nodes>\n" +
            "            <!--<node>p2p://1832201f-02b5-4f7d-ab86-9ea4d080ec05@127.0.0.1:35303</node>-->\n" +
            "\t\t</nodes>\n" +
            "\t\t<p2p>\n" +
            "\t\t\t<ip>0.0.0.0</ip>\n" +
            "\t\t\t<port>30303</port>\n" +
            "\t\t\t<discover>false</discover>\n" +
            "\t\t\t<show-status>false</show-status>\n" +
            "\t\t\t<max-temp-nodes>128</max-temp-nodes>\n" +
            "\t\t\t<max-active-nodes>128</max-active-nodes>\n" +
            "\t\t</p2p>\n" +
            "\t</net>\n" +
            "\t<sync>\n" +
            "\t\t<blocks-queue-max>32</blocks-queue-max>\n" +
            "\t\t<show-status>false</show-status>\n" +
            "\t</sync>\n" +
            "\t<consensus>\n" +
            "\t\t<mining>true</mining>\n" +
            "\t\t<miner-address>0xa00b7d34797f96e25f4e8daf7545bbffccb8d70749da20c0de883734a496d5cd</miner-address>\n" +
            "\t\t<cpu-mine-threads>3</cpu-mine-threads>\n" +
            "\t\t<extra-data>AION</extra-data>\n" +
            "\t\t<nrg-strategy>\n" +
            "\t\t\t<clamped-decay upper-bound=\"20000000\" lower-bound=\"15000000\"></clamped-decay>\n" +
            "\t\t</nrg-strategy>\n" +
            "\t</consensus>\n" +
            "\t<db>\n" +
            "\t\t<!--Sets the physical location on disk where data will be stored.-->\n" +
            "        <path>database.local1</path>\n" +
            "\t\t<!--Boolean value. Enable/disable database integrity check run at startup.-->\n" +
            "\t\t<check_integrity>true</check_integrity>\n" +
            "\t\t<!--Database implementation used to store data; supported options: leveldb, h2, rocksdb.-->\n" +
            "\t\t<!--Caution: changing implementation requires re-syncing from genesis!-->\n" +
            "\t\t<vendor>leveldb</vendor>\n" +
            "\t\t<!--Boolean value. Enable/disable database compression to trade storage space for execution time.-->\n" +
            "\t\t<enable_db_compression>false</enable_db_compression>\n" +
            "\t</db>\n" +
            "    <log>\n" +
            "        <!--\n" +
            "\t\t<GEN>DEBUG</GEN>\n" +
            "\t\t<VM>DEBUG</VM>\n" +
            "\t\t<API>DEBUG</API>\n" +
            "\t\t<SYNC>DEBUG</SYNC>\n" +
            "\t\t<CONS>DEBUG</CONS>\n" +
            "        <DB>DEBUG</DB>\n" +
            "        <GUI>TRACE</GUI>\n" +
            "        -->\n" +
            "\n" +
            "        <GEN>INFO</GEN>\n" +
            "\t\t<VM>ERROR</VM>\n" +
            "\t\t<API>TRACE</API>\n" +
            "\t\t<SYNC>INFO</SYNC>\n" +
            "\t\t<CONS>INFO</CONS>\n" +
            "        <DB>ERROR</DB>\n" +
            "\t</log>\n" +
            "\t<tx>\n" +
            "\t\t<cacheMax>256</cacheMax>\n" +
            "\t</tx>\n" +
            "\n" +
            "\n" +
            "\t<gui>\n" +
            "        <launcher>\n" +
            "            <autodetect>true</autodetect>\n" +
            "            <!--<keep-kernel-on-exit>true</keep-kernel-on-exit>-->\n" +
            "\n" +
            "            <!-- If autodetect is turned off, below java_cmd and java_home must be supplied. -->\n" +
            "            <!--<java-home>not-used</java-home>\n" +
            "            <aion-sh>not-used</aion-sh>\n" +
            "            <working_dir>not-used</working_dir>-->\n" +
            "            \n" +
            "        </launcher>\n" +
            "\n" +
            "\t</gui>\n" +
            "\n" +
            "</aion>";

    private boolean flag = false;
    Object pauseLock = new Object();

    @Test
    public void thread() throws Exception {
        Thread t = new Thread( () -> {
            for(;;) {
                synchronized (pauseLock) {
                    if(flag) {
                        try {
                            pauseLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                System.out.println("t Running");
            }
        });

        Thread modifier = new Thread( () -> {
            try {
                increment();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        t.start();
        modifier.start();
        t.join();
    }

    private void increment() throws InterruptedException {
        System.out.println("zzz");
        Thread.sleep(1500);
        System.out.println("set to true");
        flag = true;
    }
}