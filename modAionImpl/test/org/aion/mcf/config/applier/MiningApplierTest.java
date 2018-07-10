package org.aion.mcf.config.applier;

import org.aion.equihash.EquihashMiner;
import org.aion.evtmgr.EventMgrModule;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.impl.mgr.EventMgrA0;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevels;
import org.aion.mcf.blockchain.IPendingState;
import org.aion.zero.impl.AionGenesis;
import org.aion.zero.impl.GenesisBlockLoader;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.pow.AionPoW;
import org.aion.zero.types.AionTransaction;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class MiningApplierTest {
    protected static Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.name());

    @Before
    public void before() {
        // Long running test, so print out some debug info
        AionLoggerFactory
                .init(new HashMap<>() {{
                          put(LogEnum.CONS.name(), LogLevels.DEBUG.name());
                          put(LogEnum.GEN.name(), LogLevels.INFO.name());
                }},
                        false /*logToFile*/,
                        "" /*logPath*/);
        LOG = AionLoggerFactory.getLogger(LogEnum.GEN.name());
    }

    @Test
    public void testStartThenStopThenStartKernel() throws Throwable {
        // Test setup

        // In the ideal world, we would construct a Cfg/CfgAion and pass it to our objects
        // that we test, but since they're all looking at the CfgAion.inst() singleton,
        // we'll just set those values directly.  When we refactor our the references to
        // CfgAion.inst() in the classes we're testing, we should change this.
        CfgAion.inst().getConsensus().setMining(false);

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
        System.out.println("Initial state best block = "+ aionBlockchain.getBestBlock());
        mineRunner.delayedStartMining(5);

        assertThat(mineRunner.isMining(), is(false));
        assertThat(mineRunner.getHashrate(), is(0.));
        assertThat(aionBlockchain.getBestBlock().getNumber(), is(0l));

        // Test 1: Turn on mining and check that a new block is mined within 5 minutes
        System.out.println("Starting mining via MiningApplier and waiting for a block to be mined.");
        miningApplier.startOrResumeMiningInternal(mineRunner, pow, aionBlockchain, pendingState, eventMgr);
        TimeUnit.SECONDS.sleep(10); // due to delayedStartMining
        assertThat(mineRunner.isMining(), is(true));
        callFunctionUntilTrue(
                () -> {
                    System.out.println("Current best block = " + aionBlockchain.getBestBlock());
                    return aionBlockchain.getBestBlock().getNumber() > 0;
                },
                TimeUnit.SECONDS, 15, 300,
                "Expected at least one block to be mined after 5 minutes");
        final long bestBlock = aionBlockchain.getBestBlock().getNumber();
        System.out.println("New block has been mined.  New best block number is " + bestBlock);

        // Test 2: Turn off mining and verify threads stopped
        System.out.println("Pausing mining via MiningApplier and verifying that threads have stopped.");
        miningApplier.pauseMiningInternal(mineRunner, pow);
        TimeUnit.SECONDS.sleep(15);
        assertThat(mineRunner.isMining(), is(false));

        // hackily get all the thread names we care about and verify that they're waiting
        List<Thread> minersAndPowThreads = Thread.getAllStackTraces().keySet().stream().filter(
                t -> t.getName().contains("miner") || t.getName().contains("pow") || t.getName().contains("EpPow")
        ).collect(Collectors.toList());
        System.out.println("Found relevant threads: ");
        minersAndPowThreads.stream().forEach(
                t -> System.out.println(String.format("%s -> %s" ,t.getName(), t.getState()))
        );
        minersAndPowThreads.stream().forEach(
                t -> assertThat(String.format("Thread '%s' should not be RUNNABLE", t.getName())
                        , t.getState(), is(not(Thread.State.RUNNABLE)))
        );

        // Test 3: Turn mining back on and check that a new block is mined within 5 minutes
        System.out.println("Restarting mining via MiningApplier and waiting for a block to be mined.");
        miningApplier.startOrResumeMiningInternal(mineRunner, pow, aionBlockchain, pendingState, eventMgr);
        TimeUnit.SECONDS.sleep(10); // due to delayedStartMining
        assertThat(mineRunner.isMining(), is(true));
        callFunctionUntilTrue(
                () -> {
                    System.out.println("Current best block = " + aionBlockchain.getBestBlock());
                    return aionBlockchain.getBestBlock().getNumber() > bestBlock;
                },
                TimeUnit.SECONDS, 15, 600 /* longer timeout since difficulty has increased */,
                "Expected at least one block to be mined after 10 minutes");
        System.out.println("New block has been mined.  New best block number is " +
                aionBlockchain.getBestBlock().getNumber());
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
        long initial = System.currentTimeMillis();
        long elapsed = 0;
        for(;;) {
            boolean result = function.getAsBoolean();
            if(result) {
                System.out.println(String.format("Condition met after waiting for %d %s",
                        timeUnit.convert(System.currentTimeMillis() - initial, TimeUnit.MILLISECONDS),
                        timeUnit));
                return;
            } else if (elapsed >= timeout) {
                fail(failMsg);
            } else {
                System.out.println(String.format(
                        "sleeping %d %s while waiting for condition to be true (timeout: %d %s.  waited %d %s so far)",
                        period, timeUnit, timeout, timeUnit, elapsed, timeUnit));
                timeUnit.sleep(period);
                elapsed += period;
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