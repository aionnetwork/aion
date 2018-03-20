/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.equihash;

import org.aion.base.type.*;
import org.aion.base.util.MAF;
import org.aion.evtmgr.IEvent;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.callback.EventCallbackA0;
import org.aion.evtmgr.impl.evt.EventConsensus;
import org.aion.evtmgr.impl.evt.EventMiner;
import org.aion.mcf.mine.AbstractMineRunner;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.IAionBlock;

import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.aion.base.util.Hex.toHexString;

/**
 * @author Ross Kitsis (ross@nuco.io)
 */

public class EquihashMiner extends AbstractMineRunner<AionBlock> {

    public final static String VERSION = "0.1.0";

    private IAionChain a0Chain;

    private CfgAion cfg;

    private IEventMgr evtMgr;

    private static EquihashMiner inst;

    // Equihash parameters
    private int n;
    private int k;

    // Equihash solver implementation
    private Equihash miner;

    // 15 second show status delay
    private static int STATUS_INTERVAL = 15;

    // Status scheduler
    private ScheduledThreadPoolExecutor scheduledWorkers;

    // keep a moving average filter for the last 64 STATUS_INTERVALs
    private MAF hashrateMAF;

    /**
     * Miner threads
     */
    private List<Thread> threads = new ArrayList<>();

    /**
     * Singleton instance
     *
     * @return Equihash miner instance
     */
    public static EquihashMiner inst() {
        if (inst == null) {
            inst = new EquihashMiner();
        }
        return inst;
    }

    /**
     * Private constructor; called by singleton instance once
     */
    private EquihashMiner() {
        this.cfg = CfgAion.inst();

        this.a0Chain = AionImpl.inst();

        cpuThreads = cfg.getConsensus().getCpuMineThreads();

        this.n = CfgAion.getN();
        this.k = CfgAion.getK();
        this.miner = new Equihash(n, k);

        scheduledWorkers = new ScheduledThreadPoolExecutor(1);
        hashrateMAF = new MAF(64);

        setCpuThreads(cfg.getConsensus().getCpuMineThreads());

        this.evtMgr = this.a0Chain.getAionHub().getEventMgr();
        registerMinerEvents();

        registerCallback();
    }

    @Override
    public void startMining() {
        if (!isMining) {
            isMining = true;
            fireMinerStarted();
            LOG.info("sealer starting 🔒 {" + cpuThreads + "}");

            scheduledWorkers.scheduleWithFixedDelay(new ShowMiningStatusTask(), STATUS_INTERVAL * 2, STATUS_INTERVAL,
                    TimeUnit.SECONDS);

            for (int i = 0; i < cpuThreads; i++) {
                Thread t = new Thread(this::mine, "miner-" + (i + 1));

                t.start();
                LOG.info("sealer {} starting.", i + 1);
                threads.add(t);
            }
        }
    }

    @Override
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

    /**
     * Keeps mining until the thread is interrupted
     */
    protected void mine() {
        IAionBlock block;
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

    /**
     * Restart the mining process when a new block template is received.
     */
    protected void onBlockTemplate(AionBlock block) {
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
     * @param sec
     *            The number of seconds to wait until beginning to mine blocks
     */
    @Override
    public void delayedStartMining(int sec) {
        if (cfg.getConsensus().getMining()) {
            LOG.info("<delayed-start-sealing>");
            Timer t = new Timer();
            t.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (cfg.getConsensus().getMining()) {
                        startMining();
                    }
                }
            }, sec * 1000);
        } else {
            LOG.info("<sealing-disabled>");
        }
    }

    /**
     * This miner will listen to the ON_BLOCK_TEMPLATE event from the consensus
     * handler.
     */
    public void registerCallback() {
        // Only register events if actual mining
        if (cfg.getConsensus().getMining()) {
            if (this.evtMgr != null) {
                IHandler hdrCons = this.evtMgr.getHandler(4);
                if (hdrCons != null) {
                    hdrCons.eventCallback(
                            new EventCallbackA0<IBlock, ITransaction, ITxReceipt, IBlockSummary, ITxExecSummary, ISolution>() {
                                public void onBlockTemplate(IBlock block) {
                                    EquihashMiner.this.onBlockTemplate((AionBlock) block);
                                }
                            });
                }
            } else {
                LOG.error("event manager is null");
            }
        }
    }

    /**
     * Register miner events.
     */
    public void registerMinerEvents() {
        List<IEvent> evts = new ArrayList<>();
        evts.add(new EventMiner(EventMiner.CALLBACK.MININGSTARTED));
        evts.add(new EventMiner(EventMiner.CALLBACK.MININGSTOPPED));
        this.evtMgr.registerEvent(evts);
    }

    @Override
    protected void fireMinerStarted() {
        if (this.evtMgr != null) {
            this.evtMgr.newEvent(new EventMiner(EventMiner.CALLBACK.MININGSTARTED));
        }
    }

    @Override
    protected void fireMinerStopped() {
        if (this.evtMgr != null) {
            this.evtMgr.newEvent(new EventMiner(EventMiner.CALLBACK.MININGSTOPPED));
        }
    }

    @Override
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
}
