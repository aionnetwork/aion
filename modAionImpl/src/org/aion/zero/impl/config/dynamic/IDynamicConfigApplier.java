package org.aion.zero.impl.config.dynamic;

import org.aion.zero.impl.config.CfgAion;

/**
 * A class which, given two {@link CfgAion} objects, takes some action on the running Aion kernel based
 * on the difference between values in the Cfgs. The class need not take action on every difference
 * in the CfgAion. Intended to be used by {@link InFlightConfigReceiver} to alter the kernel when given
 * new CfgAion.
 *
 * <p>Any change that is performed by {@link #apply(CfgAion, CfgAion)} must be revertible by calling {@link
 * #undo(CfgAion, CfgAion)} when both are called with the same arguments.
 */
public interface IDynamicConfigApplier {
    /**
     * Alter the Aion kernel based on some config change between oldCfg and newCfg. The change must
     * be revertible by {@link #undo(CfgAion, CfgAion)}. If this method fails when applying the intended
     * alteration, it must try to recover by reverting any partial changes then return {@link
     * InFlightConfigChangeResult} with success set to false. If it failed to revert then it must
     * throw an exception.
     *
     * @param oldCfg old config
     * @param newCfg new config
     * @return result of the alteration
     * @throws InFlightConfigChangeException if could not perform the alteration to kernel
     */
    InFlightConfigChangeResult apply(CfgAion oldCfg, CfgAion newCfg) throws InFlightConfigChangeException;

    /**
     * Undo changes to the Aion kernel that were successfully performed by {@link #apply(CfgAion, CfgAion)}.
     *
     * @param oldCfg old config
     * @param newCfg new config
     * @return result of the alteration
     * @throws InFlightConfigChangeException if could not perform the alteration to kernel
     */
    InFlightConfigChangeResult undo(CfgAion oldCfg, CfgAion newCfg) throws InFlightConfigChangeException;
}
