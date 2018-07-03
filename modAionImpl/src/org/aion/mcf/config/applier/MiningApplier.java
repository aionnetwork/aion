package org.aion.mcf.config.applier;

import org.aion.equihash.EquihashMiner;
import org.aion.mcf.config.Cfg;
import org.aion.mcf.config.dynamic2.IDynamicConfigApplier;
import org.aion.mcf.config.dynamic2.InFlightConfigChangeException;
import org.aion.mcf.config.dynamic2.InFlightConfigChangeResult;
import org.aion.zero.impl.AionHub;
import org.aion.zero.impl.blockchain.AionFactory;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.config.CfgConsensusPow;

public class MiningApplier implements IDynamicConfigApplier {

    @Override
    public InFlightConfigChangeResult apply(Cfg oldCfg, Cfg newCfg) throws InFlightConfigChangeException {
        boolean oldMining = ((CfgConsensusPow)oldCfg.getConsensus()).getMining();
        boolean newMining = ((CfgConsensusPow)newCfg.getConsensus()).getMining();
        if(oldMining != newMining) {
            if(newMining) {
                startMining();
            } else {
                stopMining();
            }
        }
        return new InFlightConfigChangeResult(true, this);
    }

    @Override
    public InFlightConfigChangeResult undo(Cfg oldCfg, Cfg newCfg) throws InFlightConfigChangeException {
        return apply(newCfg, oldCfg); // same thing as apply, but with the operators reversed
    }

    private void startMining() {
        System.out.println("Mining applier started mining");
        IAionChain ac = AionFactory.create();
        ((CfgConsensusPow)((EquihashMiner)ac.getBlockMiner()).getCfg().getConsensus()).setMining(true); // fix terrible casting
        ((EquihashMiner)ac.getBlockMiner()).registerCallback(); // TODO does stopMining need to unregister these?
        AionHub.inst().initPow(); // does stopMining need to turn this off?
        ac.getBlockMiner().delayedStartMining(10);
    }

    private void stopMining() {
        System.out.println("Mining applier stopped mining");
        IAionChain ac = AionFactory.create();
        ((CfgConsensusPow)((EquihashMiner)ac.getBlockMiner()).getCfg().getConsensus()).setMining(true); // fix terrible casting
        ac.getBlockMiner().stopMining();
    }
}
