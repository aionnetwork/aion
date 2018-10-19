/**
 * ***************************************************************************** Copyright (c)
 * 2017-2018 Aion foundation.
 *
 * <p>This file is part of the aion network project.
 *
 * <p>The aion network project is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * <p>The aion network project is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with the aion network
 * project source files. If not, see <https://www.gnu.org/licenses/>.
 *
 * <p>Contributors: Aion foundation.
 * ****************************************************************************
 */
package org.aion.mcf.config;

import static org.aion.db.impl.DatabaseFactory.Props;

import com.google.common.base.Objects;
import java.util.Optional;
import java.util.Properties;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.aion.base.util.Utils;
import org.aion.db.impl.DBVendor;

public class CfgDbDetails {

    public static final int MIN_FD_OPEN_ALLOC = 1024;
    public static final String DEFAULT_BLOCK_SIZE = "16mB";
    public static final String DEFAULT_WRITE_BUFFER_SIZE = "64mB";
    public static final String DEFAULT_READ_BUFFER_SIZE = "64mB";
    public static final String DEFAULT_CACHE_SIZE = "128mB";

    public CfgDbDetails() {
        this.vendor = DBVendor.LEVELDB.toValue();

        this.enable_auto_commit = true;
        this.enable_db_cache = true;
        this.enable_db_compression = true;
        this.enable_heap_cache = false;
        // size 0 means unbound
        this.max_heap_cache_size = "1024";
        this.enable_heap_cache_stats = false;
        this.read_buffer_size = 64 * (int) Utils.MEGA_BYTE;

        // corresponds to DEFAULT_BLOCK_SIZE
        this.block_size = 16 * (int) Utils.MEGA_BYTE;
        this.max_fd_open_alloc = MIN_FD_OPEN_ALLOC;

        // corresponds to DEFAULT_WRITE_BUFFER_SIZE
        this.write_buffer_size = 64 * (int) Utils.MEGA_BYTE;

        // corresponds to DEFAULT_CACHE_SIZE
        this.cache_size = 128 * (int) Utils.MEGA_BYTE;
    }

    public String vendor;

    public boolean enable_db_cache;
    public boolean enable_db_compression;

    public boolean enable_auto_commit;
    public boolean enable_heap_cache;
    public String max_heap_cache_size;
    public boolean enable_heap_cache_stats;

    /**
     * The maximum block size
     *
     * <p>This parameter is specific to {@link org.aion.db.impl.leveldb.LevelDB}
     */
    public int block_size;

    /**
     * The maximum allocated file descriptor that will be allocated per database, therefore the
     * total amount of file descriptors that are required is {@code NUM_DB * max_fd_open_alloc}
     *
     * <p>This parameter is specific to {@link org.aion.db.impl.leveldb.LevelDB}
     */
    public int max_fd_open_alloc;

    /**
     * The size of the write buffer that will be applied per database, for more information, see <a
     * href="https://github.com/google/leveldb/blob/master/include/leveldb/options.h">here</a> From
     * LevelDB docs:
     *
     * <p>Amount of data to build up in memory (backed by an unsorted log on disk) before converting
     * to a sorted on-disk file.
     *
     * <p>Larger values increase performance, especially during bulk loads. Up to two write buffers
     * may be held in memory at the same time, so you may wish to adjust this parameter to control
     * memory usage. Also, a larger write buffer will result in a longer recovery time the next time
     * the database is opened.
     *
     * <p>This parameter is specific to {@link org.aion.db.impl.leveldb.LevelDB}
     */
    public int write_buffer_size;

    public int read_buffer_size;

    /**
     * Specify the size of the cache used by LevelDB
     *
     * <p>This parameter is specific to {@link org.aion.db.impl.leveldb.LevelDB}
     */
    public int cache_size;

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "vendor":
                            this.vendor = Cfg.readValue(sr);
                            break;
                        case Props.ENABLE_AUTO_COMMIT:
                            this.enable_auto_commit = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case Props.ENABLE_DB_CACHE:
                            this.enable_db_cache = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case Props.ENABLE_DB_COMPRESSION:
                            this.enable_db_compression = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case Props.ENABLE_HEAP_CACHE:
                            this.enable_heap_cache = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case Props.MAX_HEAP_CACHE_SIZE:
                            this.max_heap_cache_size = Cfg.readValue(sr);
                            break;
                        case Props.ENABLE_HEAP_CACHE_STATS:
                            this.enable_heap_cache_stats = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case Props.BLOCK_SIZE:
                            this.block_size = parseFileSizeSafe(Cfg.readValue(sr), this.block_size);
                            break;
                        case Props.MAX_FD_ALLOC:
                            int i = Integer.parseInt(Cfg.readValue(sr));
                            this.max_fd_open_alloc = Math.max(MIN_FD_OPEN_ALLOC, i);
                            break;
                        case Props.WRITE_BUFFER_SIZE:
                            this.write_buffer_size =
                                    parseFileSizeSafe(Cfg.readValue(sr), this.write_buffer_size);
                            break;
                        case Props.READ_BUFFER_SIZE:
                            this.read_buffer_size =
                                    parseFileSizeSafe(Cfg.readValue(sr), this.read_buffer_size);
                            break;
                        case Props.DB_CACHE_SIZE:
                            this.cache_size = parseFileSizeSafe(Cfg.readValue(sr), this.cache_size);
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

    public static int parseFileSizeSafe(String input, int fallback) {
        if (input == null || input.isEmpty()) {
            return fallback;
        }

        Optional<Long> maybeSize = Utils.parseSize(input);
        if (!maybeSize.isPresent()) {
            return fallback;
        }

        // present
        long size = maybeSize.get();
        if (size > Integer.MAX_VALUE || size <= 0) {
            return fallback;
        }

        return (int) size;
    }

    public void toXML(String name, XMLStreamWriter xmlWriter, boolean expert)
            throws XMLStreamException {
        xmlWriter.writeCharacters("\r\n\t\t");
        xmlWriter.writeStartElement(name);

        if (expert) {
            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("vendor");
            xmlWriter.writeCharacters(this.vendor);
            xmlWriter.writeEndElement();
        }

        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeStartElement(Props.ENABLE_DB_CACHE);
        xmlWriter.writeCharacters(String.valueOf(this.enable_db_cache));
        xmlWriter.writeEndElement();

        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeStartElement(Props.ENABLE_DB_COMPRESSION);
        xmlWriter.writeCharacters(String.valueOf(this.enable_db_compression));
        xmlWriter.writeEndElement();

        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeStartElement(Props.BLOCK_SIZE);
        xmlWriter.writeCharacters(DEFAULT_BLOCK_SIZE);
        xmlWriter.writeEndElement();

        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeStartElement(Props.MAX_FD_ALLOC);
        xmlWriter.writeCharacters(String.valueOf(MIN_FD_OPEN_ALLOC));
        xmlWriter.writeEndElement();

        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeStartElement(Props.WRITE_BUFFER_SIZE);
        xmlWriter.writeCharacters(String.valueOf(DEFAULT_WRITE_BUFFER_SIZE));
        xmlWriter.writeEndElement();

        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeStartElement(Props.READ_BUFFER_SIZE);
        xmlWriter.writeCharacters(String.valueOf(DEFAULT_READ_BUFFER_SIZE));
        xmlWriter.writeEndElement();

        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeStartElement(Props.DB_CACHE_SIZE);
        xmlWriter.writeCharacters(String.valueOf(DEFAULT_CACHE_SIZE));
        xmlWriter.writeEndElement();

        xmlWriter.writeCharacters("\r\n\t\t");
        xmlWriter.writeEndElement();
    }

    public Properties asProperties() {
        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, this.vendor);

        props.setProperty(Props.ENABLE_DB_CACHE, String.valueOf(this.enable_db_cache));
        props.setProperty(Props.ENABLE_DB_COMPRESSION, String.valueOf(this.enable_db_compression));
        props.setProperty(Props.DB_CACHE_SIZE, String.valueOf(this.cache_size));

        props.setProperty(Props.ENABLE_AUTO_COMMIT, String.valueOf(this.enable_auto_commit));
        props.setProperty(Props.ENABLE_HEAP_CACHE, String.valueOf(this.enable_heap_cache));
        props.setProperty(Props.MAX_HEAP_CACHE_SIZE, this.max_heap_cache_size);
        props.setProperty(
                Props.ENABLE_HEAP_CACHE_STATS, String.valueOf(this.enable_heap_cache_stats));

        props.setProperty(Props.MAX_FD_ALLOC, String.valueOf(this.max_fd_open_alloc));
        props.setProperty(Props.BLOCK_SIZE, String.valueOf(this.block_size));
        props.setProperty(Props.WRITE_BUFFER_SIZE, String.valueOf(this.write_buffer_size));
        props.setProperty(Props.READ_BUFFER_SIZE, String.valueOf(this.read_buffer_size));

        return props;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CfgDbDetails that = (CfgDbDetails) o;
        return enable_db_cache == that.enable_db_cache
                && enable_db_compression == that.enable_db_compression
                && enable_auto_commit == that.enable_auto_commit
                && enable_heap_cache == that.enable_heap_cache
                && enable_heap_cache_stats == that.enable_heap_cache_stats
                && block_size == that.block_size
                && max_fd_open_alloc == that.max_fd_open_alloc
                && write_buffer_size == that.write_buffer_size
                && read_buffer_size == that.read_buffer_size
                && cache_size == that.cache_size
                && Objects.equal(vendor, that.vendor)
                && Objects.equal(max_heap_cache_size, that.max_heap_cache_size);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                vendor,
                enable_db_cache,
                enable_db_compression,
                enable_auto_commit,
                enable_heap_cache,
                max_heap_cache_size,
                enable_heap_cache_stats,
                block_size,
                max_fd_open_alloc,
                write_buffer_size,
                read_buffer_size,
                cache_size);
    }
}
