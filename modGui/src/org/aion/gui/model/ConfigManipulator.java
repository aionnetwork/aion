package org.aion.gui.model;

import com.google.common.base.Charsets;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import org.aion.mcf.config.Cfg;
import org.aion.mcf.config.dynamic2.ConfigProposalResult;
import org.aion.mcf.config.dynamic2.InFlightConfigReceiverMBean;
import org.aion.os.KernelLauncher;
import org.aion.zero.impl.config.CfgAion;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * Provides an interface (intended to be used by Controllers) to manipulate the Kernel config.xml
 * file.  In the future it will also talk to the interface in the Kernel that will allow for
 * dynamic config change.
 */
public class ConfigManipulator {
    // TODO:
    // Current method of determining file path of config.xml is a little wonky.  cfg is the
    // instance of Cfg that the GUI is currently running.
    //
    // Problem #1: We ask it for the base dir and then blindly append a fixed string to it.
    // Problem #2: When Aion kernel runs that will instantiate a different Cfg.  We know at the moment
    //             from the impl that the part of the Cfg we care about -- base dir -- doesn't change.
    //             Does not seem very reliable.

    private final Cfg cfg;
    private final KernelLauncher kernelLauncher;

    private String lastLoadContent;

    /**
     * Constructor
     *
     * @param cfg The Cfg that is currently in use by
     * @param kernelLauncher
     */
    public ConfigManipulator(Cfg cfg,
                             KernelLauncher kernelLauncher) {
        this.cfg = cfg;
        this.kernelLauncher = kernelLauncher;
    }

    public String loadFromConfigFile() {
        try {
            lastLoadContent = Files.toString(configFile(), Charsets.UTF_8);
            return lastLoadContent;
        } catch (IOException e) {
            e.printStackTrace();
            return "<<error>>";
        }
    }

    public String getLastLoadedContent() {
        return lastLoadContent;
    }

    public Optional<String> checkForErrors(String cfgXml) {
        try {
            XMLStreamReader xmlStream = XMLInputFactory.newInstance()
                    .createXMLStreamReader(CharSource.wrap(cfgXml).openStream());
            new CfgAion().fromXML(xmlStream);
            return Optional.empty();
        } catch (Exception e) {
            e.printStackTrace();
            String errorText = e.getMessage();
            return Optional.of(errorText != null ? errorText : "Unknown error");
        }
    }

    private void doJmxStuff(String cfgText) {
        // JMX stuff
        try {
            JMXServiceURL url =
                    new JMXServiceURL("service:jmx:rmi:///jndi/rmi://127.0.0.1:11234/jmxrmi");
            JMXConnector jmxc = JMXConnectorFactory.connect(url, null);
            MBeanServerConnection mbeanServerConnection = jmxc.getMBeanServerConnection();
            ObjectName mbeanName = new ObjectName("org.aion.mcf.config.dynamic:type=testing");
            InFlightConfigReceiverMBean mbeanProxy = MBeanServerInvocationHandler.newProxyInstance(
                    mbeanServerConnection, mbeanName, InFlightConfigReceiverMBean.class, true);

            ConfigProposalResult result = mbeanProxy.propose(cfgText);
            System.out.println("result = " + result.success);
        } catch (Exception e) {
            System.out.println("Something went horribly wrong!!");
            e.printStackTrace();
        }
        // End JMX stuff
    }

    public ApplyConfigResult applyNewConfig(String cfgXml) {
//        if(kernelLauncher.hasLaunchedInstance()) {
//            return new ApplyConfigResult(false,
//                    "Kernel is running.  Please terminate before applying.",
//                    null);
//        }

        Optional<String> maybeError = checkForErrors(cfgXml);
        if(maybeError.isPresent()) {
            String msg = "Could not apply config because it has errors.  File will not be saved.  Error was:\n\n"
                    + maybeError.get();
            return new ApplyConfigResult(false, msg, null);
        }

        doJmxStuff(cfgXml);

        final String backupConfigFilename;
        try {
            backupConfigFilename = backupConfig();
        } catch (IOException ioe) {
            String msg =
                    "Failed to backup existing config, so aborting operation.  Error during backup:\n\n"
                    + ioe.getMessage();
            ioe.printStackTrace();
            return new ApplyConfigResult(false, msg, null);
        }

        try {
            //Files.write(cfgXml, configFile(), Charsets.UTF_8);
            System.out.println("Pretending to write the file!");
        } catch (RuntimeException /* TODO IOExcepion */ioe) {
            String msg =
                    "Failed to write to the config file, so aborting operation.  Error during write:\n\n"
                    + ioe.getMessage();
            ioe.printStackTrace();
            return new ApplyConfigResult(false, msg, null);
        }

        return new ApplyConfigResult(true, "Config saved.  Previous copy is backed up at " + backupConfigFilename, null);
    }

    private String backupConfig() throws IOException {
        Files.write(lastLoadContent, backupConfigFile(), Charsets.UTF_8);
        return backupConfigFile().getAbsolutePath();
    }

    public File configFile() {
        return new File(cfg.getBasePath() + "/config/config.xml");
    }

    private File backupConfigFile() {
        return new File(cfg.getBasePath() + "/config/config.backup.xml");
    }


}
