/*******************************************************************************
 *
 * Copyright (c) 2017, 2018 Aion foundation.
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 *******************************************************************************/
package org.aion.mcf.config;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.aion.mcf.types.AbstractBlock;

/**
 * abstract configuration class.
 *
 * @author chris
 */
public abstract class Cfg {

    private static final String BASE_PATH = System.getProperty("user.dir");

    public static final String CONF_FILE_PATH = BASE_PATH + "/config/config.xml";

    public static final String GENESIS_FILE_PATH = BASE_PATH + "/config/genesis.json";

    protected String mode;

    protected String id;

    protected String version;

    protected CfgApi api;

    protected CfgNet net;

    protected CfgConsensus consensus;

    protected CfgSync sync;

    protected CfgDb db;

    protected CfgLog log;

    public void setNet(CfgNet _net) {
        this.net = _net;
    }

    public void setApi(CfgApi _api) {
        this.api = _api;
    }

    public void setDb(CfgDb _db) {
        this.db = _db;
    }

    public void setLog(CfgLog _log) {
        this.log = _log;
    }

    public String getVersion() {
        return this.version;
    }

    public String getId() {
        return this.id;
    }

    public String getMode() {
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

    public abstract void fromXML();

    public abstract void toXML(final String[] args);

    public abstract void setGenesis();

    public abstract AbstractBlock<?, ?> getGenesis();

}