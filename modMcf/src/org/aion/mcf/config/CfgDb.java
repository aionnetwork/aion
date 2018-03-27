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
    public final String DEFAULT_BLOCK_SIZE = "16kB";
    public final String DEFAULT_WRITE_BUFFER_SIZE = "16mB";
    public final String DEFAULT_CACHE_SIZE = "128mB";

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

        // corresponds to DEFAULT_BLOCK_SIZE
        this.block_size = 16 * (int) Utils.KILO_BYTE;
        this.max_fd_open_alloc = MIN_FD_OPEN_ALLOC;

        // corresponds to DEFAULT_WRITE_BUFFER_SIZE
        this.write_buffer_size = 16 * (int) Utils.MEGA_BYTE;

        // corresponds to DEFAULT_CACHE_SIZE
        this.cache_size = 128 * (int) Utils.MEGA_BYTE;
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
     * {@code NUM_DB * max_fd_open_alloc}</p>
     *
     * <p>This parameter is specific to {@link org.aion.db.impl.leveldb.LevelDB}</p>
     */
    private int max_fd_open_alloc;

    /**
     * <p>The size of the write buffer that will be applied per database, for more
     * information, see <a href="https://github.com/google/leveldb/blob/master/include/leveldb/options.h">here</a></p>
     *
     * From LevelDB docs:
     *
     * <p>Amount of data to build up in memory (backed by an unsorted log
     * on disk) before converting to a sorted on-disk file.</p>
     *
     * <p>Larger values increase performance, especially during bulk loads.
     * Up to two write buffers may be held in memory at the same time,
     * so you may wish to adjust this parameter to control memory usage.
     * Also, a larger write buffer will result in a longer recovery time
     * the next time the database is opened.</p>
     *
     * <p>This parameter is specific to {@link org.aion.db.impl.leveldb.LevelDB}</p>
     */
    private int write_buffer_size;

    /**
     * <p>Specify the size of the cache used by LevelDB</p>
     *
     * <p>This parameter is specific to {@link org.aion.db.impl.leveldb.LevelDB}</p>
     */
    private int cache_size;

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
                        this.block_size = parseFileSizeSafe(Cfg.readValue(sr), this.block_size);
                        break;
                    }
                    case "max_fd_alloc_size": {
                        int i = Integer.parseInt(Cfg.readValue(sr));
                        this.max_fd_open_alloc = Math.max(MIN_FD_OPEN_ALLOC, i);
                        break;
                    }
                    case "write_buffer_size": {
                        this.block_size = parseFileSizeSafe(Cfg.readValue(sr), this.write_buffer_size);
                        break;
                    }
                    case "cache_size": {
                        this.cache_size = parseFileSizeSafe(Cfg.readValue(sr), this.cache_size);
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

    private static int parseFileSizeSafe(String input, int fallback) {
        if (input == null || input.isEmpty())
            return fallback;

        Optional<Long> maybeSize = Utils.parseSize(input);
        if (!maybeSize.isPresent())
            return fallback;

        // present
        long size = maybeSize.get();
        if (size > Integer.MAX_VALUE || size <= 0)
            return fallback;

        return (int) size;
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

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("write_buffer_size");
            xmlWriter.writeCharacters(String.valueOf(DEFAULT_WRITE_BUFFER_SIZE));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("cache_size");
            xmlWriter.writeCharacters(String.valueOf(DEFAULT_CACHE_SIZE));
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

    public int getWriteBufferSize() {
        return this.write_buffer_size;
    }

    public int getCacheSize() {
        return this.cache_size;
    }
}






















