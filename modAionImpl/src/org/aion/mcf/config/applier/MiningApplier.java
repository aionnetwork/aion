package org.aion.mcf.config.applier;

import com.google.common.annotations.VisibleForTesting;
import org.aion.equihash.EquihashMiner;
import org.aion.evtmgr.IEventMgr;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
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
import org.slf4j.Logger;

public class MiningApplier implements IDynamicConfigApplier {
    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.name());
    private static int START_MINING_DELAY_SEC = 5;

    boolean run = false;
    public MiningApplier() {
        run = false;
    }

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
                hub.getPoW(),
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
        LOG.info("Mining applier started mining");

        ((CfgConsensusPow)equihashMiner.getCfg().getConsensus()).setMining(true); // awful casting
        equihashMiner.registerCallback(); // should we also unregister when pausing?
        pow.init(aionBlockchain, pendingState, eventMgr); // idempotent; calling it a second time just no-ops
        if(!run) {
            pow.initThreads();
            run = true;
        }
        pow.resume();
        equihashMiner.delayedStartMining(START_MINING_DELAY_SEC);
    }

    @VisibleForTesting
    void pauseMining() {
        IAionChain ac = AionFactory.create();
        pauseMiningInternal((EquihashMiner)ac.getBlockMiner(), AionImpl.inst().getAionHub().getPoW());
    }

    @VisibleForTesting
    void pauseMiningInternal(EquihashMiner miner,
                             AionPoW pow) {
        LOG.info("Mining applier stopped mining");
        ((CfgConsensusPow)(miner.getCfg()).getConsensus()).setMining(false); // fix terrible casting
//        AionImpl.inst().getAionHub().pausePow();
//        ac.getBlockMiner().stopMining();
        pow.pause();
        miner.pauseMining();
    }
}
