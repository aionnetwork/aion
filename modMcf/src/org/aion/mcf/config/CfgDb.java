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

import org.aion.base.util.Utils;
import org.aion.db.impl.DBVendor;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author chris
 */
public class CfgDb {

    public final int MIN_FD_OPEN_ALLOC = 1024;
    public final String DEFAULT_BLOCK_SIZE = "4kB";

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
        this.block_size = 16 * (int) Utils.MEGA_BYTE;
        this.max_fd_open_alloc = MIN_FD_OPEN_ALLOC;
    }

    protected String path;

    private String vendor;

    private boolean enable_auto_commit;
    private boolean enable_db_cache;
    private boolean enable_db_compression;

    private boolean enable_heap_cache;
    private String max_heap_cache_size;
    private boolean enable_heap_cache_stats;

    /**
     * <p>The maximum block size</p>
     *
     * <p>This parameter is specific to {@link org.aion.db.impl.leveldb.LevelDB}</p>
     */
    private int block_size;

    /**
     * <p>The maximum allocated file descriptor that will be allocated per
     * database, therefore the total amount of file descriptors that are required is
     * {@code NUM_DB * max_fd_open_alloc}
     *
     * This parameter is specified to {@link org.aion.db.impl.leveldb.LevelDB}</p>
     */
    private int max_fd_open_alloc;

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
                    case "block_size": {
                        Optional<Long> maybeSize = Utils.parseSize(Cfg.readValue(sr));
                        // TODO: ideally we should log out a message indicating we failed to parse
                        if (maybeSize.isPresent()) {
                            long s = maybeSize.get();

                            // check for overflow
                            // TODO: this is very non-descriptive behaviour, need to doc this properly
                            if (s > Integer.MAX_VALUE)
                                break;
                            this.block_size = (int) s;
                        }
                        break;
                    }
                    case "max_fd_alloc_size": {
                        int i = Integer.parseInt(Cfg.readValue(sr));
                        this.max_fd_open_alloc = Math.max(MIN_FD_OPEN_ALLOC, i);
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

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("block_size");
            xmlWriter.writeCharacters(DEFAULT_BLOCK_SIZE);
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("max_fd_alloc_size");
            xmlWriter.writeCharacters(String.valueOf(MIN_FD_OPEN_ALLOC));
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

    public int getFdOpenAllocSize() {
        return this.max_fd_open_alloc;
    }

    public int getBlockSize() {
        return this.block_size;
    }
}






















