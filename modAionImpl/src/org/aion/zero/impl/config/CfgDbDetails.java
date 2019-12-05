package org.aion.zero.impl.config;


import com.google.common.base.Objects;
import java.util.Properties;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.aion.zero.impl.config.CfgDb.Props;

public class CfgDbDetails {

    public CfgDbDetails() {
        this.vendor = "leveldb";

        this.enable_auto_commit = true;
        this.enable_db_cache = true;
        this.enable_db_compression = true;
        // size 0 means unbound
        this.max_heap_cache_size = "1024";
        this.enable_heap_cache_stats = false;
    }

    public String vendor;

    public boolean enable_db_cache;
    public boolean enable_db_compression;

    public boolean enable_auto_commit;
    public String max_heap_cache_size;
    public boolean enable_heap_cache_stats;

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "vendor":
                            this.vendor = ConfigUtil.readValue(sr);
                            break;
                        case Props.ENABLE_AUTO_COMMIT:
                            this.enable_auto_commit = Boolean.parseBoolean(ConfigUtil.readValue(sr));
                            break;
                        case Props.ENABLE_DB_CACHE:
                            this.enable_db_cache = Boolean.parseBoolean(ConfigUtil.readValue(sr));
                            break;
                        case Props.ENABLE_DB_COMPRESSION:
                            this.enable_db_compression = Boolean.parseBoolean(ConfigUtil.readValue(sr));
                            break;
                        default:
                            ConfigUtil.skipElement(sr);
                            break;
                    }
                    break;
                case XMLStreamReader.END_ELEMENT:
                    break loop;
            }
        }
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

        xmlWriter.writeCharacters("\r\n\t\t");
        xmlWriter.writeEndElement();
    }

    Properties asProperties() {
        Properties props = new Properties();
        props.setProperty(Props.DB_TYPE, this.vendor);

        props.setProperty(Props.ENABLE_DB_CACHE, String.valueOf(this.enable_db_cache));
        props.setProperty(Props.ENABLE_DB_COMPRESSION, String.valueOf(this.enable_db_compression));
        props.setProperty(Props.ENABLE_AUTO_COMMIT, String.valueOf(this.enable_auto_commit));

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
                && enable_heap_cache_stats == that.enable_heap_cache_stats
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
                max_heap_cache_size,
                enable_heap_cache_stats);
    }
}
