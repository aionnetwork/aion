/*
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
 */

package org.aion.mcf.config;

import com.google.common.base.Objects;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/** @author chris */
public final class CfgSync {

    private int blocksQueueMax;

    private boolean showStatus;
    private List<StatsType> showStatistics;

    private static int BLOCKS_QUEUE_MAX = 32;

    public CfgSync() {
        this.blocksQueueMax = BLOCKS_QUEUE_MAX;
        this.showStatus = false;
        this.showStatistics = new ArrayList<>(Arrays.asList(StatsType.NONE));
    }

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "blocks-queue-max":
                            this.blocksQueueMax = Integer.parseInt(Cfg.readValue(sr));
                            break;
                        case "show-status":
                            this.showStatus = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case "show-statistics":
                            parseSelectedStats(showStatistics, Cfg.readValue(sr));
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

    private static void parseSelectedStats(List<StatsType> showStatistics, String readValue) {
        showStatistics.clear();

        String[] selected = readValue.split(",");

        for (String option : selected) {
            try {
                showStatistics.add(StatsType.valueOf(option.toUpperCase()));
            } catch (Exception e) {
                // skip option
                continue;
            }
        }

        // expand all to specific options
        if (showStatistics.contains(StatsType.ALL)) {
            showStatistics.remove(StatsType.ALL);
            showStatistics.addAll(StatsType.getAllSpecificTypes());
        }

        if (showStatistics.contains(StatsType.NONE) && showStatistics.size() > 1) {
            showStatistics.remove(StatsType.NONE);
        }

        // set none if empty
        if (showStatistics.isEmpty()) {
            showStatistics.add(StatsType.NONE);
        }
    }

    public String toXML() {
        final XMLOutputFactory output = XMLOutputFactory.newInstance();
        XMLStreamWriter xmlWriter;
        String xml;
        try {
            Writer strWriter = new StringWriter();
            xmlWriter = output.createXMLStreamWriter(strWriter);

            // start element sync
            xmlWriter.writeCharacters("\r\n\t");
            xmlWriter.writeStartElement("sync");

            // sub-element blocks-queue-max
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("blocks-queue-max");
            xmlWriter.writeCharacters(BLOCKS_QUEUE_MAX + "");
            xmlWriter.writeEndElement();

            // sub-element show-status
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("show-status");
            xmlWriter.writeCharacters(this.showStatus + "");
            xmlWriter.writeEndElement();

            // sub-element show-status
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeComment(
                    "requires show-status=true; comma separated list of options: "
                            + Arrays.toString(StatsType.values()).toLowerCase());
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("show-statistics");
            xmlWriter.writeCharacters(printSelectedStats().toLowerCase());
            xmlWriter.writeEndElement();

            // close element sync
            xmlWriter.writeCharacters("\r\n\t");
            xmlWriter.writeEndElement();

            xml = strWriter.toString();
            strWriter.flush();
            strWriter.close();
            xmlWriter.flush();
            xmlWriter.close();
            return xml;
        } catch (IOException | XMLStreamException e) {
            return "";
        }
    }

    /**
     * Returns a string containing a comma separated list of the statistics to be displayed.
     *
     * @return a string containing a comma separated list of the statistics to be displayed
     */
    private String printSelectedStats() {
        // not meaningful if other settings are also present
        showStatistics.remove(StatsType.NONE);

        if (showStatistics.isEmpty()) {
            return StatsType.NONE.toString();
        } else {
            if (showStatistics.contains(StatsType.ALL)
                    || showStatistics.containsAll(StatsType.getAllSpecificTypes())) {
                return StatsType.ALL.toString();
            } else if (showStatistics.size() == 1) {
                return showStatistics.get(0).toString();
            } else {
                StringBuilder sb = new StringBuilder(showStatistics.remove(0).toString());
                for (StatsType tp : showStatistics) {
                    sb.append(",");
                    sb.append(tp.toString());
                }
                return sb.toString();
            }
        }
    }

    public int getBlocksQueueMax() {
        return this.blocksQueueMax;
    }

    public boolean getShowStatus() {
        return this.showStatus;
    }

    public List<StatsType> getShowStatistics() {
        return showStatistics;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CfgSync cfgSync = (CfgSync) o;
        return blocksQueueMax == cfgSync.blocksQueueMax && showStatus == cfgSync.showStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(blocksQueueMax, showStatus);
    }
}
