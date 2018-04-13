package org.aion.zero.impl.config;

import org.aion.zero.impl.core.energy.EnergyStrategies;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import static org.aion.zero.impl.core.energy.EnergyStrategies.CLAMPED_DECAYING;
import static org.aion.zero.impl.core.energy.EnergyStrategies.MONOTONIC;

/**
 * Defines options for energy strategy, currently we only support
 * a handful
 */
public class CfgEnergyStrategy {

    /**
     * Set default strategy to clamped (safest option), allows the network
     * to be somewhat flexible
     */
    private EnergyStrategies strategy = EnergyStrategies.CLAMPED_DECAYING;

    /**
     * Coefficients for {@link EnergyStrategies#CLAMPED_DECAYING}
     */
    private long lowerBound = 15_000_000L;
    private long upperBound = 25_000_000L;
    private static final String UPPER_BOUND = "upper-bound";
    private static final String LOWER_BOUND = "lower-bound";

    /**
     * Coefficients for {@link EnergyStrategies#TARGETTED}
     */
    private long target = 10_000_000L;
    private static final String TARGET = "target";

    public CfgEnergyStrategy() {
    }

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        int it = 0;
        loop: while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    EnergyStrategies strategies = EnergyStrategies.getStrategy(elementName);

                    if (strategies != null) {
                        switch (strategies) {
                            case MONOTONIC:
                                // decode the remaining elements
                                strategy = MONOTONIC;
                                sr.next();
                                break;
                            case CLAMPED_DECAYING:
                                strategy = EnergyStrategies.CLAMPED_DECAYING;
                                parseClampedDecaying(sr);
                                sr.next();
                                break;
                            case TARGETTED:
                                strategy = EnergyStrategies.TARGETTED;
                                parseTarget(sr);
                                sr.next();
                                break;
                            case DECAYING:
                                strategy = EnergyStrategies.DECAYING;
                                sr.next();
                                break;
                        }
                    }
                    it++;

                    if (it > 1)
                        throw new IllegalArgumentException("cannot select more than one strategy");

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

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement(CLAMPED_DECAYING.getLabel());
            xmlWriter.writeAttribute(UPPER_BOUND, String.valueOf(this.upperBound));
            xmlWriter.writeAttribute(LOWER_BOUND, String.valueOf(this.lowerBound));
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

    public void parseTarget(final XMLStreamReader sr) {
        if (sr.getAttributeCount() != 1)
            throw new IllegalArgumentException("energyStrategy expecting target ATTRIBUTE");
        String name = sr.getAttributeLocalName(0);

        if (!name.equals(TARGET))
            throw new IllegalArgumentException("energyStrategy expecting ATTRIBUTED named " + TARGET);

        String value = sr.getAttributeValue(0);
        long target = Long.parseLong(value);

        if (target < 0)
            throw new IllegalArgumentException("energyStrategy target must be positive");
        this.target = target;
    }

    // TODO: we did not cover the case where arguments are same
    public void parseClampedDecaying(final XMLStreamReader sr) {
        if (sr.getAttributeCount() != 2)
            throw new IllegalArgumentException("energyStrategy expecting upper-bound, lower-bound ATTRIBUTE");

        for (int i = 0; i < 2; i++) {
            String name = sr.getAttributeLocalName(i);
            long val = Long.parseLong(sr.getAttributeValue(i));
            if (val < 0)
                throw new IllegalArgumentException("energyStrategy value must be positive");

            switch(name) {
                case UPPER_BOUND:
                    this.upperBound = val;
                    break;
                case LOWER_BOUND:
                    this.lowerBound = val;
                    break;
                default:
                    throw new IllegalArgumentException("unexpected entry");
            }
        }
    }

    public EnergyStrategies getStrategy() {
        return this.strategy;
    }

    public long getTarget() {
        return this.target;
    }

    public long getLowerBound() {
        return this.lowerBound;
    }

    public long getUpperBound() {
        return this.upperBound;
    }
}
