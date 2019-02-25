package org.aion.mcf.config;

import com.google.common.base.Objects;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/** Api configuration class. */
public final class CfgApi {

    private CfgApiZmq zmq;
    private CfgApiRpc rpc;
    private CfgApiNrg nrg;

    public CfgApi() {
        this.rpc = new CfgApiRpc();
        this.zmq = new CfgApiZmq();
        this.nrg = new CfgApiNrg();
    }

    public CfgApiRpc getRpc() {
        return this.rpc;
    }

    public CfgApiZmq getZmq() {
        return this.zmq;
    }

    public CfgApiNrg getNrg() {
        return this.nrg;
    }

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop:
        while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    switch (sr.getLocalName()) {
                        case "java":
                            this.zmq.fromXML(sr);
                            break;
                        case "rpc":
                            this.rpc.fromXML(sr);
                            break;
                        case "nrg-recommendation":
                            this.nrg.fromXML(sr);
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
        output.setProperty("escapeCharacters", false);
        XMLStreamWriter xmlWriter;
        String xml;
        try {
            Writer strWriter = new StringWriter();
            xmlWriter = output.createXMLStreamWriter(strWriter);
            xmlWriter.writeCharacters("\r\n\t");
            xmlWriter.writeStartElement("api");

            xmlWriter.writeCharacters(this.rpc.toXML());
            xmlWriter.writeCharacters(this.zmq.toXML());
            xmlWriter.writeCharacters(this.nrg.toXML());

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CfgApi cfgApi = (CfgApi) o;
        return Objects.equal(zmq, cfgApi.zmq)
                && Objects.equal(rpc, cfgApi.rpc)
                && Objects.equal(nrg, cfgApi.nrg);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(zmq, rpc, nrg);
    }
}
