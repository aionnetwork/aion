/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.mcf.config;

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.aion.mcf.types.AbstractBlock;

/** @author chris */
public abstract class Cfg {

    protected String mode;

    protected String id;

    protected CfgApi api;

    protected CfgNet net;

    protected CfgConsensus consensus;

    protected CfgSync sync;

    protected CfgDb db;

    protected CfgLog log;

    protected CfgTx tx;

    protected CfgReports reports;

    protected CfgGui gui;

    protected CfgFork fork;

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

    public CfgGui getGui() {
        return this.gui;
    }

    public CfgFork getFork() {
        return this.fork;
    }

    public String[] getNodes() {
        return this.net.getNodes();
    }

    public CfgConsensus getConsensus() {
        return this.consensus;
    }

    public void setConsensus(CfgConsensus _consensus) {
        this.consensus = _consensus;
    }

    /* ------------ execution path management ------------ */

    // names
    private final String configDirName = "config";
    private final String configFileName = "config.xml";
    private final String genesisFileName = "genesis.json";
    private final String keystoreDirName = "keystore";
    private final String forkFileName = "fork.properties";

    // base path
    private final File INITIAL_PATH = new File(System.getProperty("user.dir"));

    // directories containing the configuration files
    private final File CONFIG_DIR = new File(INITIAL_PATH, configDirName);
    private File networkConfigDir = null;

    // base configuration: old kernel OR using network config
    private File baseConfigFile = null;
    private File baseGenesisFile = null;
    private File baseForkFile = null;


    // can be absolute in config file OR depend on execution path
    private File logDir = null;
    private File databaseDir = null;
    private boolean absoluteLogDir = false;
    private boolean absoluteDatabaseDir = false;

    // impact execution path
    private String network = null;
    private File dataDir = null;

    /** Data directory with network. */
    private File execDir = null;

    private File execConfigDir = null;
    private File execConfigFile = null;
    private File execGenesisFile = null;
    private File execForkFile = null;

    /** Resets internal data containing network and path. */
    @VisibleForTesting
    public void resetInternal() {
        networkConfigDir = null;
        baseConfigFile = null;
        baseGenesisFile = null;
        baseForkFile = null;
        logDir = null;
        databaseDir = null;
        absoluteLogDir = false;
        absoluteDatabaseDir = false;
        network = null;
        dataDir = null;
        execDir = null;
        execConfigDir = null;
        execConfigFile = null;
        execGenesisFile = null;
        execForkFile = null;
    }

    /**
     * Determines the location of the initial configuration files ensuring compatibility with old
     * kernels.
     */
    protected void initializeConfiguration() {
        // use old config location for compatibility with old kernels
        baseConfigFile = new File(CONFIG_DIR, configFileName);
        baseGenesisFile = new File(CONFIG_DIR, genesisFileName);


        if (!baseConfigFile.exists() || !baseGenesisFile.exists()) {
            updateNetworkExecPaths();
        } else {
            execDir = INITIAL_PATH;
            execConfigDir = CONFIG_DIR;
            execConfigFile = baseConfigFile;
            execGenesisFile = baseGenesisFile;
            updateStoragePaths();
        }
    }

    /**
     * Updates the base configuration and execution paths as is defined by <b>new kernels</b> where
     * the configuration is placed in folders for each network type.
     */
    private void updateNetworkExecPaths() {
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
        execGenesisFile = new File(execConfigDir, genesisFileName);
        execForkFile = new File(execConfigDir, forkFileName);

        updateStoragePaths();
        if (execForkFile.exists()) {
            setForkProperties(network, execForkFile);
        } else if (baseForkFile.exists()) {
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
        updateNetworkExecPaths();
    }

    /**
     * Sets the network to be used by the kernel.
     *
     * @param _network the network chosen for execution
     * @implNote Using this method overwrites the use of old kernel setup.
     */
    public void setNetwork(String _network) {
        this.network = _network;
        updateNetworkExecPaths();
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

    public File getKeystoreDir() {
        return new File(getExecDir(), keystoreDirName);
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

    /** Returns the location where the genesis file is saved for kernel execution. */
    public File getExecGenesisFile() {
        if (execGenesisFile == null) {
            initializeConfiguration();
        }
        return execGenesisFile;
    }

    /** Returns the location where the fork file is saved for kernel execution. */
    public File getExecForkFile() {
        if (execForkFile == null) {
            initializeConfiguration();
        }
        return execForkFile;
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
    public void setReadConfigFiles(File configFile, File genesisFile) {
        this.baseConfigFile = configFile;
        this.baseGenesisFile = genesisFile;
    }

    /** @implNote Maintains the old setup if the genesis file is present in the old location. */
    public File getInitialGenesisFile() {
        if (baseGenesisFile == null) {
            initializeConfiguration();
        }
        return baseGenesisFile;
    }

    public File getInitialForkFile() {
        if (baseForkFile == null) {
            initializeConfiguration();
        }
        return baseForkFile;
    }

    public static String readValue(final XMLStreamReader sr) throws XMLStreamException {
        StringBuilder str = new StringBuilder();
        readLoop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.CHARACTERS:
                    str.append(sr.getText());
                    break;
                case XMLStreamReader.END_ELEMENT:
                    break readLoop;
            }
        }
        return str.toString();
    }

    public static void skipElement(final XMLStreamReader sr) throws XMLStreamException {
        skipLoop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.END_ELEMENT:
                    break skipLoop;
            }
        }
    }

    /**
     * Loads the configuration from the default config file. Returns a boolean value used to
     * determine if the configuration needs to be saved back to disk with a valid peer identifier.
     *
     * @return {@code true} when the peer id read from the file is [NODE-ID-PLACEHOLDER] which needs
     *     to be replaced by a valid user ID on disk, {@code false} otherwise.
     */
    public abstract boolean fromXML();

    /**
     * Loads the configuration from the given config file.
     *
     * <p>Returns a boolean value used to determine if the configuration needs to be saved back to
     * disk with a valid peer identifier.
     *
     * @return {@code true} when the peer id read from the file is [NODE-ID-PLACEHOLDER] which needs
     *     to be replaced by a valid user ID on disk, {@code false} otherwise.
     */
    public abstract boolean fromXML(File configFile);

    public abstract void toXML(final String[] args);

    public abstract void toXML(final String[] args, File file);

    public abstract void setGenesis();

    public abstract AbstractBlock<?, ?> getGenesis();

    public abstract void setForkProperties(String network, File forkFile);
}
