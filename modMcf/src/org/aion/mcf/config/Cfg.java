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

import java.io.File;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.aion.mcf.types.AbstractBlock;

/** @author chris */
public abstract class Cfg {

    private final String configDirectoryName = "config";
    private final String configFileName = "config.xml";
    private final String genesisFileName = "genesis.json";
    private final String keystoreDirectoryName = "keystore";

    private File logDirectory = null;
    private File databaseDirectory = null;
    private File execDirectory = null;
    private File configFile = null;
    private File genesisFile = null;

    private String network = "mainnet";

    protected String BASE_PATH = System.getProperty("user.dir");
    protected final String INITIAL_PATH = System.getProperty("user.dir");
    protected final File oldConfigDir = new File(INITIAL_PATH, configDirectoryName);

    /** @implNote modified only from {@link #setNetwork(String)} */
    protected File newConfigDir = new File(oldConfigDir, network);

    private boolean useOldSetup = false;
    private boolean ignoreOldSetup = false;

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

    public String[] getNodes() {
        return this.net.getNodes();
    }

    public CfgConsensus getConsensus() {
        return this.consensus;
    }

    public void setConsensus(CfgConsensus _consensus) {
        this.consensus = _consensus;
    }

    /** @return the base dir where all configuration + persistence is managed */
    public String getBasePath() {
        return getExecDirectory().getAbsolutePath();
    }

    /** Resets internal data containing network and path. */
    public void resetInternal() {
        logDirectory = null;
        databaseDirectory = null;
        execDirectory = null;
        genesisFile = null;
        configFile = null;
        network = "mainnet";

        BASE_PATH = INITIAL_PATH;

        useOldSetup = false;
        ignoreOldSetup = false;
    }

    /** Returns the directory location where the kernel configuration and persistence is managed. */
    public File getExecDirectory() {
        if ((!ignoreOldSetup && useOldSetup) || execDirectory == null) {
            return new File(INITIAL_PATH);
        } else {
            return new File(execDirectory, network.toString());
        }
    }

    /**
     * Sets the directory where the kernel data containing setup and execution information will be
     * stored.
     *
     * @param _execDirectory the directory chosen for execution
     * @implNote Using this method overwrites the use of old kernel setup.
     */
    public void setExecDirectory(File _execDirectory) {
        this.execDirectory = _execDirectory;
        // default network when only datadir is set
        this.ignoreOldSetup = true;
    }

    /**
     * Sets the network to be used by the kernel.
     *
     * @param _network the network chosen for execution
     * @implNote Using this method overwrites the use of old kernel setup.
     */
    public void setNetwork(String _network) {
        this.network = _network;
        this.newConfigDir = new File(oldConfigDir, network);
        this.execDirectory = new File(INITIAL_PATH);
        this.ignoreOldSetup = true;
    }

    public String getNetwork() {
        return network;
    }

    public String getLogPath() {
        return getLogDirectory().getAbsolutePath();
    }

    public File getLogDirectory() {
        if (logDirectory != null) {
            // set when using absolute paths
            return logDirectory;
        } else {
            return new File(getExecDirectory(), getLog().getLogPath());
        }
    }

    public void setLogDirectory(File _logDirectory) {
        this.logDirectory = _logDirectory;
    }

    public String getDatabasePath() {
        return getDatabaseDirectory().getAbsolutePath();
    }

    public File getDatabaseDirectory() {
        if (databaseDirectory != null) {
            // set when using absolute paths
            return databaseDirectory;
        } else {
            return new File(getExecDirectory(), getDb().getPath());
        }
    }

    public void setDatabaseDirectory(File _databaseDirectory) {
        this.databaseDirectory = _databaseDirectory;
    }

    public String getKeystorePath() {
        return getKeystoreDirectory().getAbsolutePath();
    }

    public File getKeystoreDirectory() {
        return new File(getBasePath(), keystoreDirectoryName);
    }

    /** Returns the configuration directory location for the kernel execution. */
    public File getExecConfigDirectory() {
        // TODO check different input
        return new File(getExecDirectory(), configDirectoryName);
    }

    /** Returns the location where the config file is saved for kernel execution. */
    public File getExecConfigFile() {
        return new File(getExecConfigDirectory(), configFileName);
    }

    /** Returns the location where the config file is saved for kernel execution. */
    public String getExecConfigPath() {
        return getExecConfigFile().getAbsolutePath();
    }

    /** Returns the location where the genesis file is saved for kernel execution. */
    public File getExecGenesisFile() {
        return new File(getExecConfigDirectory(), genesisFileName);
    }

    /** Returns the location where the genesis file is saved for kernel execution. */
    public String getExecGenesisPath() {
        return getExecGenesisFile().getAbsolutePath();
    }

    /** @implNote Maintains the old setup if the config file is present in the old location. */
    public File getInitialConfigFile() {
        if (configFile == null) {
            // TODO-Ale: may want to consider exec path as well
            // use old config location for compatibility with old kernels
            File config = new File(oldConfigDir, configFileName);

            // TODO: read mainnet config when ignore set
            if (ignoreOldSetup || !config.exists()) {
                if (execDirectory == null) {
                    execDirectory = new File(INITIAL_PATH);
                }
                config = getExecConfigFile();
                if (!config.exists()) {
                    config = new File(newConfigDir, configFileName);
                }
            } else {
                useOldSetup = true;
            }
            configFile = config;
        }
        return configFile;
    }

    /** @implNote Maintains the old setup if the config file is present in the old location. */
    public String getInitialConfigPath() {
        return getInitialConfigFile().getAbsolutePath();
    }

    public File getInitialGenesisFile() {
        if (genesisFile == null) {
            // use old genesis location for compatibility with old kernels
            File genesis = new File(oldConfigDir, genesisFileName);

            if (ignoreOldSetup || !genesis.exists()) {
                if (execDirectory == null) {
                    execDirectory = new File(INITIAL_PATH);
                }
                genesis = getExecGenesisFile();
                if (!genesis.exists()) {
                    genesis = new File(newConfigDir, genesisFileName);
                }
            }
            genesisFile = genesis;
        }
        return genesisFile;
    }

    /** @implNote Maintains the old setup if the config file is present in the old location. */
    public String getInitialGenesisPath() {
        return getInitialGenesisFile().getAbsolutePath();
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
}
