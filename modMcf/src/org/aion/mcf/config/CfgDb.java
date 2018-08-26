/* ******************************************************************************
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
 ******************************************************************************/
package org.aion.mcf.config;

import static org.aion.db.impl.DatabaseFactory.Props;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.google.common.base.Objects;
import org.aion.base.util.Utils;
import org.aion.db.impl.DBVendor;

/** @author chris */
public class CfgDb {

    public static class Names {
        public static final String DEFAULT = "default";

        public static final String BLOCK = "block";
        public static final String INDEX = "index";

        public static final String DETAILS = "details";
        public static final String STORAGE = "storage";

        public static final String STATE = "state";
        public static final String STATE_ARCHIVE = "stateArchive";
        public static final String TRANSACTION = "transaction";

        public static final String TX_CACHE = "pendingtxCache";
        public static final String TX_POOL = "pendingtxPool";
    }

    private String path;
    private String vendor;
    private boolean compression;
    private boolean check_integrity;
    private CfgPrune prune;
    private PruneOption prune_option;

    /**
     * Enabling expert mode allows more detailed database configurations.
     *
     * @implNote This parameter must remain hardcoded.
     */
    private boolean expert = false;

    // individual db configurations
    private Map<String, CfgDbDetails> specificConfig;

    public CfgDb() {
        this.path = "database";
        this.vendor = DBVendor.LEVELDB.toValue();
        this.compression = false;
        this.check_integrity = true;
        this.prune = new CfgPrune(false);
        this.prune_option = PruneOption.FULL;

        if (expert) {
            this.specificConfig = new HashMap<>();
            this.specificConfig.put(Names.DEFAULT, new CfgDbDetails());
        }
    }

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "path":
                            this.path = Cfg.readValue(sr);
                            break;
                        case "check_integrity":
                            this.check_integrity = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case "state-storage":
                            setPrune(Cfg.readValue(sr));
                            break;
                            // parameter considered only when expert==false
                        case "vendor":
                            this.vendor = Cfg.readValue(sr);
                            break;
                            // parameter considered only when expert==false
                        case Props.ENABLE_DB_COMPRESSION:
                            this.compression = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                            // parameter considered only when expert==true
                        case Names.DEFAULT:
                            {
                                CfgDbDetails dbConfig = this.specificConfig.get(Names.DEFAULT);
                                dbConfig.fromXML(sr);
                                this.specificConfig.put(Names.DEFAULT, dbConfig);
                                break;
                            }
                            // parameter considered only when expert==true
                        case Names.BLOCK:
                            {
                                CfgDbDetails dbConfig = new CfgDbDetails();
                                dbConfig.fromXML(sr);
                                this.specificConfig.put(Names.BLOCK, dbConfig);
                                break;
                            }
                            // parameter considered only when expert==true
                        case Names.INDEX:
                            {
                                CfgDbDetails dbConfig = new CfgDbDetails();
                                dbConfig.fromXML(sr);
                                this.specificConfig.put(Names.INDEX, dbConfig);
                                break;
                            }
                            // parameter considered only when expert==true
                        case Names.DETAILS:
                            {
                                CfgDbDetails dbConfig = new CfgDbDetails();
                                dbConfig.fromXML(sr);
                                this.specificConfig.put(Names.DETAILS, dbConfig);
                                break;
                            }
                            // parameter considered only when expert==true
                        case Names.STORAGE:
                            {
                                CfgDbDetails dbConfig = new CfgDbDetails();
                                dbConfig.fromXML(sr);
                                this.specificConfig.put(Names.STORAGE, dbConfig);
                                break;
                            }
                            // parameter considered only when expert==true
                        case Names.STATE:
                            {
                                CfgDbDetails dbConfig = new CfgDbDetails();
                                dbConfig.fromXML(sr);
                                this.specificConfig.put(Names.STATE, dbConfig);
                                break;
                            }
                            // parameter considered only when expert==true
                        case Names.TRANSACTION:
                            {
                                CfgDbDetails dbConfig = new CfgDbDetails();
                                dbConfig.fromXML(sr);
                                this.specificConfig.put(Names.TRANSACTION, dbConfig);
                                break;
                            }
                            // parameter considered only when expert==true
                        case Names.TX_POOL:
                            {
                                CfgDbDetails dbConfig = new CfgDbDetails();
                                dbConfig.fromXML(sr);
                                this.specificConfig.put(Names.TX_POOL, dbConfig);
                                break;
                            }
                            // parameter considered only when expert==true
                        case Names.TX_CACHE:
                            {
                                CfgDbDetails dbConfig = new CfgDbDetails();
                                dbConfig.fromXML(sr);
                                this.specificConfig.put(Names.TX_CACHE, dbConfig);
                                break;
                            }
                        default:
                            Cfg.skipElement(sr);
                            break;
                    }
                    break;
                case XMLStreamReader.END_ELEMENT:
                    break loop;
            }
        }
    }

    public String toXML() {
        final XMLOutputFactory output = XMLOutputFactory.newInstance();
        XMLStreamWriter xmlWriter;
        String xml;
        try {
            Writer strWriter = new StringWriter();
            xmlWriter = output.createXMLStreamWriter(strWriter);
            xmlWriter.writeCharacters("\r\n\t");
            xmlWriter.writeStartElement("db");

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeComment("Sets the physical location on disk where data will be stored.");
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("path");
            xmlWriter.writeCharacters(this.getPath());
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeComment(
                    "Boolean value. Enable/disable database integrity check run at startup.");
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("check_integrity");
            xmlWriter.writeCharacters(String.valueOf(this.check_integrity));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeComment(
                    "Data pruning behavior for the state database. Options: FULL, TOP, SPREAD.");
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeComment("FULL: the state is not pruned");
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeComment(
                    "TOP: the state is kept only for the top K blocks; limits sync to branching only within the stored blocks");
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeComment(
                    "SPREAD: the state is kept for the top K blocks and at regular block intervals");
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("state-storage");
            xmlWriter.writeCharacters(this.prune_option.toString());
            xmlWriter.writeEndElement();

            if (!expert) {
                xmlWriter.writeCharacters("\r\n\t\t");
                xmlWriter.writeComment(
                        "Database implementation used to store data; supported options: leveldb, h2, rocksdb.");
                xmlWriter.writeCharacters("\r\n\t\t");
                xmlWriter.writeComment(
                        "Caution: changing implementation requires re-syncing from genesis!");
                xmlWriter.writeCharacters("\r\n\t\t");
                xmlWriter.writeStartElement("vendor");
                xmlWriter.writeCharacters(this.vendor);
                xmlWriter.writeEndElement();

                xmlWriter.writeCharacters("\r\n\t\t");
                xmlWriter.writeComment(
                        "Boolean value. Enable/disable database compression to trade storage space for execution time.");
                xmlWriter.writeCharacters("\r\n\t\t");
                xmlWriter.writeStartElement(Props.ENABLE_DB_COMPRESSION);
                xmlWriter.writeCharacters(String.valueOf(this.compression));
                xmlWriter.writeEndElement();
            } else {
                for (Map.Entry<String, CfgDbDetails> entry : specificConfig.entrySet()) {
                    entry.getValue().toXML(entry.getKey(), xmlWriter, expert);
                }
            }

            xmlWriter.writeCharacters("\r\n\t");
            xmlWriter.writeEndElement();
            xml = strWriter.toString();
            strWriter.flush();
            strWriter.close();
            xmlWriter.flush();
            xmlWriter.close();
            return xml;
        } catch (IOException | XMLStreamException e) {
            e.printStackTrace();
            return "";
        }
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public CfgPrune getPrune() {
        return this.prune;
    }

    /**
     * Number of topmost blocks present in the database in TOP pruning mode. Information about these
     * blocks is also kept in memory for later pruning.
     */
    public static final int TOP_PRUNE_BLOCK_COUNT = 256;
    /**
     * Number of topmost blocks present in the database in SPREAD pruning mode. Information about
     * these blocks is also kept in memory for later pruning.
     */
    public static final int SPREAD_PRUNE_BLOCK_COUNT = 128;
    /** At what frequency block states are being archived. */
    public static final int SPREAD_PRUNE_ARCHIVE_RATE = 10000;

    public enum PruneOption {
        FULL,
        TOP,
        SPREAD;

        @Override
        public String toString() {
            return this.name();
        }

        public static PruneOption fromValue(String value) {
            value = value.toUpperCase();

            if (value != null) {
                for (PruneOption color : values()) {
                    if (color.toString().equals(value)) {
                        return color;
                    }
                }
            }

            // return default value
            return getDefault();
        }

        public static PruneOption getDefault() {
            return FULL;
        }
    }

    public void setPrune(String _prune_option) {
        this.prune_option = PruneOption.fromValue(_prune_option);

        switch (prune_option) {
            case TOP:
                // journal prune only
                this.prune = new CfgPrune(TOP_PRUNE_BLOCK_COUNT);
                break;
            case SPREAD:
                // journal prune with archived states
                this.prune = new CfgPrune(SPREAD_PRUNE_BLOCK_COUNT, SPREAD_PRUNE_ARCHIVE_RATE);
                break;
            case FULL:
            default:
                // the default is no pruning
                this.prune = new CfgPrune(false);
                break;
        }
    }

    public Map<String, Properties> asProperties() {
        Map<String, Properties> propSet = new HashMap<>();

        if (expert) {
            for (Map.Entry<String, CfgDbDetails> entry : specificConfig.entrySet()) {
                propSet.put(entry.getKey(), entry.getValue().asProperties());
            }

            Properties props = propSet.get(Names.DEFAULT);
            props.setProperty(Props.CHECK_INTEGRITY, String.valueOf(this.check_integrity));
        } else {
            Properties props = new Properties();
            props.setProperty(Props.DB_TYPE, this.vendor);
            props.setProperty(Props.ENABLE_DB_COMPRESSION, String.valueOf(this.compression));
            props.setProperty(Props.CHECK_INTEGRITY, String.valueOf(this.check_integrity));

            props.setProperty(Props.ENABLE_DB_CACHE, "true");
            props.setProperty(Props.DB_CACHE_SIZE, String.valueOf(128 * (int) Utils.MEGA_BYTE));

            props.setProperty(Props.ENABLE_AUTO_COMMIT, "true");
            props.setProperty(Props.ENABLE_HEAP_CACHE, "false");
            props.setProperty(Props.MAX_HEAP_CACHE_SIZE, "32");
            props.setProperty(Props.ENABLE_HEAP_CACHE_STATS, "false");

            props.setProperty(Props.MAX_FD_ALLOC, "1024");
            props.setProperty(Props.BLOCK_SIZE, String.valueOf(16 * (int) Utils.MEGA_BYTE));
            props.setProperty(Props.WRITE_BUFFER_SIZE, String.valueOf(64 * (int) Utils.MEGA_BYTE));
            props.setProperty(Props.READ_BUFFER_SIZE, String.valueOf(64 * (int) Utils.MEGA_BYTE));

            propSet.put(Names.DEFAULT, props);
        }

        return propSet;
    }

    public void setHeapCacheEnabled(boolean value) {
        // already disabled when expert==false
        if (expert) {
            for (Map.Entry<String, CfgDbDetails> entry : specificConfig.entrySet()) {
                entry.getValue().enable_heap_cache = value;
            }
        }
    }
  
    public void setDatabasePath(String value) {
        path = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CfgDb cfgDb = (CfgDb) o;
        return compression == cfgDb.compression &&
                check_integrity == cfgDb.check_integrity &&
                expert == cfgDb.expert &&
                Objects.equal(path, cfgDb.path) &&
                Objects.equal(vendor, cfgDb.vendor) &&
                Objects.equal(prune, cfgDb.prune) &&
                prune_option == cfgDb.prune_option &&
                Objects.equal(specificConfig, cfgDb.specificConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path, vendor, compression, check_integrity, prune, prune_option, expert, specificConfig);
    }
}
