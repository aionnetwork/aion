package org.aion.stake;

import static org.aion.util.bytes.ByteUtil.toHexString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.callback.EventCallback;
import org.aion.evtmgr.impl.es.EventExecuteService;
import org.aion.evtmgr.impl.evt.EventConsensus;
import org.aion.evtmgr.impl.evt.EventMiner;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.stake.StakeRunnerInterface;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.StakingBlock;
import org.slf4j.Logger;

public class StakeRunner implements StakeRunnerInterface {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.name());
    private boolean isStaking;
    private volatile StakingBlock proposingBlock;

    private final CfgAion cfg;

    private final IEventMgr evtMgr;

    private final EventExecuteService ees;

    private Thread thread = null;

    // TODO: [unity] hard code private key
    private byte[] privateKey =
            ByteUtil.hexStringToBytes(
                    "0xcc76648ce8798bc18130bc9d637995e5c42a922ebeab78795fac58081b9cf9d4069346ca77152d3e42b1630826feef365683038c3b00ff20b0ea42d7c121fa9f");

    private ECKey key = ECKeyFac.inst().fromPrivate(privateKey);

    private final class EpMiner implements Runnable {
        boolean go = true;

        @Override
        public void run() {
            while (go) {
                IEvent e = ees.take();
                if (e.getEventType() == IHandler.TYPE.CONSENSUS.getValue()
                        && e.getCallbackType()
                                == EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE.getValue()) {
                    StakeRunner.this.onBlockTemplate((StakingBlock) e.getFuncArgs().get(0));
                } else if (e.getEventType() == IHandler.TYPE.POISONPILL.getValue()) {
                    go = false;
                }
            }
        }
    }

    private static class Holder {
        static final StakeRunner INSTANCE = new StakeRunner();
    }

    /**
     * Singleton instance
     *
     * @return Equihash miner instance
     */
    public static StakeRunner inst() {
        return Holder.INSTANCE;
    }

    /** Private constructor; called by singleton instance once */
    private StakeRunner() {
        this.cfg = CfgAion.inst();

        IAionChain a0Chain = AionImpl.inst();

        ees = new EventExecuteService(1000, "EpMiner", Thread.NORM_PRIORITY, LOG);
        ees.setFilter(setEvtFilter());

        this.evtMgr = a0Chain.getAionHub().getEventMgr();
        registerMinerEvents();
        registerCallback();

        ees.start(new EpMiner());
    }

    private Set<Integer> setEvtFilter() {
        Set<Integer> eventSN = new HashSet<>();

        int sn = IHandler.TYPE.CONSENSUS.getValue() << 8;
        eventSN.add(sn + EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE.getValue());

        return eventSN;
    }

    @Override
    public void startStaking() {
        if (!isStaking) {
            isStaking = true;
            fireRunnerStarted();

            thread = new Thread(this::staking, "staker");

            thread.start();
            LOG.info("Pos staker starting.");
        }
    }

    @Override
    public void stopStaking() {
        if (isStaking) {
            isStaking = false;
            fireRunnerStopped();
            LOG.info("Pos staker stopping.");

            //            scheduledWorkers.shutdownNow();

            thread.interrupt();
            LOG.info("Interrupt staker.");

            try {
                thread.join();
                LOG.info("Stopped staker.");
            } catch (InterruptedException e) {
                LOG.error("Failed to stop staker thread");
            }
        }
    }

    /** Keeps staking until the thread is interrupted */
    private void staking() {
        StakingBlock block;

        while (!Thread.currentThread().isInterrupted()) {
            if ((block = proposingBlock) == null) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    break;
                }
            } else {

                byte[] sig = key.sign(block.getHeader().getMineHash()).getSignature();

                block.getHeader().setSignature(sig);
                block.getHeader().setPubKey(key.getPubKey());

                IEvent ev = new EventConsensus(EventConsensus.CALLBACK.ON_STAKE_SIG);
                ev.setFuncArgs(Collections.singletonList(block));
                evtMgr.newEvent(ev);

                proposingBlock = null;
            }
        }
    }

    /** Restart the mining process when a new block template is received. */
    private void onBlockTemplate(StakingBlock block) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("onBlockTemplate(): {}", toHexString(block.getHash()));
        }

        // Do not change reference if the event passes a null reference
        if (isStaking() && block != null) {
            proposingBlock = block;
        }
    }

    /**
     * Start block mining after sec seconds
     *
     * @param sec The number of seconds to wait until beginning to mine blocks
     */
    public void delayedStartStaking(int sec) {
        if (cfg.getConsensus().getStaking()) {
            LOG.info("<delayed-start-staking>");
            Timer t = new Timer();
            t.schedule(
                    new TimerTask() {
                        @Override
                        public void run() {
                            if (cfg.getConsensus().getStaking()) {
                                startStaking();
                            }
                        }
                    },
                    sec * 1000);
        } else {
            LOG.info("<staking-disabled>");
        }
    }

    /** This miner will listen to the ON_BLOCK_TEMPLATE event from the consensus handler. */
    private void registerCallback() {
        // Only register events if actual mining
        if (cfg.getConsensus().getStaking()) {
            if (this.evtMgr != null) {
                IHandler hdrCons = this.evtMgr.getHandler(4);
                if (hdrCons != null) {
                    hdrCons.eventCallback(new EventCallback(ees, LOG));
                }
            } else {
                LOG.error("event manager is null");
            }
        }
    }

    /** Register miner events. */
    private void registerMinerEvents() {
        List<IEvent> evts = new ArrayList<>();
        evts.add(new EventMiner(EventMiner.CALLBACK.MININGSTARTED));
        evts.add(new EventMiner(EventMiner.CALLBACK.MININGSTOPPED));
        this.evtMgr.registerEvent(evts);
    }

    public void fireRunnerStarted() {
        if (this.evtMgr != null) {
            this.evtMgr.newEvent(new EventMiner(EventMiner.CALLBACK.MININGSTARTED));
        }
    }

    public void fireRunnerStopped() {
        if (this.evtMgr != null) {
            this.evtMgr.newEvent(new EventMiner(EventMiner.CALLBACK.MININGSTOPPED));
        }
    }

    public boolean isStaking() {
        return isStaking;
    }

    public void shutdown() {
        ees.shutdown();
    }
}
