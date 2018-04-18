/*******************************************************************************
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

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.aion.db.impl.DatabaseFactory.Props;

/**
 * @author chris
 */
public class CfgDb {

    public static class Names {
        public static final String DEFAULT = "default";

        public static final String BLOCK = "block";
        public static final String INDEX = "index";

        public static final String DETAILS = "details";
        public static final String STORAGE = "storage";

        public static final String STATE = "state";
        public static final String TRANSACTION = "transaction";

        public static final String TX_CACHE = "pendingtxCache";
        public static final String TX_POOL = "pendingtxPool";
    }

    protected String path;

    // individual db configurations
    private Map<String, CfgDbDetails> specificConfig;

    public CfgDb() {
        this.path = "database";
        this.specificConfig = new HashMap<>();
        this.specificConfig.put(Names.DEFAULT, new CfgDbDetails());
    }

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    CfgDbDetails dbDefault = specificConfig.get(Names.DEFAULT);
                    switch (elementName) {
                        case "path":
                            this.path = Cfg.readValue(sr);
                            break;
                        case "vendor":
                            dbDefault.vendor = Cfg.readValue(sr);
                            break;
                        case Props.ENABLE_AUTO_COMMIT:
                            dbDefault.enable_auto_commit = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case Props.ENABLE_DB_CACHE:
                            dbDefault.enable_db_cache = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case Props.ENABLE_DB_COMPRESSION:
                            dbDefault.enable_db_compression = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case Props.ENABLE_HEAP_CACHE:
                            dbDefault.enable_heap_cache = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case Props.MAX_HEAP_CACHE_SIZE:
                            dbDefault.max_heap_cache_size = Cfg.readValue(sr);
                            break;
                        case Props.ENABLE_HEAP_CACHE_STATS:
                            dbDefault.enable_heap_cache_stats = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case Props.BLOCK_SIZE:
                            dbDefault.block_size = CfgDbDetails
                                    .parseFileSizeSafe(Cfg.readValue(sr), dbDefault.block_size);
                            break;
                        case Props.MAX_FD_ALLOC:
                            int i = Integer.parseInt(Cfg.readValue(sr));
                            dbDefault.max_fd_open_alloc = Math.max(CfgDbDetails.MIN_FD_OPEN_ALLOC, i);
                            break;
                        case Props.WRITE_BUFFER_SIZE:
                            dbDefault.write_buffer_size = CfgDbDetails
                                    .parseFileSizeSafe(Cfg.readValue(sr), dbDefault.write_buffer_size);
                            break;
                        case Props.READ_BUFFER_SIZE:
                            dbDefault.read_buffer_size = CfgDbDetails
                                    .parseFileSizeSafe(Cfg.readValue(sr), dbDefault.read_buffer_size);
                            break;
                        case Props.DB_CACHE_SIZE:
                            dbDefault.cache_size = CfgDbDetails
                                    .parseFileSizeSafe(Cfg.readValue(sr), dbDefault.cache_size);
                            break;
                        case Names.DEFAULT: {
                            dbDefault.fromXML(sr);
                            this.specificConfig.put(Names.DEFAULT, dbDefault);
                            break;
                        }
                        case Names.BLOCK: {
                            CfgDbDetails dbConfig = new CfgDbDetails();
                            dbConfig.fromXML(sr);
                            this.specificConfig.put(Names.BLOCK, dbConfig);
                            break;
                        }
                        case Names.INDEX: {
                            CfgDbDetails dbConfig = new CfgDbDetails();
                            dbConfig.fromXML(sr);
                            this.specificConfig.put(Names.INDEX, dbConfig);
                            break;
                        }
                        case Names.DETAILS: {
                            CfgDbDetails dbConfig = new CfgDbDetails();
                            dbConfig.fromXML(sr);
                            this.specificConfig.put(Names.DETAILS, dbConfig);
                            break;
                        }
                        case Names.STORAGE: {
                            CfgDbDetails dbConfig = new CfgDbDetails();
                            dbConfig.fromXML(sr);
                            this.specificConfig.put(Names.STORAGE, dbConfig);
                            break;
                        }
                        case Names.STATE: {
                            CfgDbDetails dbConfig = new CfgDbDetails();
                            dbConfig.fromXML(sr);
                            this.specificConfig.put(Names.STATE, dbConfig);
                            break;
                        }
                        case Names.TRANSACTION: {
                            CfgDbDetails dbConfig = new CfgDbDetails();
                            dbConfig.fromXML(sr);
                            this.specificConfig.put(Names.TRANSACTION, dbConfig);
                            break;
                        }
                        case Names.TX_POOL: {
                            CfgDbDetails dbConfig = new CfgDbDetails();
                            dbConfig.fromXML(sr);
                            this.specificConfig.put(Names.TX_POOL, dbConfig);
                            break;
                        }
                        case Names.TX_CACHE: {
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
            xmlWriter.writeStartElement("path");
            xmlWriter.writeCharacters(this.getPath());
            xmlWriter.writeEndElement();

            for (Map.Entry<String, CfgDbDetails> entry : specificConfig.entrySet()) {
                entry.getValue().toXML(entry.getKey(), xmlWriter);
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
        return this.path;
    }

    public Map<String, Properties> asProperties() {
        Map<String, Properties> props = new HashMap<>();

        for (Map.Entry<String, CfgDbDetails> entry : specificConfig.entrySet()) {
            props.put(entry.getKey(), entry.getValue().asProperties());

        }
        return props;
    }

    public void setHeapCacheEnabled(boolean value) {
        for (Map.Entry<String, CfgDbDetails> entry : specificConfig.entrySet()) {
            entry.getValue().enable_heap_cache = value;
        }
    }
}