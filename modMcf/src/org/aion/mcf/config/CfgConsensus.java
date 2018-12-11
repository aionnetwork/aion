package org.aion.mcf.config;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public abstract class CfgConsensus {

    public abstract void fromXML(final XMLStreamReader sr) throws XMLStreamException;
}
