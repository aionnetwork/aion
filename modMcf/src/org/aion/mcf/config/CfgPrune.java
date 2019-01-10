package org.aion.mcf.config;

import com.google.common.base.Objects;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.aion.base.db.IPruneConfig;

/**
 * Configuration for data pruning behavior.
 *
 * @author Alexandra Roatis
 */
public class CfgPrune implements IPruneConfig {

    private boolean enabled;
    private boolean archived;
    private int current_count = MINIMUM_CURRENT_COUNT;
    private int archive_rate = MINIMUM_ARCHIVE_RATE;

    private static final int MINIMUM_CURRENT_COUNT = 128;
    private static final int MINIMUM_ARCHIVE_RATE = 1000;

    public CfgPrune(boolean _enabled) {
        this.enabled = _enabled;
        this.archived = _enabled;
    }

    public CfgPrune(int _current_count) {
        // enable journal pruning
        this.enabled = true;
        this.current_count =
                _current_count > MINIMUM_CURRENT_COUNT ? _current_count : MINIMUM_CURRENT_COUNT;
        // disable archiving
        this.archived = false;
    }

    public CfgPrune(int _current_count, int _archive_rate) {
        // enable journal pruning
        this.enabled = true;
        this.current_count =
                _current_count > MINIMUM_CURRENT_COUNT ? _current_count : MINIMUM_CURRENT_COUNT;
        // enable archiving
        this.archived = true;
        this.archive_rate =
                _archive_rate > MINIMUM_ARCHIVE_RATE ? _archive_rate : MINIMUM_ARCHIVE_RATE;
    }

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "enabled":
                            this.enabled = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case "archived":
                            this.archived = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case "current_count":
                            this.current_count = Integer.parseInt(Cfg.readValue(sr));
                            // must be at least MINIMUM_CURRENT_COUNT
                            if (this.current_count < MINIMUM_CURRENT_COUNT) {
                                this.current_count = MINIMUM_CURRENT_COUNT;
                            }
                            break;
                        case "archive_rate":
                            this.archive_rate = Integer.parseInt(Cfg.readValue(sr));
                            // must be at least MINIMUM_ARCHIVE_RATE
                            if (this.archive_rate < MINIMUM_ARCHIVE_RATE) {
                                this.archive_rate = MINIMUM_ARCHIVE_RATE;
                            }
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

    public void toXML(XMLStreamWriter xmlWriter) throws XMLStreamException {
        xmlWriter.writeCharacters("\r\n\t\t");
        xmlWriter.writeStartElement("prune");

        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeComment("Boolean value. Enable/disable database pruning.");
        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeStartElement("enabled");
        xmlWriter.writeCharacters(String.valueOf(this.enabled));
        xmlWriter.writeEndElement();

        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeComment("Boolean value. Enable/disable database archiving.");
        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeStartElement("archived");
        xmlWriter.writeCharacters(String.valueOf(this.archived));
        xmlWriter.writeEndElement();

        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeComment(
                "Integer value with minimum set to 128. Only blocks older than best block level minus this number are candidates for pruning.");
        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeStartElement("current_count");
        xmlWriter.writeCharacters(String.valueOf(this.current_count));
        xmlWriter.writeEndElement();

        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeComment(
                "Integer value with minimum set to 1000. States for blocks that are exact multiples of this number will not be pruned.");
        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeStartElement("archive_rate");
        xmlWriter.writeCharacters(String.valueOf(this.archive_rate));
        xmlWriter.writeEndElement();

        xmlWriter.writeCharacters("\r\n\t\t");
        xmlWriter.writeEndElement();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isArchived() {
        return archived;
    }

    @Override
    public int getCurrentCount() {
        return current_count;
    }

    @Override
    public int getArchiveRate() {
        return archive_rate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CfgPrune cfgPrune = (CfgPrune) o;
        return enabled == cfgPrune.enabled
                && archived == cfgPrune.archived
                && current_count == cfgPrune.current_count
                && archive_rate == cfgPrune.archive_rate;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(enabled, archived, current_count, archive_rate);
    }
}
