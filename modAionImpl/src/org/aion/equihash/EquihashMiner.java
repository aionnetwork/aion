package org.aion.equihash;

import static org.aion.util.conversions.Hex.toHexString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.callback.EventCallback;
import org.aion.evtmgr.impl.es.EventExecuteService;
import org.aion.evtmgr.impl.evt.EventConsensus;
import org.aion.evtmgr.impl.evt.EventMiner;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.util.others.MAF;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

/** @author Ross Kitsis (ross@nuco.io) */
public class EquihashMiner {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.name());

    private int cpuThreads;

    private boolean isMining;

    private volatile AionBlock miningBlock;

    public static final String VERSION = "0.1.0";

    private final CfgAion cfg;

    private final IEventMgr evtMgr;

    // Equihash solver implementation
    private final Equihash miner;

    // 15 second show status delay
    private static final int STATUS_INTERVAL = 15;

    // Status scheduler
    private final ScheduledThreadPoolExecutor scheduledWorkers;

    // keep a moving average filter for the last 64 STATUS_INTERVALs
    private final MAF hashrateMAF;

    private final EventExecuteService ees;

    /** Miner threads */
    private final List<Thread> threads = new ArrayList<>();

    private final class EpMiner implements Runnable {
        boolean go = true;

        @Override
        public void run() {
            while (go) {
                IEvent e = ees.take();
                if (e.getEventType() == IHandler.TYPE.CONSENSUS.getValue()
                        && e.getCallbackType()
                                == EventConsensus.CALLBACK.ON_BLOCK_TEMPLATE.getValue()) {
                    EquihashMiner.this.onBlockTemplate((AionBlock) e.getFuncArgs().get(0));
                } else if (e.getEventType() == IHandler.TYPE.POISONPILL.getValue()) {
                    go = false;
                }
            }
        }
    }

    private static class Holder {
        static final EquihashMiner INSTANCE = new EquihashMiner();
    }

    /**
     * Singleton instance
     *
     * @return Equihash miner instance
     */
    public static EquihashMiner inst() {
        return Holder.INSTANCE;
    }

    /** Private constructor; called by singleton instance once */
    private EquihashMiner() {
        this.cfg = CfgAion.inst();

        IAionChain a0Chain = AionImpl.inst();

        cpuThreads = cfg.getConsensus().getCpuMineThreads();

        int n = CfgAion.getN();
        int k = CfgAion.getK();
        this.miner = new Equihash(n, k);

        scheduledWorkers = new ScheduledThreadPoolExecutor(1);
        hashrateMAF = new MAF(64);

        setCpuThreads(cfg.getConsensus().getCpuMineThreads());

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

    public void startMining() {
        if (!isMining) {
            isMining = true;
            fireMinerStarted();
            LOG.info("sealer starting 🔒 {" + cpuThreads + "}");

            scheduledWorkers.scheduleWithFixedDelay(
                    new ShowMiningStatusTask(),
                    STATUS_INTERVAL * 2,
                    STATUS_INTERVAL,
                    TimeUnit.SECONDS);

            for (int i = 0; i < cpuThreads; i++) {
                Thread t = new Thread(this::mine, "miner-" + (i + 1));

                t.start();
                LOG.info("sealer {} starting.", i + 1);
                threads.add(t);
            }
        }
    }

    public void stopMining() {
        if (isMining) {
            isMining = false;
            fireMinerStopped();
            LOG.info("sealer stopping 🔒");

            scheduledWorkers.shutdownNow();

            // interrupt
            int cnt = 0;
            for (Thread t : threads) {
                t.interrupt();
                LOG.info("Interrupt sealer {}", ++cnt);
            }

            // join
            cnt = 0;
            for (Thread t : threads) {
                try {
                    t.join();
                    LOG.info("Stopped sealer {}", ++cnt);
                } catch (InterruptedException e) {
                    LOG.error("Failed to stop sealer thread");
                }
            }
        }
    }

    /** Keeps mining until the thread is interrupted */
    private void mine() {
        AionBlock block;
        byte[] nonce;
        while (!Thread.currentThread().isInterrupted()) {
            if ((block = miningBlock) == null) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    break;
                }
            } else {

                // A new array must be created each loop
                // If reference is reused the array contents may be changed
                // before block sealed causing validation to fail
                nonce = new byte[32];
                ThreadLocalRandom.current().nextBytes(nonce);

                Solution s = miner.mine(block, nonce);
                if (s != null) {
                    IEvent ev = new EventConsensus(EventConsensus.CALLBACK.ON_SOLUTION);
                    ev.setFuncArgs(Collections.singletonList(s));
                    evtMgr.newEvent(ev);
                }
            }
        }
    }

    /** Restart the mining process when a new block template is received. */
    private void onBlockTemplate(AionBlock block) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("onBlockTemplate(): {}", toHexString(block.getHash()));
        }

        // Do not change reference if the event passes a null reference
        if (isMining() && block != null) {
            miningBlock = block;
        }
    }

    /**
     * Start block mining after sec seconds
     *
     * @param sec The number of seconds to wait until beginning to mine blocks
     */
    public void delayedStartMining(int sec) {
        if (cfg.getConsensus().getMining()) {
            LOG.info("<delayed-start-sealing>");
            Timer t = new Timer();
            t.schedule(
                    new TimerTask() {
                        @Override
                        public void run() {
                            if (cfg.getConsensus().getMining()) {
                                startMining();
                            }
                        }
                    },
                    sec * 1000);
        } else {
            LOG.info("<sealing-disabled>");
        }
    }

    /** This miner will listen to the ON_BLOCK_TEMPLATE event from the consensus handler. */
    private void registerCallback() {
        // Only register events if actual mining
        if (cfg.getConsensus().getMining()) {
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

    private void fireMinerStarted() {
        if (this.evtMgr != null) {
            this.evtMgr.newEvent(new EventMiner(EventMiner.CALLBACK.MININGSTARTED));
        }
    }

    private void fireMinerStopped() {
        if (this.evtMgr != null) {
            this.evtMgr.newEvent(new EventMiner(EventMiner.CALLBACK.MININGSTOPPED));
        }
    }

    private void setCpuThreads(int cpuThreads) {
        this.cpuThreads = cpuThreads;
    }

    public boolean isMining() {
        return isMining;
    }

    public double getHashrate() {
        return hashrateMAF.getAverage();
    }

    private class ShowMiningStatusTask implements Runnable {
        @Override
        public void run() {
            Thread.currentThread().setName("miner_status");
            double hashrate = (double) miner.totalSolGenerated.getAndSet(0) / STATUS_INTERVAL;
            hashrateMAF.add(hashrate);
            LOG.info("Aion internal miner generating {} solutions per second", hashrate);
        }
    }

    public void shutdown() {
        ees.shutdown();
    }
}
