package org.aion.gui.model;

import com.google.common.base.Charsets;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import org.aion.log.AionLoggerFactory;
import org.aion.mcf.config.Cfg;
import org.aion.mcf.config.dynamic2.ConfigProposalResult;
import org.aion.mcf.config.dynamic2.InFlightConfigReceiver;
import org.aion.mcf.config.dynamic2.InFlightConfigReceiverMBean;
import org.aion.mcf.config.dynamic2.RollbackException;
import org.aion.os.KernelLauncher;
import org.aion.zero.impl.config.CfgAion;
import org.slf4j.Logger;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Optional;

/**
 * Provides an interface (intended to be used by Controllers) to manipulate the kernel config.
 * This includes both the config.xml file as well as sending remote commands to a running kernel
 * to reconfigure it dynamically.
 */
public class ConfigManipulator {
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

    private static final Logger LOG = AionLoggerFactory.getLogger(org.aion.log.LogEnum.GUI.name());

    /**
     * Constructor
     *
     * @param cfg            The Cfg that is currently in use by
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

    /**
     * Apply a new config
     *
     * @param cfgXml XML text of new config
     * @return {@link ApplyConfigResult} whether it was successful or failure, plus reason for failure
     */
    public ApplyConfigResult applyNewConfig(String cfgXml) {
        Optional<String> maybeError = checkForErrors(cfgXml);
        if (maybeError.isPresent()) {
            String msg = "Could not apply config because it has errors.  File will not be saved.  Error was:\n\n"
                    + maybeError.get();
            return new ApplyConfigResult(false, msg, null);
        }

        ApplyConfigResult result = sendConfigProposal(cfgXml);
        if (!result.isSucceeded()) {
            return result;
        }

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
            Files.write(cfgXml, configFile(), Charsets.UTF_8);
            LOG.info("Saving new config.xml");
        } catch (IOException ioe) {
            String msg =
                    "Failed to write to the config file, so aborting operation.  Error during write:\n\n"
                            + ioe.getMessage();
            ioe.printStackTrace();
            return new ApplyConfigResult(false, msg, null);
        }

        return new ApplyConfigResult(result.isSucceeded(),
                "Config saved.  Previous copy is backed up at " + backupConfigFilename,
                null);
    }

    /**
     * @param cfgText
     * @return
     */
    private ApplyConfigResult sendConfigProposal(String cfgText) {
        try {
            JMXServiceURL url = new JMXServiceURL(
                    InFlightConfigReceiver.createJmxUrl(InFlightConfigReceiver.DEFAULT_JMX_PORT));
            try (JMXConnector conn = JMXConnectorFactory.connect(url, null)) {
                MBeanServerConnection mbeanServerConnection = conn.getMBeanServerConnection();
                ObjectName objectName = new ObjectName(InFlightConfigReceiver.DEFAULT_JMX_OBJECT_NAME);
                InFlightConfigReceiverMBean mbeanProxy = MBeanServerInvocationHandler.newProxyInstance(
                        mbeanServerConnection, objectName, InFlightConfigReceiverMBean.class, true);

                ConfigProposalResult result = mbeanProxy.propose(cfgText);
                LOG.debug("JMX propose call returned: " + result.toString());
                String msg = result.getErrorCause() != null ? result.getErrorCause().getMessage() : null;

                return new ApplyConfigResult(result.isSuccess(), msg, result.getErrorCause());
            }
        } catch (IOException | MalformedObjectNameException ex) {
            LOG.error("JMX call exception", ex);
            return new ApplyConfigResult(false,
                    "Failed to make JMX call", ex);
        } catch (RollbackException re) {
            LOG.error("Kernel encountered config error and failed to roll back", re);
            return new ApplyConfigResult(false,
                    "Encountered error while applying config changes, " +
                            "but could not undo the partially applied changes.  " +
                            "It is recommended that you restart your kernel.",
                    re);
        }
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
