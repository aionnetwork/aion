package org.aion.mcf.config.dynamic2;

import com.google.common.io.CharSource;
import org.aion.mcf.config.Cfg;
import org.aion.zero.impl.config.CfgAion;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Function;

public class InFlightConfigReceiver implements InFlightConfigReceiverMBean {
    private final Cfg activeCfg;
    private final DynamicConfigKeyRegistry configKeyRegistry;

    public InFlightConfigReceiver(Cfg activeCfg, DynamicConfigKeyRegistry configKeyRegistry) {
        this.activeCfg = activeCfg;
        this.configKeyRegistry = configKeyRegistry;
    }

    /**
     * Attempt to apply a new config.  If any error occurs, any modification to the config that
     * has already been applied will be rolled back.  If successful, the new config will become
     * the active config.
     *
     * It is possible that this operation succeeds, but there are problems in the config.  This
     * occurs because some properties cannot be applied to a running kernel.  In this case, those
     * errors will only surface when the kernel is restarted.
     *
     * TODO: or should it be the inverse, reject any property change that can't be dynamically applied?
     *
     */
    @Override
    public ConfigProposalResult propose(String configXmlText) {
        CfgAion newCfg = new CfgAion();
        try {
            XMLStreamReader xmlStream = XMLInputFactory.newInstance()
                    .createXMLStreamReader(CharSource.wrap(configXmlText).openStream());
            newCfg.fromXML(xmlStream);
        } catch (Exception e) {
            e.printStackTrace(); //FIXME
            return null;
        }

        System.out.println("Got new proposal.  Cfg.id = " + newCfg.getId());

        try {
            return diffAndNotify(newCfg);
        } catch (InFlightConfigChangeException e) {
            System.out.println("InFlightConfigReceiver#propose - error");
            e.printStackTrace();
            return new ConfigProposalResult(false);
        }
    }

    private ConfigProposalResult diffAndNotify(Cfg newCfg) throws InFlightConfigChangeException {
        // build up an undo stack as we apply each config change so we can rollback if error
        Deque<InFlightConfigChangeResult> undoSteps = new ArrayDeque<>();

        //
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
            try {
                InFlightConfigChangeResult result = applier.apply(activeCfg, newCfg);
                undoSteps.push(result);
            } catch (InFlightConfigChangeNotAllowedException ndkce) {
                System.out.println("InFlightConfigChangeNotAllowedException for " + key);
                rollback(undoSteps, newCfg);
                return new ConfigProposalResult(false);
            } catch (InFlightConfigChangeException ifcce) {
                rollback(undoSteps, newCfg);
                return new ConfigProposalResult(false);
            }
        }
        return new ConfigProposalResult(true);
    }

    private void rollback(Deque<InFlightConfigChangeResult> steps, Cfg newCfg) {
        while(!steps.isEmpty()) {
            InFlightConfigChangeResult result = steps.pop();
            try {
                result.getApplier().undo(activeCfg, newCfg);
            } catch (InFlightConfigChangeException e) {
                System.out.println("Fatal error - couldn't roll back dynamic config.");
                e.printStackTrace();
            }
        }
    }
}
