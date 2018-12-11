package org.aion.mcf.config;

import com.google.common.base.Objects;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/** @author chris */
public class CfgTx {

    public CfgTx() {
        this.cacheMax = 256; // by 0.1M;
        this.buffer = true;
        this.poolDump = false;
        this.poolBackup = false;
    }

    private int cacheMax;

    private boolean buffer;

    private boolean poolDump;

    private boolean poolBackup;

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "cachemax":
                            this.cacheMax = Integer.parseInt(Cfg.readValue(sr));
                            if (this.cacheMax < 128) {
                                this.cacheMax = 128;
                            } else if (this.cacheMax > 16384) { // 16GB
                                this.cacheMax = 16384;
                            }
                            break;
                        case "buffer":
                            this.buffer = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case "pooldump":
                            this.poolDump = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case "poolbackup":
                            this.poolBackup = Boolean.parseBoolean(Cfg.readValue(sr));
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
            xmlWriter.writeStartElement("tx");

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("cacheMax");
            xmlWriter.writeCharacters(String.valueOf(this.getCacheMax()));
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

    public int getCacheMax() {
        return this.cacheMax;
    }

    public boolean getBuffer() {
        return this.buffer;
    }

    public boolean getPoolDump() {
        return poolDump;
    }

    public boolean getPoolBackup() {
        return poolBackup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CfgTx cfgTx = (CfgTx) o;
        return cacheMax == cfgTx.cacheMax
                && buffer == cfgTx.buffer
                && poolDump == cfgTx.poolDump
                && poolBackup == cfgTx.poolBackup;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(cacheMax, buffer, poolDump, poolBackup);
    }
}
