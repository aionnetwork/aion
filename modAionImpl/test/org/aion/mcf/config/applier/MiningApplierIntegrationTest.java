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
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.pow.AionPoW;
import org.aion.zero.types.AionTransaction;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;

import java.io.File;
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

/** Test {@link MiningApplier} interaction with {@link EquihashMiner} and {@link AionPoW} */
public class MiningApplierIntegrationTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder(new File("."));

    private StandaloneBlockchain aionBlockchain = new StandaloneBlockchain
            .Builder().withValidatorConfiguration("simple").build().bc;

    private static final Logger LOG;
    static {
        // Long running test, so print out some debug info
        AionLoggerFactory
                .init(new HashMap<>() {{
                          put(LogEnum.CONS.name(), LogLevels.DEBUG.name());
                          put("TEST", LogLevels.INFO.name());
                      }},
                        false /*logToFile*/,
                        "" /*logPath*/);
        LOG = AionLoggerFactory.getLogger("TEST");
    }

    /**
     * Test that {@link MiningApplier} can start, stop, then re-start {@link EquihashMiner} and
     * {@link AionPoW} workers and that doing so does not disrupt their ability to mine blocks
     * into a {@link IAionBlockchain}.
     */
    @Test
    public void testStartThenStopThenStartKernel() throws Throwable {
        // Set up test: start AionPoW and EquihashMiner with mining off
        LOG.info("Using temp db path {} [Absolute path: {}]",
                tmp.getRoot().getPath(), tmp.getRoot().getAbsolutePath());

        // In the ideal world, we would construct a Cfg/CfgAion and pass it to our objects
        // that we test, but since they're all looking at the CfgAion.inst() singleton,
        // we'll just set those values directly.  When we refactor our the references to
        // CfgAion.inst() in the classes we're testing, we should change this.
        CfgAion.inst().getConsensus().setMining(false);
        CfgAion.inst().getDb().setPath(tmp.getRoot().getPath());

        Properties prop = new Properties();
        prop.put(EventMgrModule.MODULENAME, "org.aion.evtmgr.impl.mgr.EventMgrA0");
        IEventMgr eventMgr = new EventMgrA0(prop);
        eventMgr.start();

        IPendingState<AionTransaction> pendingState = mock(IPendingState.class);

        AionPoW pow = new AionPoW(new CustomBlockchainAionImpl());
        pow.init(aionBlockchain, pendingState, eventMgr);

        JSONObject genesisJson = new JSONObject(GENESIS_BLOCK_JSON);
        AionGenesis genesis = GenesisBlockLoader.loadJSON(genesisJson);
        aionBlockchain.setBestBlock(genesis);
        aionBlockchain.setGenesis(genesis);

        EquihashMiner mineRunner = new EquihashMiner(eventMgr, CfgAion.inst());
        MiningApplier miningApplier = new MiningApplier(mineRunner, pow, aionBlockchain, pendingState, eventMgr);

        // Pre-test sanity check
        LOG.info("Initial state best block = {}", aionBlockchain.getBestBlock());
        mineRunner.delayedStartMining(5);

        assertThat(mineRunner.isMining(), is(false));
        assertThat(mineRunner.getHashrate(), is(0.));
        assertThat(aionBlockchain.getBestBlock().getNumber(), is(0l));

        // PART 1: Turn on mining and check that a new block is mined within 10 minutes
        LOG.info("Starting mining via MiningApplier and waiting for a block to be mined.");
        miningApplier.startOrResumeMining();
        TimeUnit.SECONDS.sleep(10); // due to delayedStartMining
        assertThat(mineRunner.isMining(), is(true));
        callFunctionUntilTrue(
                () -> {
                    LOG.info("Current best block = " + aionBlockchain.getBestBlock());
                    return aionBlockchain.getBestBlock().getNumber() > 0;
                },
                TimeUnit.SECONDS, 15, 600,
                "Expected at least one block to be mined after 10 minutes");
        final long bestBlock = aionBlockchain.getBestBlock().getNumber();
        LOG.info("New block has been mined.  New best block number is {}", bestBlock);

        // PART 2: Turn off mining and verify threads stopped
        LOG.info("Pausing mining via MiningApplier and verifying that threads have stopped.");
        miningApplier.pauseMining();
        TimeUnit.SECONDS.sleep(13);
        assertThat(mineRunner.isMining(), is(false));

        // hackily get all the thread names we care about and verify that they're not RUNNABLE
        List<Thread> minersAndPowThreads = Thread.getAllStackTraces().keySet().stream().filter(
                t -> t.getName().contains(EquihashMiner.MINER_THREAD_NAME_PREFIX)
                        || t.getName().contains(AionPoW.EPPOW_THREAD_NAME)
                        || t.getName().contains(AionPoW.POW_THREAD_NAME)
        ).collect(Collectors.toList());
        LOG.info("Found relevant threads: ");
        minersAndPowThreads.stream().forEach(
                t -> LOG.info("{} -> {}" ,t.getName(), t.getState())
        );
        minersAndPowThreads.stream().forEach(
                t -> assertThat(String.format("Thread '%s' should not be RUNNABLE", t.getName())
                        , t.getState(), is(not(Thread.State.RUNNABLE)))
        );

        // PART 3: Turn mining back on and check that a new block is mined within 20 minutes
        // 20 min. is probably overkill, but don't want Jenkins intermittently failing
        // this if it's running slow.
        LOG.info("Restarting mining via MiningApplier and waiting for a block to be mined.");
        miningApplier.startOrResumeMining();
        TimeUnit.SECONDS.sleep(10); // due to delayedStartMining
        assertThat(mineRunner.isMining(), is(true));
        callFunctionUntilTrue(
                () -> {
                    LOG.info("Current best block = {}", aionBlockchain.getBestBlock());
                    return aionBlockchain.getBestBlock().getNumber() > bestBlock;
                },
                TimeUnit.SECONDS, 15,1200,
                "Expected at least one block to be mined after 20 minutes");
        LOG.info("New block has been mined.  New best block number is " +
                aionBlockchain.getBestBlock().getNumber());
    }

    /**
     * Keep running boolean function until it returns true, at a specified period, until timeout is
     * reached.  Not intended for any test that needs strict timing.
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
                LOG.info(String.format("Condition met after waiting for %d %s",
                        timeUnit.convert(System.currentTimeMillis() - initial, TimeUnit.MILLISECONDS),
                        timeUnit));
                return;
            } else if (elapsed >= timeout) {
                fail(failMsg);
            } else {
                elapsed += period;
                LOG.info(String.format(
                        "sleeping %d %s while waiting for condition to be true (Timeout: %d %s. Waited %d %s so far)",
                        period, timeUnit, timeout, timeUnit, elapsed, timeUnit));
                timeUnit.sleep(period);
            }
        }
    }

    /**
     * AionImpl is hardcoded to use the IAionBlockchain constructed in AionHub,
     * this subclass overrides that so we can use the IAionBlockchain constructed
     * in this test.
     */
    private class CustomBlockchainAionImpl extends AionImpl {

        public CustomBlockchainAionImpl() {
            super();
        }

        @Override
        public IAionBlockchain getAionBlockchain() {
            return aionBlockchain;
        }
    }

    /** Genesis block with lowest possible difficulty */
    private static final String GENESIS_BLOCK_JSON = "{" +
            "  \"alloc\": {}," +
            "  \"networkBalanceAlloc\": {" +
            "    \"0\": {" +
            "      \"balance\": \"465934586660000000000000000\"" +
            "    }" +
            "  }," +
            "  \"energyLimit\": \"15000000\"," +
            "  \"nonce\": \"0x00\"," +
            "  \"difficulty\": \"0x1\"," +
            "  \"coinbase\": \"0x0000000000000000000000000000000000000000000000000000000000000000\"," +
            "  \"timestamp\": \"1524528000\"," +
            "  \"parentHash\": \"0x6a6d99a2ef14ab3b835dfc92fb918d76c37f6578a69825fbe19cd366485604b1\"," +
            "  \"chainId\": \"256\"" +
            "}";
}