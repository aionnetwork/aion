package org.aion.mcf.config.applier;

import com.google.common.annotations.VisibleForTesting;
import org.aion.equihash.EquihashMiner;
import org.aion.evtmgr.IEventMgr;
import org.aion.mcf.blockchain.IPendingState;
import org.aion.mcf.config.Cfg;
import org.aion.mcf.config.CfgConsensus;
import org.aion.mcf.config.dynamic2.IDynamicConfigApplier;
import org.aion.mcf.config.dynamic2.InFlightConfigChangeException;
import org.aion.mcf.config.dynamic2.InFlightConfigChangeResult;
import org.aion.zero.impl.AionHub;
import org.aion.zero.impl.blockchain.AionFactory;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.config.CfgConsensusPow;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.pow.AionPoW;
import org.aion.zero.types.AionTransaction;

public class MiningApplier implements IDynamicConfigApplier {
    private static int START_MINING_DELAY_SEC = 5;

    @Override
    public InFlightConfigChangeResult apply(Cfg oldCfg, Cfg newCfg) throws InFlightConfigChangeException {
        boolean oldMining = ((CfgConsensusPow)oldCfg.getConsensus()).getMining();
        boolean newMining = ((CfgConsensusPow)newCfg.getConsensus()).getMining();
        if(oldMining != newMining) {
            if(newMining) {
                startOrResumeMining();
            } else {
                pauseMining();
            }
        }
        return new InFlightConfigChangeResult(true, this);
    }

    @Override
    public InFlightConfigChangeResult undo(Cfg oldCfg, Cfg newCfg) throws InFlightConfigChangeException {
        return apply(newCfg, oldCfg); // same thing as apply, but with the operators reversed
    }

    private void startOrResumeMining() {
        IAionChain ac = AionFactory.create();
        AionHub hub = AionImpl.inst().getAionHub();
        startOrResumeMiningInternal((EquihashMiner)ac.getBlockMiner(),
                AionImpl.inst().getAionHub().getPoW(),
                hub.getBlockchain(),
                hub.getPendingState(),
                hub.getEventMgr());
    }

    /**
     * For testing/internal only.  Production code should not call this because all the other code
     * in kernel only cares about the global singleton instances of these parameters.
     */
    @VisibleForTesting
    void startOrResumeMiningInternal(EquihashMiner equihashMiner,
                                     AionPoW pow,
                                     IAionBlockchain aionBlockchain,
                                     IPendingState<AionTransaction> pendingState,
                                     IEventMgr eventMgr) {
        System.out.println("Mining applier started mining");

        ((CfgConsensusPow)equihashMiner.getCfg().getConsensus()).setMining(true); // awful casting
        equihashMiner.registerCallback(); // should we also unregister when pausing?
        pow.init(aionBlockchain, pendingState, eventMgr); // idempotent; calling it a second time just no-ops
        pow.initThreads();
        pow.resume();
        equihashMiner.delayedStartMining(START_MINING_DELAY_SEC);
    }

    @VisibleForTesting
    void pauseMining() {
        System.out.println("Mining applier stopped mining");
        IAionChain ac = AionFactory.create();
        ((CfgConsensusPow)(
                (EquihashMiner)ac.getBlockMiner()).getCfg().getConsensus()
        ).setMining(false); // fix terrible casting
        AionImpl.inst().getAionHub().pausePow();
//        ac.getBlockMiner().stopMining();
        ((EquihashMiner) ac.getBlockMiner()).pauseMining();
    }
}
