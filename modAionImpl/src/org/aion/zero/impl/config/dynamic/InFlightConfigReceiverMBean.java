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

package org.aion.zero.impl.config.dynamic;

import javax.xml.stream.XMLStreamReader;
import org.aion.zero.impl.config.CfgAion;

/**
 * Accepts new Aion kernel configurations.
 *
 * <p>(Not using "I" prefix convention here because need to follow interface-naming convention for
 * MBean).
 */
public interface InFlightConfigReceiverMBean {
    /**
     * Propose a new Aion kernel configuration to use. The new config will be applied to the kernel;
     * an operation which will either entirely succeed or fail (no partial results; if part of the
     * config is applied and then a later part fails, all changes are undone).
     *
     * @param configXmlText XML text that is serializable by {@link
     *     CfgAion#fromXML(XMLStreamReader)}
     * @return result of apply
     * @throw RollbackException if an error was encountered while applying new config, but kernel
     *     failed to restore itself to the initial state.
     */
    ConfigProposalResult propose(String configXmlText) throws RollbackException;
}
