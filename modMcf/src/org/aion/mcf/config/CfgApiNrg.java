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
 *
 ******************************************************************************/
package org.aion.mcf.config;

import com.google.common.base.Objects;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/**
 * @author ali sharif
 */
public final class CfgApiNrg {

    private long defaultPrice;
    private long maxPrice;

    private boolean oracleEnabled;

    CfgApiNrg() {
        // recommend setting the defaultPrice to a safe-low known nrg price accepted by most miners on the network
        // (this value fluctuates over time, depending on network conditions)
        this.defaultPrice = 10_000_000_000L; // 10E9 AION
        this.maxPrice = 100_000_000_000L; // 100E9 AION
        this.oracleEnabled = false; // ship with oracle disabled
    }

    public long getNrgPriceDefault() {
        return this.defaultPrice;
    }

    public void setNrgPriceDefault(long defaultPrice) {
        this.defaultPrice = defaultPrice;
    }

    public long getNrgPriceMax() {
        return this.maxPrice;
    }

    public void setNrgPriceMax(long maxPrice) {
        this.maxPrice = maxPrice;
    }

    public boolean isOracleEnabled() {
        return this.oracleEnabled;
    }

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "oracle-enabled":
                            try {
                                oracleEnabled = Boolean.parseBoolean(Cfg.readValue(sr));
                            } catch (Exception e) {
                                System.out.println(
                                    "failed to read config node: aion.api.nrg.enable; using preset: "
                                        + this.oracleEnabled);
                                e.printStackTrace();
                            }
                            break;
                        case "default":
                            try {
                                // using BigDecimal here only because of [BigDecimal -> String -> BigDecimal] preservation property
                                defaultPrice = (new BigDecimal(Cfg.readValue(sr))).longValueExact();
                            } catch (Exception e) {
                                System.out.println(
                                    "failed to read config node: aion.api.nrg.default; using preset: "
                                        + new BigDecimal(defaultPrice).toEngineeringString());
                                e.printStackTrace();
                            }
                            break;
                        case "max":
                            try {
                                maxPrice = (new BigDecimal(Cfg.readValue(sr))).longValueExact();
                            } catch (Exception e) {
                                System.out.println(
                                    "failed to read config node: aion.api.nrg.maxPrice; using preset: "
                                        + new BigDecimal(maxPrice).toEngineeringString());
                                e.printStackTrace();
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

    public String toXML() {
        final XMLOutputFactory output = XMLOutputFactory.newInstance();
        XMLStreamWriter xmlWriter;
        String xml;
        try {
            Writer strWriter = new StringWriter();
            xmlWriter = output.createXMLStreamWriter(strWriter);

            // start element sync
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("nrg-recommendation");

            /* BigDecimal -> String -> BigDecimal preserved, according to BigDecimal.toString() javadoc:
             * "If that string representation is converted back to a BigDecimal using the BigDecimal(String)
             * constructor, then the original value will be recovered."
             * This property does NOT hold for toEngineeringString() (which is easier to read)
             */

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeComment(
                "default NRG price used by api if oracle disabled, minimum price recommended by oracle");
            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("default");
            xmlWriter.writeCharacters((new BigDecimal(defaultPrice)).toString());
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeComment("max NRG price recommended by oracle");
            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("max");
            xmlWriter.writeCharacters((new BigDecimal(maxPrice)).toString());
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeComment(
                "enable/diable nrg-oracle service. if disabled, api returns default NRG price if asked for nrgPrice");
            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("oracle-enabled");
            xmlWriter.writeCharacters(String.valueOf(this.oracleEnabled));
            xmlWriter.writeEndElement();

            // close element sync
            xmlWriter.writeCharacters("\r\n\t\t");
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CfgApiNrg cfgApiNrg = (CfgApiNrg) o;
        return defaultPrice == cfgApiNrg.defaultPrice &&
            maxPrice == cfgApiNrg.maxPrice &&
            oracleEnabled == cfgApiNrg.oracleEnabled;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(defaultPrice, maxPrice, oracleEnabled);
    }
}