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

import org.aion.db.impl.DBVendor;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * @author chris
 */
public class CfgDb {

    public CfgDb() {
        this.vendor = DBVendor.LEVELDB.toValue();
        this.path = "database";

        this.enable_auto_commit = false;
        this.enable_db_cache = true;
        this.enable_db_compression = true;
        this.enable_heap_cache = false;
        // size 0 means unbound
        this.max_heap_cache_size = "1024";
        this.enable_heap_cache_stats = false;
    }

    protected String path;

    private String vendor;

    private boolean enable_auto_commit;
    private boolean enable_db_cache;
    private boolean enable_db_compression;

    private boolean enable_heap_cache;
    private String max_heap_cache_size;
    private boolean enable_heap_cache_stats;

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
                case "vendor":
                    this.vendor = Cfg.readValue(sr);
                    break;
                case "enable_auto_commit":
                    this.enable_auto_commit = Boolean.parseBoolean(Cfg.readValue(sr));
                    break;
                case "enable_db_cache":
                    this.enable_db_cache = Boolean.parseBoolean(Cfg.readValue(sr));
                    break;
                case "enable_db_compression":
                    this.enable_db_compression = Boolean.parseBoolean(Cfg.readValue(sr));
                    break;
                case "enable_heap_cache":
                    this.enable_heap_cache = Boolean.parseBoolean(Cfg.readValue(sr));
                    break;
                case "max_heap_cache_size":
                    this.max_heap_cache_size = Cfg.readValue(sr);
                    break;
                case "enable_heap_cache_stats":
                    this.enable_heap_cache_stats = Boolean.parseBoolean(Cfg.readValue(sr));
                    break;
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

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("vendor");
            xmlWriter.writeCharacters(this.getVendor());
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("enable_auto_commit");
            xmlWriter.writeCharacters(String.valueOf(this.isAutoCommitEnabled()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("enable_db_cache");
            xmlWriter.writeCharacters(String.valueOf(this.isDbCacheEnabled()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("enable_db_compression");
            xmlWriter.writeCharacters(String.valueOf(this.isDbCompressionEnabled()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("enable_heap_cache");
            xmlWriter.writeCharacters(String.valueOf(this.isHeapCacheEnabled()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("max_heap_cache_size");
            xmlWriter.writeCharacters(String.valueOf(this.getMaxHeapCacheSize()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("enable_heap_cache_stats");
            xmlWriter.writeCharacters(String.valueOf(this.isHeapCacheStatsEnabled()));
            xmlWriter.writeEndElement();

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

    public String getVendor() {
        return this.vendor;
    }

    public boolean isAutoCommitEnabled() {
        return enable_auto_commit;
    }

    public boolean isDbCacheEnabled() {
        return enable_db_cache;
    }

    public boolean isDbCompressionEnabled() {
        return enable_db_compression;
    }

    public boolean isHeapCacheEnabled() {
        return enable_heap_cache;
    }

    public String getMaxHeapCacheSize() {
        return max_heap_cache_size;
    }

    public boolean isHeapCacheStatsEnabled() {
        return enable_heap_cache_stats;
    }

    public void setHeapCacheEnabled(boolean enable_heap_cache) {
        this.enable_heap_cache = enable_heap_cache;
    }
}






















