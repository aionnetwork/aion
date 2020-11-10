package org.aion.zero.impl.config;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.zero.impl.SystemExitCodes;
import org.aion.zero.impl.types.AionGenesis;
import org.aion.zero.impl.types.ForkPropertyLoader;
import org.aion.zero.impl.types.GenesisBlockLoader;
import org.aion.zero.impl.types.ProtocolUpgradeSettings;

/** @author chris */
public final class CfgAion {

    protected AionGenesis genesis;

    protected static final int N = 210;

    private static final int K = 9;

    private static final String NODE_ID_PLACEHOLDER = "[NODE-ID-PLACEHOLDER]";

    protected String mode;
    protected String id;
    protected String keystorePath;

    protected CfgApi api;
    protected CfgNet net;
    protected CfgConsensusUnity consensus;
    protected CfgSync sync;
    protected CfgDb db;
    protected CfgLog log;
    protected CfgTx tx;
    protected CfgReports reports;
    // TODO: [GUI] disable GUI config features
    //protected CfgGui gui;
    protected CfgFork fork;

    /* ------------ execution path management ------------ */

    // names
    private final String configDirName = "config";
    private final String allNetworksDirName = "networks";
    private final String configFileName = "config.xml";
    private final String genesisFileName = "genesis.json";
    private final String keystoreDirName = "keystore";
    private final String forkFileName = "fork.properties";

    // base path
    private final File INITIAL_PATH = new File(System.getProperty("user.dir"));

    // directories containing the configuration files
    private final File CONFIG_DIR = new File(INITIAL_PATH, allNetworksDirName);
    private File networkConfigDir = null;

    // base configuration: old kernel OR using network config
    private File baseConfigFile = null;
    private File baseGenesisFile = null;
    private File baseForkFile = null;

    // can be absolute in config file OR depend on execution path
    private File logDir = null;
    private File databaseDir = null;
    private File keystoreDir = null;
    private boolean absoluteLogDir = false;
    private boolean absoluteDatabaseDir = false;
    private boolean absoluteKeystoreDir = false;

    // impact execution path
    private String network = null;
    private File dataDir = null;

    /** Data directory with network. */
    private File execDir = null;
    private File execConfigDir = null;
    private File execConfigFile = null;

    public CfgAion() {
        this.mode = "aion";
        this.id = UUID.randomUUID().toString();
        this.keystorePath = null;
        this.net = new CfgNet();
        this.consensus = new CfgConsensusUnity();
        this.sync = new CfgSync();
        this.api = new CfgApi();
        this.db = new CfgDb();
        this.log = new CfgLog();
        this.tx = new CfgTx();
        this.reports = new CfgReports();
        // TODO: [GUI] disable GUI config features
        //this.gui = new CfgGui();
        this.fork = new CfgFork();
        initializeConfiguration();
    }

    private static class CfgAionHolder {
        private static CfgAion inst = new CfgAion();
    }

    public static CfgAion inst() {
        return CfgAionHolder.inst;
    }

    public static void setInst(CfgAion cfgAion) {
        CfgAionHolder.inst = cfgAion;
    }

    public void setGenesis() {
        setGenesisInner(false);
    }

    public void setGenesisForTest() {
        setGenesisInner(true);
    }

    private void setGenesisInner(boolean forTest) {
        try {
            this.genesis = GenesisBlockLoader.loadJSON(getGenesisFile().getAbsolutePath());
        } catch (IOException e) {
            System.out.println(String.format("Genesis load exception %s", e.getMessage()));
            System.out.println("defaulting to default AionGenesis configuration");
            try {
                this.genesis =
                        forTest
                                ? (new AionGenesis.Builder()).buildForTest()
                                : (new AionGenesis.Builder()).build();
            } catch (Exception e2) {
                // if this fails, it means our DEFAULT genesis violates header rules
                // this is catastrophic
                System.out.println("load default AionGenesis runtime failed! " + e2.getMessage());
                throw new RuntimeException(e2);
            }
        }
    }

    public void setGenesis(AionGenesis genesis) {
        this.genesis = genesis;
    }

    public CfgConsensusUnity getConsensus() {
        return this.consensus;
    }

    public AionGenesis getGenesis() {
        if (this.genesis == null) setGenesis();
        return this.genesis;
    }

    public static int getN() {
        return N;
    }

    public static int getK() {
        return K;
    }

    private void closeFileInputStream(final FileInputStream fis) {
        if (fis != null) {
            try {
                fis.close();
            } catch (IOException e) {
                System.out.println("<error on-close-file-input-stream>");
                System.exit(SystemExitCodes.INITIALIZATION_ERROR);
            }
        }
    }

    //    /** @implNote the default fork settings is looking for the fork config of the mainnet. */
    //    public void setForkProperties() {
    //        setForkProperties("mainnet", null);
    //    }

    public void setForkProperties(String networkName, File forkFile) {
        // old kernel doesn't support the fork feature.
        if (networkName == null || networkName.equals("config")) {
            return;
        }

        ProtocolUpgradeSettings protocolSettings;
        try {
            if (forkFile == null) {
                String path = System.getProperty("user.dir")
                    + "/"
                    + networkName
                    + "/config"
                    + CfgFork.FORK_PROPERTIES_PATH;

                protocolSettings = ForkPropertyLoader.loadJSON(path);
            } else {
                protocolSettings = ForkPropertyLoader.loadJSON(forkFile.getPath());
            }

            this.getFork().setProtocolUpgradeSettings(protocolSettings.upgrade, protocolSettings.rollbackTransactionHash);
        } catch (Exception e) {
            System.out.println(
                    "<error on-parsing-fork-properties msg="
                            + e.getLocalizedMessage()
                            + ">, no protocol been updated.");
        }
    }

    public void dbFromXML() {
        File cfgFile = getInitialConfigFile();
        XMLInputFactory input = XMLInputFactory.newInstance();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(cfgFile);
            XMLStreamReader sr = input.createXMLStreamReader(fis);
            loop:
            while (sr.hasNext()) {
                int eventType = sr.next();
                switch (eventType) {
                    case XMLStreamReader.START_ELEMENT:
                        String elementName = sr.getLocalName().toLowerCase();
                        switch (elementName) {
                            case "db":
                                this.db.fromXML(sr);
                                break;
                            default:
                                ConfigUtil.skipElement(sr);
                                break;
                        }
                        break;
                    case XMLStreamReader.END_ELEMENT:
                        if (sr.getLocalName().toLowerCase().equals("aion")) break loop;
                        else break;
                }
            }
        } catch (Exception e) {
            System.out.println("<error on-parsing-config-xml msg=" + e.getLocalizedMessage() + ">");
            System.exit(SystemExitCodes.INITIALIZATION_ERROR);
        } finally {
            closeFileInputStream(fis);
        }
    }

    public boolean fromXML(final XMLStreamReader sr) throws XMLStreamException {
        boolean shouldWriteBackToFile = false;
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "id":
                            String nodeId = ConfigUtil.readValue(sr);
                            if (NODE_ID_PLACEHOLDER.equals(nodeId)) {
                                this.id = UUID.randomUUID().toString();
                                shouldWriteBackToFile = true;
                            } else {
                                this.id = nodeId;
                            }
                            break;
                        case "mode":
                            this.mode = ConfigUtil.readValue(sr);
                            break;
                        case "api":
                            this.api.fromXML(sr);
                            break;
                        case "net":
                            this.net.fromXML(sr);
                            break;
                        case "sync":
                            this.sync.fromXML(sr);
                            break;
                        case "consensus":
                            this.consensus.fromXML(sr);
                            break;
                        case "db":
                            this.db.fromXML(sr);
                            break;
                        case "log":
                            this.log.fromXML(sr);
                            break;
                        case "tx":
                            this.tx.fromXML(sr);
                            break;
                        case "reports":
                            this.reports.fromXML(sr);
                            break;
                        case "gui":
                            // TODO: [GUI] disable GUI config features
                            // this.gui.fromXML(sr);
                            break;
                        default:
                            ConfigUtil.skipElement(sr);
                            break;
                    }
                    break;
                case XMLStreamReader.END_ELEMENT:
                    if (sr.getLocalName().toLowerCase().equals("aion")) break loop;
                    else break;
            }
        }
        return shouldWriteBackToFile;
    }

    public boolean fromXML() {
        return fromXML(getInitialConfigFile());
    }

    public boolean fromXML(File cfgFile) {
        boolean shouldWriteBackToFile = false;
        if (!cfgFile.exists()) {
            return false;
        }
        XMLInputFactory input = XMLInputFactory.newInstance();
        FileInputStream fis;
        try {
            fis = new FileInputStream(cfgFile);
            XMLStreamReader sr = input.createXMLStreamReader(fis);
            shouldWriteBackToFile = fromXML(sr);

            // seedMode migration compatibility
            Boolean seedMode = consensus.getSeedMode();
            if (seedMode != null) {
                tx.setSeedMode(seedMode);
            }

            closeFileInputStream(fis);
        } catch (Exception e) {
            System.out.println("<error on-parsing-config-xml msg=" + e.getLocalizedMessage() + ">");
            System.exit(SystemExitCodes.INITIALIZATION_ERROR);
        }

        // checks for absolute path for database
        File db = new File(this.getDb().getPath());
        if (db.isAbsolute()) {
            this.setDatabaseDir(db);
        }

        // checks for absolute path for log
        File log = new File(this.getLog().getLogPath());
        if (log.isAbsolute()) {
            this.setLogDir(log);
        }

        if (keystorePath != null) {
            File ks = new File(keystorePath);
            if (ks.isAbsolute()) {
                this.setKeystoreDir(ks);
            }
        }

        return shouldWriteBackToFile;
    }

    public void toXML(final String[] args) {
        toXML(args, getExecConfigFile());
    }

    public void toXML(final String[] args, File file) {
        if (args != null) {
            boolean override = false;
            for (String arg : args) {
                arg = arg.toLowerCase();
                if (arg.startsWith("--id=")) {
                    override = true;
                    String id = arg.replace("--id=", "");
                    try {
                        UUID uuid = UUID.fromString(id);
                        this.id = uuid.toString();
                    } catch (IllegalArgumentException exception) {
                        System.out.println("<invalid-id-arg id=" + id + ">");
                    }
                }
                if (arg.startsWith("--nodes=")) {
                    override = true;
                    String[] subArgsArr = arg.replace("--nodes=", "").split(",");
                    if (subArgsArr.length > 0) {
                        List<String> _nodes = new ArrayList<>();
                        for (String subArg : subArgsArr) {
                            if (!subArg.equals("")) {
                                _nodes.add(subArg);
                            }
                        }
                        this.getNet().setNodes(_nodes.toArray(new String[0]));
                    }
                }
                if (arg.startsWith("--p2p=")) {
                    override = true;
                    String[] subArgsArr = arg.replace("--p2p=", "").split(",");
                    if (subArgsArr.length == 2) {
                        if (!subArgsArr[0].equals("")) {
                            this.getNet().getP2p().setIp(subArgsArr[0]);
                        }
                        this.getNet().getP2p().setPort(Integer.parseInt(subArgsArr[1]));
                    }
                }
                if (arg.startsWith("--log=")) {
                    override = true;
                    String subArgs = arg.replace("--log=", "");
                    String[] subArgsArr = subArgs.split(",");
                    for (int i1 = 0, max1 = subArgsArr.length; i1 < max1; i1++) {
                        if ((i1 + 1) < max1) {
                            String _module = subArgsArr[i1].toUpperCase();
                            String _level = subArgsArr[++i1].toUpperCase();
                            // ensures the LogEnum can be decoded
                            if (LogEnum.contains(_module)) {
                                // ensures the LogLevel can be decoded
                                if (LogLevel.contains(_level)) {
                                    this.log.getModules().put(LogEnum.valueOf(_module), LogLevel.valueOf(_level));
                                } else {
                                    // default for incorrect levels
                                    this.log.getModules().put(LogEnum.valueOf(_module), LogLevel.WARN);
                                }
                            }
                        }
                    }
                }
            }
            if (override) System.out.println("Config Override");
        }

        XMLOutputFactory output = XMLOutputFactory.newInstance();
        output.setProperty("escapeCharacters", false);
        XMLStreamWriter sw = null;

        try {

            sw = output.createXMLStreamWriter(new FileWriter(file));
            sw.writeStartDocument("utf-8", "1.0");
            sw.writeCharacters("\r\n");
            sw.writeStartElement("aion");

            sw.writeCharacters("\r\n\t");
            sw.writeStartElement("mode");
            sw.writeCharacters(this.getMode());
            sw.writeEndElement();

            sw.writeCharacters("\r\n\t");
            sw.writeStartElement("id");
            sw.writeCharacters(this.getId());
            sw.writeEndElement();

            if (keystorePath != null) {
                sw.writeCharacters("\r\n\t");
                sw.writeStartElement("keystore");
                sw.writeCharacters(keystorePath);
                sw.writeEndElement();
            }

            sw.writeCharacters(this.getApi().toXML());
            sw.writeCharacters(this.getNet().toXML());
            sw.writeCharacters(this.getSync().toXML());
            sw.writeCharacters(this.getConsensus().toXML());
            sw.writeCharacters(this.getDb().toXML());
            sw.writeCharacters(this.getLog().toXML());
            sw.writeCharacters(this.getTx().toXML());
            sw.writeCharacters(this.getReports().toXML());
            // TODO: [GUI] disable GUI config features
            //sw.writeCharacters(this.getGui().toXML());

            sw.writeCharacters("\r\n");
            sw.writeEndElement();
            sw.flush();
            sw.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("<error on-write-config-xml-to-file>");
            System.exit(SystemExitCodes.INITIALIZATION_ERROR);
        } finally {
            if (sw != null) {
                try {
                    sw.close();
                } catch (XMLStreamException e) {
                    System.out.println("<error on-close-stream-writer>");
                    System.exit(SystemExitCodes.INITIALIZATION_ERROR);
                }
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CfgAion cfgAion = (CfgAion) o;
        return Objects.equal(genesis, cfgAion.genesis);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(genesis);
    }

    public void setId(final String _id) {
        this.id = _id;
    }

    public void setNet(final CfgNet _net) {
        this.net = _net;
    }

    public void setApi(final CfgApi _api) {
        this.api = _api;
    }

    public void setDb(final CfgDb _db) {
        this.db = _db;
    }

    public void setLog(final CfgLog _log) {
        this.log = _log;
    }

    public void setTx(final CfgTx _tx) {
        this.tx = _tx;
    }

    public String getId() {
        return this.id;
    }

    protected String getMode() {
        return this.mode;
    }

    public CfgNet getNet() {
        return this.net;
    }

    public CfgSync getSync() {
        return this.sync;
    }

    public CfgApi getApi() {
        return this.api;
    }

    public CfgDb getDb() {
        return this.db;
    }

    public CfgLog getLog() {
        return this.log;
    }

    public CfgTx getTx() {
        return this.tx;
    }

    public CfgReports getReports() {
        return this.reports;
    }

    // TODO: [GUI] disable GUI config features
    public CfgGui getGui() {
        throw new UnsupportedOperationException();
    }

    public CfgFork getFork() {
        return this.fork;
    }

    public String[] getNodes() {
        return this.net.getNodes();
    }

    public void setConsensus(CfgConsensusUnity _consensus) {
        this.consensus = _consensus;
    }

    /** Resets internal data containing network and path. */
    @VisibleForTesting
    public void resetInternal() {
        networkConfigDir = null;
        baseConfigFile = null;
        baseGenesisFile = null;
        baseForkFile = null;
        logDir = null;
        databaseDir = null;
        keystoreDir = null;
        absoluteLogDir = false;
        absoluteDatabaseDir = false;
        absoluteKeystoreDir = false;
        network = null;
        dataDir = null;
        execDir = null;
        execConfigDir = null;
        execConfigFile = null;
    }

    /**
     * Initializes the base configuration and execution paths using the selected network:
     * <ul>
     *     <li>the config file is copied to the execution path</li>
     *     <li>the genesis and fork files are read from the initial configuration</li>
     * </ul>
     */
    protected void initializeConfiguration() {
        if (network == null) {
            network = "mainnet";
        }
        networkConfigDir = new File(CONFIG_DIR, network);
        baseConfigFile = new File(networkConfigDir, configFileName);
        baseGenesisFile = new File(networkConfigDir, genesisFileName);
        baseForkFile = new File(networkConfigDir, forkFileName);

        if (dataDir == null) {
            dataDir = INITIAL_PATH;
        }
        execDir = new File(dataDir, network);
        execConfigDir = new File(execDir, configDirName);
        execConfigFile = new File(execConfigDir, configFileName);

        updateStoragePaths();
        if (baseForkFile.exists()) {
            setForkProperties(network, baseForkFile);
        }
    }

    /** Updates the path to the log, database directories. */
    private void updateStoragePaths() {
        if (!absoluteLogDir) {
            logDir = new File(execDir, getLog().getLogPath());
        } else if (logDir == null) {
            logDir = new File(getLog().getLogPath());
        }
        if (!absoluteDatabaseDir) {
            databaseDir = new File(execDir, getDb().getPath());
        } else if (databaseDir == null) {
            databaseDir = new File(getDb().getPath());
        }
        if (!absoluteKeystoreDir) {
            if (keystorePath != null) {
                // absolute paths are set when reading the file
                // so this must be a relative path
                keystoreDir = new File(execDir, keystorePath);
            } else {
                // path not set so using defaults
                keystoreDir = new File(execDir, keystoreDirName);
            }
        } else if (keystoreDir == null) {
            keystoreDir = new File(keystorePath);
        }
    }

    /**
     * Sets the directory where the kernel data containing setup and execution information will be
     * stored.
     *
     * @param _dataDir the directory chosen for execution
     * @implNote Using this method overwrites the use of old kernel setup.
     */
    public void setDataDirectory(File _dataDir) {
        this.dataDir = _dataDir;
        initializeConfiguration();
    }

    /**
     * Sets the network to be used by the kernel.
     *
     * @param _network the network chosen for execution
     * @implNote Using this method overwrites the use of old kernel setup.
     */
    public void setNetwork(String _network) {
        this.network = _network;
        initializeConfiguration();
    }

    /** @return the base dir where all configuration + persistence is managed */
    public String getBasePath() {
        return getExecDir().getAbsolutePath();
    }

    /** Returns the directory location where the kernel configuration and persistence is managed. */
    public File getExecDir() {
        if (execDir == null) {
            initializeConfiguration();
        }
        return execDir;
    }

    public String getNetwork() {
        return network;
    }

    public String getLogPath() {
        return getLogDir().getAbsolutePath();
    }

    public File getLogDir() {
        if (logDir == null) {
            // was not updated with absolute path
            logDir = new File(getExecDir(), getLog().getLogPath());
        }
        return logDir;
    }

    /**
     * Used to set an absolute path for the log directory.
     *
     * @param _logDirectory the path to be used for logging.
     */
    public void setLogDir(File _logDirectory) {
        this.logDir = _logDirectory;
        this.absoluteLogDir = true;
    }

    public String getDatabasePath() {
        return getDatabaseDir().getAbsolutePath();
    }

    public File getDatabaseDir() {
        if (databaseDir == null) {
            // was not updated with absolute path
            databaseDir = new File(getExecDir(), getDb().getPath());
        }
        return databaseDir;
    }

    /**
     * Used to set an absolute path for the database directory.
     *
     * @param _databaseDirectory the path to be used for storing the database.
     */
    public void setDatabaseDir(File _databaseDirectory) {
        this.databaseDir = _databaseDirectory;
        this.absoluteDatabaseDir = true;
    }

    /**
     * Used to set an absolute path for the keystore directory.
     *
     * @param _keystoreDirectory the path to be used for the keystore.
     */
    public void setKeystoreDir(File _keystoreDirectory) {
        this.keystoreDir = _keystoreDirectory;
        this.absoluteKeystoreDir = true;
    }

    public File getKeystoreDir() {
        if (keystoreDir == null) {
            // was not updated with absolute path
            keystoreDir =
                new File(getExecDir(), keystorePath != null ? keystorePath : keystoreDirName);
        }
        return keystoreDir;
    }

    /** Returns the configuration directory location for the kernel execution. */
    public File getExecConfigDirectory() {
        if (execConfigDir == null) {
            initializeConfiguration();
        }
        return execConfigDir;
    }

    /** Returns the location where the config file is saved for kernel execution. */
    public File getExecConfigFile() {
        if (execConfigFile == null) {
            initializeConfiguration();
        }
        return execConfigFile;
    }

    /** @implNote Maintains the old setup if the config file is present in the old location. */
    public File getInitialConfigFile() {
        if (baseConfigFile == null) {
            initializeConfiguration();
        }
        return baseConfigFile;
    }

    /**
     * Used to updated the initial configuration to using the execution configuration files when
     * reading the initial configuration from those files.
     */
    public void setReadConfigFile(File configFile) {
        this.baseConfigFile = configFile;
    }

    /** @implNote Maintains the old setup if the genesis file is present in the old location. */
    public File getGenesisFile() {
        if (baseGenesisFile == null) {
            initializeConfiguration();
        }
        return baseGenesisFile;
    }

    public File getForkFile() {
        if (baseForkFile == null) {
            initializeConfiguration();
        }
        return baseForkFile;
    }
}
