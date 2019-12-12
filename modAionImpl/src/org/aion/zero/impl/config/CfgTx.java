package org.aion.zero.impl.config;

import com.google.common.annotations.VisibleForTesting;
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
        this.poolDump = false;
        this.poolBackup = false;
        this.pendingTransactionTimeout = 3600;
        this.seedMode = false;
    }

    private int cacheMax;

    private boolean poolDump;

    private boolean poolBackup;

    private int pendingTransactionTimeout;

    private boolean seedMode;

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "cachemax":
                            this.cacheMax = Integer.parseInt(ConfigUtil.readValue(sr));
                            if (this.cacheMax < 128) {
                                this.cacheMax = 128;
                            } else if (this.cacheMax > 16384) { // 16GB
                                this.cacheMax = 16384;
                            }
                            break;
                        case "pooldump":
                            this.poolDump = Boolean.parseBoolean(ConfigUtil.readValue(sr));
                            break;
                        case "poolbackup":
                            this.poolBackup = Boolean.parseBoolean(ConfigUtil.readValue(sr));
                            break;
                        case "pendingtxtimeout":
                            this.pendingTransactionTimeout = Integer.parseInt(ConfigUtil.readValue(sr));
                            if (this.pendingTransactionTimeout < 60) { // 1 min
                                this.pendingTransactionTimeout = 60;
                            }
                            break;
                        case "seedmode":
                            this.seedMode = Boolean.parseBoolean(ConfigUtil.readValue(sr));
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
            xmlWriter.writeComment("Sets max TransactionPoolCaching size by 0.1MB incremental unit");
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("cacheMax");
            xmlWriter.writeCharacters(String.valueOf(this.getCacheMax()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeComment("Sets pending transaction timeout threshold in the transaction pool");
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("txPendingTimeout");
            xmlWriter.writeCharacters(String.valueOf(this.getTxPendingTimeout()));
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeComment("Sets the pending transactions backup to the Database");
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("poolBackup");
            xmlWriter.writeCharacters(String.valueOf(this.getPoolBackup()));
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

    public int getTxPendingTimeout() {
        return this.pendingTransactionTimeout;
    }

    public int getCacheMax() {
        return this.cacheMax;
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
                && poolDump == cfgTx.poolDump
                && poolBackup == cfgTx.poolBackup
                && pendingTransactionTimeout == cfgTx.pendingTransactionTimeout;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(cacheMax, poolDump, poolBackup, pendingTransactionTimeout);
    }

    public boolean isSeedMode() {
        return seedMode;
    }

    @VisibleForTesting
    public void setSeedMode(final boolean value) {
        seedMode = value;
    }
}
