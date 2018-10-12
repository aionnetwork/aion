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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.CharSource;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.config.Cfg;
import org.aion.zero.impl.config.CfgAion;
import org.slf4j.Logger;

/**
 * Responsible for receiving new Aion kernel configurations and applying them to the kernel while it
 * is running ("in-flight").
 *
 * New configurations can be submitted through the {@link #propose(String)} method.  This is exposed
 * through the JMX interface if it needs to be called from a separate process.
 *
 * When new configurations are proposed, they can be accepted or rejected.  If any error occurs
 * while applying a new configuration, it is rejected.  When it is accepted, the new configuration
 * is used in its entirety; similarly, if it is rejected, no changes at all should be made to kernel
 * (any changes applied up until the error must be reverted).
 *
 * Not all properties in <tt>config.xml</tt> are modifiable in-flight.  If a change to a such a
 * property is present in the new configuration, it will be rejected.
 */
public class InFlightConfigReceiver implements InFlightConfigReceiverMBean {

    /**
     * Default JMX port
     */
    public static final int DEFAULT_JMX_PORT = 11234; /* TODO put in config.xml */
    /**
     * Default JMX object name
     */
    public static final String DEFAULT_JMX_OBJECT_NAME = "org.aion.mcf.config.receiver:id=1";
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.name());
    private final Cfg activeCfg;
    private final DynamicConfigKeyRegistry configKeyRegistry;

    public InFlightConfigReceiver(Cfg activeCfg, DynamicConfigKeyRegistry configKeyRegistry) {
        this.activeCfg = activeCfg;
        this.configKeyRegistry = configKeyRegistry;
    }

    /**
     * @return a JMX URL that client can use to connect to JMX server.
     */
    public static String createJmxUrl(int port) {
        return String.format("service:jmx:rmi:///jndi/rmi://127.0.0.1:%d/jmxrmi", port);
    }

    /**
     * Attempt to apply a new config.  If any error occurs, any modification to the config that has
     * already been applied will be rolled back.  If successful, the new config will become the
     * active config.
     */
    @Override
    public synchronized ConfigProposalResult propose(String configXmlText)
        throws RollbackException {
        LOG.trace("Received new configuration XML");
        LOG.trace(configXmlText);
        CfgAion newCfg = new CfgAion();
        try {
            XMLStreamReader xmlStream = XMLInputFactory.newInstance()
                .createXMLStreamReader(CharSource.wrap(configXmlText).openStream());
            newCfg.fromXML(xmlStream);
        } catch (XMLStreamException | IOException | NumberFormatException ex) {
            LOG.error(
                "Error constructing Cfg from given XML.  Rejecting Cfg proposal.  Exception was: {}",
                ex);
            return new ConfigProposalResult(false, ex);
        }

        ConfigProposalResult result = applyNewConfig(newCfg);
        if (result.isSuccess()) {
            // At kernel start-up time, values from CfgAion.inst() are read and copied
            // to other places.  Right now we don't do anything to find/update those
            // values (assume the IDynamicConfigApplier subclasses will all do the
            // necessary updating correctly).  Revisit this that becomes too
            // confusing/spaghetti-like.
            CfgAion.setInst(newCfg);
        }
        return result;
    }

    @VisibleForTesting
    ConfigProposalResult applyNewConfig(Cfg newCfg) throws RollbackException {
        // build up an undo stack as we apply each config change so we can rollback if error
        final Deque<InFlightConfigChangeResult> undoSteps = new ArrayDeque<>();

        for (String key : configKeyRegistry.getBoundKeys()) {
            Function<Cfg, ?> getter = configKeyRegistry.getGetter(key);
            if (getter == null) {
                throw new IllegalStateException(String.format(
                    "DynamicConfigKeyRegistry configuration error.  There is no getter for the bound key '%s'",
                    key));
            }
            Object newVal = getter.apply(newCfg);
            Object oldVal = getter.apply(activeCfg);
            if (Objects.equals(newVal, oldVal)) {
                continue;
            }

            Optional<IDynamicConfigApplier> maybeApplier = configKeyRegistry.getApplier(key);
            if (!maybeApplier.isPresent()) {
                throw new IllegalArgumentException(String.format(
                    "The key '%s' does not support in-flight configuration change", key));
            } else if (maybeApplier == null) {
                throw new IllegalStateException(String.format(
                    "DynamicConfigKeyRegistry configuration error.  There is no applier for the bound key '%s'",
                    key));
            }
            IDynamicConfigApplier applier = maybeApplier.get();

            final InFlightConfigChangeResult result;
            try {
                LOG.trace("About to call applier.apply {} for key '{}'", applier, key);
                result = applier.apply(activeCfg, newCfg);
            } catch (InFlightConfigChangeException ex) {
                // if applier.apply worked partially then threw, kernel might be in some broken
                // state now; might as well try to fix ourselves as much as possible with rollback
                // though.
                LOG.error(String.format(
                    "Error applying change to config key '%s'.  Will attempt rollback.", key), ex);
                rollback(undoSteps, newCfg);
                return new ConfigProposalResult(false, ex);
            }

            LOG.trace("Applier {} returned '{}' for key '{}'", applier, result, key);
            if (!result.isSuccess()) {
                LOG.error(String.format(
                    "Could not apply change to config key '%s'.  Will attempt rollback.", key));
                rollback(undoSteps, newCfg);
                return new ConfigProposalResult(false);
            }

            undoSteps.push(result);
        }
        return new ConfigProposalResult(true);
    }

    private void rollback(Deque<InFlightConfigChangeResult> steps, Cfg newCfg)
        throws RollbackException {
        LOG.info("Trying to rollback.  Undo steps are: {}", steps.toString());

        List<InFlightConfigChangeException> exceptions = new LinkedList<>();
        while (!steps.isEmpty()) {
            InFlightConfigChangeResult result = steps.pop();
            try {
                LOG.trace("About to call undo for application result {}", result);
                result.getApplier().undo(activeCfg, newCfg);
            } catch (InFlightConfigChangeException ex) {
                exceptions.add(ex);
                LOG.error(
                    String.format("Rollback error while trying to undo %s", result.toString()));
            }
        }
        if (!exceptions.isEmpty()) {
            throw new RollbackException("Rollback had errors", exceptions);
        }
    }
}
