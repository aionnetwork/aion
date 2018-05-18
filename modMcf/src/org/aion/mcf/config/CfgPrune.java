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
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 ******************************************************************************/
package org.aion.mcf.config;

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
    private int current;
    private int archive;

    private static final int MINIMUM_CURRENT_COUNT = 128;
    private static final int MINIMUM_ARCHIVE_RATE = 1000;

    public CfgPrune() {
        this.enabled = true;
        this.current = MINIMUM_CURRENT_COUNT;
        this.archive = MINIMUM_ARCHIVE_RATE;
    }

    public CfgPrune(boolean _enabled) {
        this.enabled = _enabled;
        this.current = MINIMUM_CURRENT_COUNT;
        this.archive = MINIMUM_ARCHIVE_RATE;
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
                        case "current_count":
                            this.current = Integer.parseInt(Cfg.readValue(sr));
                            // must be at least MINIMUM_CURRENT_COUNT
                            if (this.current < MINIMUM_CURRENT_COUNT) {
                                this.current = MINIMUM_CURRENT_COUNT;
                            }
                            break;
                        case "archive_rate":
                            this.archive = Integer.parseInt(Cfg.readValue(sr));
                            // must be at least MINIMUM_ARCHIVE_RATE
                            if (this.archive < MINIMUM_ARCHIVE_RATE) {
                                this.archive = MINIMUM_ARCHIVE_RATE;
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
        xmlWriter.writeComment(
                "Integer value with minimum set to 128. Only blocks older than best block level minus this number are candidates for pruning.");
        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeStartElement("current_count");
        xmlWriter.writeCharacters(String.valueOf(this.current));
        xmlWriter.writeEndElement();

        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeComment(
                "Integer value with minimum set to 1000. States for blocks that are exact multiples of this number will not be pruned.");
        xmlWriter.writeCharacters("\r\n\t\t\t");
        xmlWriter.writeStartElement("archive_rate");
        xmlWriter.writeCharacters(String.valueOf(this.archive));
        xmlWriter.writeEndElement();

        xmlWriter.writeCharacters("\r\n\t\t");
        xmlWriter.writeEndElement();
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int getCurrentCount() {
        return current;
    }

    @Override
    public int getArchiveRate() {
        return archive;
    }
}
