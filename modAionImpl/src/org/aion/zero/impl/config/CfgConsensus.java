package org.aion.zero.impl.config;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public abstract class CfgConsensus {

    public abstract void fromXML(final XMLStreamReader sr) throws XMLStreamException;
}
