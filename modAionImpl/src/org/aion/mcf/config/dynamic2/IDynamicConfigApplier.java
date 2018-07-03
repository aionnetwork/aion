package org.aion.mcf.config.dynamic2;

import org.aion.mcf.config.Cfg;

public interface IDynamicConfigApplier {
    InFlightConfigChangeResult apply(Cfg oldCfg, Cfg newCfg) throws InFlightConfigChangeException;
    InFlightConfigChangeResult undo(Cfg oldCfg, Cfg newCfg) throws InFlightConfigChangeException;
}