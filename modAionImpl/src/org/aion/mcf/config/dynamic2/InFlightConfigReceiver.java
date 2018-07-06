package org.aion.mcf.config.dynamic2;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.CharSource;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.config.Cfg;
import org.aion.zero.impl.config.CfgAion;
import org.slf4j.Logger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Responsible for receiving new Aion kernel configurations and applying them to the kernel
 * while it is running ("in-flight").
 *
 * New configurations can be submitted through the {@link #propose(String)} method.  This is
 * exposed through the JMX interface if it needs to be called from a separate process.
 *
 * When new configurations are proposed, they can be accepted or rejected.  If any error occurs
 * while applying a new configuration, it is rejected.  When it is accepted, the new configuration
 * is used in its entirety; similarly, if it is rejected, no changes at all should be made to
 * kernel (any changes applied up until the error must be reverted).
 *
 * Not all properties in <tt>config.xml</tt> are modifiable in-flight.  If a change to a such a
 * property is present in the new configuration, it will be rejected.
 */
public class InFlightConfigReceiver implements InFlightConfigReceiverMBean {
    private final Cfg activeCfg;
    private final DynamicConfigKeyRegistry configKeyRegistry;

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.name());

    public InFlightConfigReceiver(Cfg activeCfg, DynamicConfigKeyRegistry configKeyRegistry) {
        this.activeCfg = activeCfg;
        this.configKeyRegistry = configKeyRegistry;
    }

    /**
     * Attempt to apply a new config.  If any error occurs, any modification to the config that
     * has already been applied will be rolled back.  If successful, the new config will become
     * the active config.
     */
    @Override
    public synchronized ConfigProposalResult propose(String configXmlText) throws RollbackException {
        LOG.trace("Received new configuration XML");
        LOG.trace(configXmlText);
        CfgAion newCfg = new CfgAion();
        try {
            XMLStreamReader xmlStream = XMLInputFactory.newInstance()
                    .createXMLStreamReader(CharSource.wrap(configXmlText).openStream());
            newCfg.fromXML(xmlStream);
        } catch (XMLStreamException | IOException | NumberFormatException ex) {
            LOG.error("Error constructing Cfg from given XML.  Rejecting Cfg proposal.  Exception was: {}", ex);
            return new ConfigProposalResult(false, ex);
        }

        return applyNewConfig(newCfg);
    }

    @VisibleForTesting ConfigProposalResult applyNewConfig(Cfg newCfg) throws RollbackException {
        // build up an undo stack as we apply each config change so we can rollback if error
        final Deque<InFlightConfigChangeResult> undoSteps = new ArrayDeque<>();

        for(String key : configKeyRegistry.getBoundKeys()) {
            Function<Cfg, ?> getter = configKeyRegistry.getGetter(key);
            if(getter == null) {
                throw new IllegalStateException(String.format(
                        "DynamicConfigKeyRegistry configuration error.  There is no getter for the bound key '%s'",
                        key));
            }
            Object newVal = getter.apply(newCfg);
            Object oldVal = getter.apply(activeCfg);
            if(Objects.equals(newVal, oldVal)) {
                continue;
            }

            IDynamicConfigApplier applier = configKeyRegistry.getApplier(key);
            if(applier == null) {
                throw new IllegalStateException(String.format(
                        "DynamicConfigKeyRegistry configuration error.  There is no applier for the bound key '%s'",
                        key));
            }

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
            if(!result.isSuccess()) {
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
        while(!steps.isEmpty()) {
            InFlightConfigChangeResult result = steps.pop();
            try {
                LOG.trace("About to call undo for application result {}", result);
                result.getApplier().undo(activeCfg, newCfg);
            } catch (InFlightConfigChangeException ex) {
                exceptions.add(ex);
                LOG.error(String.format("Rollback error while trying to undo %s", result.toString()));
            }
        }
        if(!exceptions.isEmpty()) {
            throw new RollbackException("Rollback had errors", exceptions);
        }
    }
}
