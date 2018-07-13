package org.aion.gui.model;

import com.google.common.annotations.VisibleForTesting;
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
    private final FileLoaderSaver fileLoaderSaver;
    private final JmxCaller jmxCaller;

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
        this(cfg, kernelLauncher, new FileLoaderSaver(), new JmxCaller());
    }

    @VisibleForTesting
    ConfigManipulator(Cfg cfg,
                      KernelLauncher kernelLauncher,
                      FileLoaderSaver fileLoaderSaver,
                      JmxCaller jmxCaller) {
        this.cfg = cfg;
        this.kernelLauncher = kernelLauncher;
        this.fileLoaderSaver = fileLoaderSaver;
        this.jmxCaller = jmxCaller;
    }

    public String loadFromConfigFile() {
        try {
            lastLoadContent = fileLoaderSaver.load(configFile());
        } catch (IOException ioe) {
            LOG.error("Couldn't load config file,", ioe);
            return "<Could not load config file>";
            // TODO throw/return something that UI will respond to by showing a graphical error
        }
        return lastLoadContent;
    }

    public String getLastLoadedContent() {
        return lastLoadContent;
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

        ApplyConfigResult result = sendConfigProposal(cfgXml);
        if (!result.isSucceeded()) {
            return result;
        }

        try {
            fileLoaderSaver.save(cfgXml, configFile());
            LOG.info("Saving new config.xml");
        } catch (IOException ioe) {
            String msg =
                    "Config was successfully applied, but failed to save to config.xml:\n\n"
                            + ioe.getMessage();
            LOG.error(msg, ioe);
            return new ApplyConfigResult(true, msg, null);
        }

        return new ApplyConfigResult(result.isSucceeded(),
                "Config saved.  Previous copy is backed up at " + backupConfigFilename,
                null);
    }

    private Optional<String> checkForErrors(String cfgXml) {
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

    @VisibleForTesting
    ApplyConfigResult sendConfigProposal(String cfgText) {
        try {
            ConfigProposalResult result = jmxCaller.getInFlightConfigReceiver().propose(cfgText);
            LOG.debug("JMX propose call returned: " + result.toString());
            String msg = result.getErrorCause() != null ? result.getErrorCause().getMessage() : null;

            return new ApplyConfigResult(result.isSuccess(), msg, result.getErrorCause());
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
        fileLoaderSaver.save(lastLoadContent, backupConfigFile());
        return backupConfigFile().getAbsolutePath();
    }

    public File configFile() {
        return new File(cfg.getBasePath() + "/config/config.xml");
    }

    private File backupConfigFile() {
        return new File(cfg.getBasePath() + "/config/config.backup.xml");
    }

    // -- Inner classes ---------------------------------------------------------------------------
    // (only used to make unit testing of ConfigManipulator easier)

    @VisibleForTesting
    static class JmxCaller {
        public InFlightConfigReceiverMBean getInFlightConfigReceiver(int port)
        throws IOException, MalformedObjectNameException {
            JMXServiceURL url = new JMXServiceURL(
                    InFlightConfigReceiver.createJmxUrl(port));
            try (JMXConnector conn = JMXConnectorFactory.connect(url, null)) {
                MBeanServerConnection mbeanServerConnection = conn.getMBeanServerConnection();
                ObjectName objectName = new ObjectName(InFlightConfigReceiver.DEFAULT_JMX_OBJECT_NAME);
                return MBeanServerInvocationHandler.newProxyInstance(
                        mbeanServerConnection, objectName, InFlightConfigReceiverMBean.class, true);
            }
        }

        public InFlightConfigReceiverMBean getInFlightConfigReceiver()
                throws IOException, MalformedObjectNameException {
            return getInFlightConfigReceiver(InFlightConfigReceiver.DEFAULT_JMX_PORT);
        }
    }

    @VisibleForTesting
    static class FileLoaderSaver {
        public void save(String cfgXml, File file) throws IOException {
            Files.write(cfgXml, file, Charsets.UTF_8);
        }

        public String load(File file) throws IOException {
            return Files.toString(file, Charsets.UTF_8);
        }
    }
}
