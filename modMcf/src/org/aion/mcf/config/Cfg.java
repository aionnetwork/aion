/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.mcf.config;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.aion.mcf.types.AbstractBlock;

/**
 * @author chris
 */
public abstract class Cfg {

    private static final String BASE_PATH = System.getProperty("user.dir");

    protected static final String CONF_FILE_PATH = BASE_PATH + "/config/config.xml";

    protected static final String GENESIS_FILE_PATH = BASE_PATH + "/config/genesis.json";

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

    public void setId(final String _id){
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

    /**
     * @return the base dir where all configuration + persistance is managed
     */
    public String getBasePath() {
        return BASE_PATH;
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
     * @return boolean
     * use return to determine if also need to write back
     * to file with current config
     */
    public abstract boolean fromXML();

    public abstract void toXML(final String[] args);

    public abstract void setGenesis();

    public abstract AbstractBlock<?, ?> getGenesis();

}