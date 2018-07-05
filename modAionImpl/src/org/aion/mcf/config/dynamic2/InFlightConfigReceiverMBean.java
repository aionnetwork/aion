package org.aion.mcf.config.dynamic2;

import org.aion.zero.impl.config.CfgAion;

import javax.xml.stream.XMLStreamReader;

/**
 * Accepts new Aion kernel configurations.
 *
 * (Not using "I" prefix convention here because need to follow interface-naming convention for MBean).
 */
public interface InFlightConfigReceiverMBean {
    /**
     * Propose a new Aion kernel configuration to use.  The new config will be applied to the
     * kernel; an operation which will either entirely succeed or fail (no partial results; if
     * part of the config is applied and then a later part fails, all changes are undone).
     *
     * @param configXmlText XML text that is serializable by {@link CfgAion#fromXml(XMLStreamReader)}
     * @return result of apply
     */
    ConfigProposalResult propose(String configXmlText) throws RollbackException;
}
