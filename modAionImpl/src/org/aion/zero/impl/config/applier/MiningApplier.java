package org.aion.zero.impl.config.applier;

import com.google.common.annotations.VisibleForTesting;
import org.aion.equihash.EquihashMiner;
import org.aion.evtmgr.IEventMgr;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.IPendingState;
import org.aion.mcf.config.Cfg;
import org.aion.zero.impl.config.dynamic.IDynamicConfigApplier;
import org.aion.zero.impl.config.dynamic.InFlightConfigChangeException;
import org.aion.zero.impl.config.dynamic.InFlightConfigChangeResult;
import org.aion.zero.impl.blockchain.AionFactory;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.config.CfgConsensusPow;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.pow.AionPoW;
import org.aion.zero.types.AionTransaction;
import org.slf4j.Logger;

/**
 * Alters Aion kernel to switch on or off mining.
 */
public class MiningApplier implements IDynamicConfigApplier {
    private EquihashMiner equihashMiner;
    private AionPoW pow;
    private IAionBlockchain aionBlockchain;
    private IPendingState<AionTransaction> pendingState;
    private IEventMgr eventMgr;

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.name());
    private static int START_MINING_DELAY_SEC = 5;

    /**
     * Constructor
     *
     * @param equihashMiner equihash miner that will be altered
     * @param pow pow that will be altered
     * @param aionBlockchain blockchain that pow operates upon
     * @param pendingState pending state used by pow
     * @param eventMgr event manager used by pow
     */
    public MiningApplier(EquihashMiner equihashMiner,
                         AionPoW pow,
                         IAionBlockchain aionBlockchain,
                         IPendingState<AionTransaction> pendingState,
                         IEventMgr eventMgr) {
        this.equihashMiner = equihashMiner;
        this.pow = pow;
        this.aionBlockchain = aionBlockchain;
        this.pendingState = pendingState;
        this.eventMgr = eventMgr;
    }

    /**
     * Constructor using dependencies stored in the global AionHub instance; i.e.
     * {@link AionImpl#getAionHub()}.  Use
     * {@link MiningApplier#MiningApplier(EquihashMiner, AionPoW, IAionBlockchain, IPendingState, IEventMgr)}
     * whenever possible to avoid dependencies on the singleton.
     */
    public MiningApplier() {
        this((EquihashMiner)AionFactory.create().getBlockMiner(),
                AionImpl.inst().getAionHub().getPoW(),
                AionImpl.inst().getAionHub().getBlockchain(),
                AionImpl.inst().getAionHub().getPendingState(),
                AionImpl.inst().getAionHub().getEventMgr());
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

    @VisibleForTesting
    void startOrResumeMining() {
        LOG.info("Mining applier started mining");
        equihashMiner.getCfg().getConsensus().setMining(true);
        equihashMiner.registerCallback(); // when pow and equihashMiner are stopped, the registered
                                          // events aren't sent, so we don't bother unregistering
                                          // when stopped.
        pow.init(aionBlockchain, pendingState, eventMgr); // idempotent; calling it a second time just no-ops
        pow.resume();
        equihashMiner.delayedStartMining(START_MINING_DELAY_SEC);
    }

    @VisibleForTesting
    void pauseMining() {
        LOG.info("Mining applier stopped mining");
        equihashMiner.getCfg().getConsensus().setMining(false);
        pow.pause();
        equihashMiner.stopMining();
    }
}
