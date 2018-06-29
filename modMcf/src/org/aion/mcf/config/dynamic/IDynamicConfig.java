package org.aion.mcf.config.dynamic;

import org.aion.mcf.config.Cfg;
import org.aion.mcf.config.ConfigProposalResult;

/**
 * A config that is mutable and can dynamically apply properties to a running Aion kernel.
 *
 * Holds a config that is designated the "Active" config (i.e. what the kernel is running).
 *
 * Clients who need to modify the config can propose new configurations.  Implementors of this
 * interface must try to apply that config to the kernel.  If an error occurs, all changes that
 * have already been applied must be reverted.
 *
 * Responsibility of updating the kernel can be delegated to other classes.  Classes who are
 * involved in this updating process can register themselves as listeners.  Any proposed config
 * changes are broadcast to these listeners.
 */
public interface IDynamicConfig {

    /**
     * Get the config that the kernel is currently using
     * @return the config that the kernel is currently using
     */
    Cfg getActiveCfg();

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
    ConfigProposalResult proposeCfg(String xml);

    /**
     * Registers a listener to be notified of config changes
     */
    void register(ConfigObserver observer);

    /**
     * Unregister a listener
     */
    void unregister(ConfigObserver observer);
}
