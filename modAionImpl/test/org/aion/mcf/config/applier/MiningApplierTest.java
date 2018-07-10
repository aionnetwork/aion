package org.aion.mcf.config.applier;

import com.google.common.io.ByteStreams;
import org.aion.base.util.Functional;
import org.aion.equihash.EquihashMiner;
import org.aion.evtmgr.EventMgrModule;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.impl.mgr.EventMgrA0;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevels;
import org.aion.mcf.blockchain.IPendingState;
import org.aion.mcf.config.CfgLog;
import org.aion.mcf.mine.IMineRunner;
import org.aion.mcf.vm.types.Log;
import org.aion.zero.impl.AionGenesis;
import org.aion.zero.impl.GenesisBlockLoader;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.blockchain.AionFactory;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.pow.AionPoW;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.IAionBlock;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class MiningApplierTest {

    @Before
    public void before() {
        AionLoggerFactory
                .init(Collections.singletonMap(LogEnum.CONS.name(), LogLevels.TRACE.name()),
                        false /*logToFile*/,
                        "" /*logPath*/);
    }

    @Test
    public void testPauseResumeKernel() throws Throwable {
        // -- test setup --

        // In the ideal world, we would construct a Cfg/CfgAion and pass it to our objects
        // that we test, but since they're all looking at the CfgAion.inst() singleton,
        // we'll just set those values directly.  When we refactor our the references to
        // CfgAion.inst() in the classes we're testing, we should change this.
        CfgAion.inst().getConsensus().setMining(false);
//        CfgAion.inst().getConsensus().setMining(true);


        Properties prop = new Properties();
        prop.put(EventMgrModule.MODULENAME, "org.aion.evtmgr.impl.mgr.EventMgrA0");
        IEventMgr eventMgr = new EventMgrA0(prop);
        eventMgr.start();

        IAionBlockchain aionBlockchain = StandaloneBlockchain.inst();
        IPendingState<AionTransaction> pendingState = mock(IPendingState.class);

        AionPoW pow = new AionPoW();
        pow.init(aionBlockchain, pendingState, eventMgr);

        JSONObject genesisJson = new JSONObject(genesisBlockJson);
        AionGenesis genesis = GenesisBlockLoader.loadJSON(genesisJson);
        aionBlockchain.setBestBlock(genesis);

        EquihashMiner mineRunner = new EquihashMiner(eventMgr);
        MiningApplier miningApplier = new MiningApplier();

        // Pre-test sanity check
        System.out.println(aionBlockchain.getBestBlock());
        mineRunner.delayedStartMining(10);

        assertThat(mineRunner.isMining(), is(false));
        assertThat(mineRunner.getHashrate(), is(0.));
        assertThat(aionBlockchain.getBestBlock().getNumber(), is(0l));

        // Set mining to on and check that after 2 minutes, a block has been mined
        miningApplier.startOrResumeMiningInternal(mineRunner, pow, aionBlockchain, pendingState, eventMgr);
        TimeUnit.SECONDS.sleep(10); // due to delayedStartMining
        assertThat(mineRunner.isMining(), is(true));


        for(;;) {
            TimeUnit.SECONDS.sleep(15);
            System.out.println(aionBlockchain.getBestBlock());
            if(aionBlockchain.getBestBlock().getNumber() != 0) break;
        }

        System.out.println("Best block after two minutes:");
        System.out.println(aionBlockchain.getBestBlock());




        /*
        callFunctionUntilTrue(
                () -> {
                    System.out.println(aionBlockchain.getBestBlock());
                    return aionBlockchain.getBestBlock().getNumber() > 0;
                },
                TimeUnit.SECONDS, 5, 120,
                "Expected at least one block to be mined after two minutes");
        */
    }

    /**
     * Keep running boolean function until it returns true, at a specified period, until timeout is reached.
     *
     * Not intended for strict time-keeping tests.
     */
    private final void callFunctionUntilTrue(BooleanSupplier function,
                                             TimeUnit timeUnit,
                                             int period,
                                             int timeout,
                                             String failMsg) throws Exception {
        long elapsed = 0;
        for(;;) {
            while(!function.getAsBoolean() && elapsed <= timeout)
            timeUnit.sleep(period);
            elapsed += period;

            boolean result = function.getAsBoolean();
            if(result) {
                return;
            } else if (elapsed >= timeout) {
                fail(failMsg);
            } else {
                System.out.println(String.format(
                        "sleeping %d %s while waiting for condition to be true (timeout: %d %s.  waited %d %s so far)",
                        period, timeUnit, timeout, timeUnit, elapsed, timeUnit));
            }
        }
    }

    private final String genesisBlockJson = "{" +
            "  \"alloc\": {}," +
            "  \"networkBalanceAlloc\": {" +
            "    \"0\": {" +
            "      \"balance\": \"465934586660000000000000000\"" +
            "    }" +
            "  }," +
            "  \"energyLimit\": \"15000000\"," +
            "  \"nonce\": \"0x00\"," +
            "  \"difficulty\": \"0x0\"," +
            "  \"coinbase\": \"0x0000000000000000000000000000000000000000000000000000000000000000\"," +
            "  \"timestamp\": \"1524528000\"," +
            "  \"parentHash\": \"0x6a6d99a2ef14ab3b835dfc92fb918d76c37f6578a69825fbe19cd366485604b1\"," +
            "  \"chainId\": \"256\"" +
            "}";
}