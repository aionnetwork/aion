package org.aion.zero.impl.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.mcf.core.ImportResult.CONSENSUS_BREAK;
import static org.aion.mcf.core.ImportResult.EXIST;
import static org.aion.mcf.core.ImportResult.IMPORTED_BEST;
import static org.aion.mcf.core.ImportResult.IMPORTED_NOT_BEST;
import static org.aion.mcf.core.ImportResult.INVALID_BLOCK;
import static org.aion.mcf.core.ImportResult.NO_PARENT;
import static org.aion.p2p.P2pConstant.COEFFICIENT_NORMAL_PEERS;
import static org.aion.p2p.P2pConstant.LARGE_REQUEST_SIZE;
import static org.aion.p2p.P2pConstant.MAX_NORMAL_PEERS;
import static org.aion.p2p.P2pConstant.MIN_NORMAL_PEERS;
import static org.aion.zero.impl.BlockchainTestUtils.generateAccounts;
import static org.aion.zero.impl.BlockchainTestUtils.generateNewBlock;
import static org.aion.zero.impl.BlockchainTestUtils.generateNextBlock;
import static org.aion.zero.impl.BlockchainTestUtils.generateRandomChain;
import static org.aion.zero.impl.sync.PeerState.Mode.BACKWARD;
import static org.aion.zero.impl.sync.PeerState.Mode.FORWARD;
import static org.aion.zero.impl.sync.PeerState.Mode.LIGHTNING;
import static org.aion.zero.impl.sync.PeerState.Mode.NORMAL;
import static org.aion.zero.impl.sync.PeerState.Mode.THUNDER;
import static org.aion.zero.impl.sync.TaskImportBlocks.attemptLightningJump;
import static org.aion.zero.impl.sync.TaskImportBlocks.filterBatch;
import static org.aion.zero.impl.sync.TaskImportBlocks.forwardModeUpdate;
import static org.aion.zero.impl.sync.TaskImportBlocks.isAlreadyStored;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.aion.base.db.IContractDetails;
import org.aion.base.db.IPruneConfig;
import org.aion.base.db.IRepositoryConfig;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.crypto.ECKey;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.mcf.core.ImportResult;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.db.ContractDetailsAion;
import org.aion.zero.impl.sync.PeerState.Mode;
import org.aion.zero.impl.types.AionBlock;
import org.junit.Test;
import org.junit.runner.RunWith;

/** @author Alexandra Roatis */
@RunWith(JUnitParamsRunner.class)
public class TaskImportBlocksTest {

    private final List<ECKey> accounts = generateAccounts(10);
    private final StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();

    /** @return parameters for {@link #testCountStates(long, long, Mode, Collection)} */
    @SuppressWarnings("unused")
    private Object parametersForTestCountStates() {
        List<Object> parameters = new ArrayList<>();

        PeerState state;
        List<PeerState> set1 = new ArrayList<>();
        for (Mode mode : Mode.values()) {
            state = new PeerState(mode, 10L);
            state.setLastBestBlock(100L);
            set1.add(state);
        }

        List<PeerState> set2 = new ArrayList<>(set1);
        for (Mode mode : Mode.values()) {
            state = new PeerState(mode, 10L);
            state.setLastBestBlock(200L);
            set2.add(state);
        }

        for (Mode mode : Mode.values()) {
            parameters.add(new Object[] {0L, -1L, mode, Collections.emptySet()});
            parameters.add(new Object[] {1L, 50L, mode, set1});
            parameters.add(new Object[] {0L, 100L, mode, set1});
            parameters.add(new Object[] {2L, 99L, mode, set2});
            parameters.add(new Object[] {1L, 100L, mode, set2});
            parameters.add(new Object[] {1L, 199L, mode, set2});
            parameters.add(new Object[] {0L, 200L, mode, set2});
            List<PeerState> set3 =
                    new ArrayList<>(set2)
                            .stream()
                            .filter(s -> s.getMode() != mode)
                            .collect(Collectors.toList());
            parameters.add(new Object[] {0L, -1L, mode, set3});
        }

        return parameters.toArray();
    }

    @Test
    @Parameters(method = "parametersForTestCountStates")
    public void testCountStates(long expected, long best, Mode mode, Collection<PeerState> set) {
        long actual = TaskImportBlocks.countStates(best, mode, set);
        assertThat(actual).isEqualTo(expected);
    }

    /** @return parameters for {@link #testSelectBase(long, long, SortedSet, SortedSet)} */
    @SuppressWarnings("unused")
    private Object parametersForTestSelectBase() {
        List<Object> parameters = new ArrayList<>();

        SortedSet<Long> emptySet = new TreeSet<>();
        parameters.add(new Object[] {100L, 100L, new TreeSet<Long>(), new TreeSet<Long>()});
        parameters.add(new Object[] {200L, 100L, new TreeSet<Long>(), new TreeSet<Long>()});

        SortedSet<Long> set1 = new TreeSet<>();
        set1.add(200L);
        parameters.add(new Object[] {200L, 100L, set1, new TreeSet<Long>()});

        SortedSet<Long> set2 = new TreeSet<>();
        set2.add(10L);
        set2.add(50L);
        set2.add(100L);
        set2.add(200L);
        set2.add(300L);
        SortedSet<Long> expectedSet = new TreeSet<>();
        expectedSet.add(300L);
        parameters.add(new Object[] {200L, 100L, set2, expectedSet});

        SortedSet<Long> set3 = new TreeSet<>();
        set3.addAll(set2);
        parameters.add(new Object[] {300L, 300L, set3, new TreeSet<Long>()});

        set3 = new TreeSet<>();
        set3.addAll(set2);
        parameters.add(new Object[] {400L, 300L, set3, new TreeSet<Long>()});

        SortedSet<Long> set4 = new TreeSet<>();
        set3.addAll(set2);
        parameters.add(new Object[] {310L, 310L, set4, new TreeSet<Long>()});

        set4 = new TreeSet<>();
        set3.addAll(set2);
        parameters.add(new Object[] {400L, 350L, set4, new TreeSet<Long>()});

        return parameters.toArray();
    }

    @Test
    @Parameters(method = "parametersForTestSelectBase")
    public void testSelectBase(
            long expected, long best, SortedSet<Long> set, SortedSet<Long> expectedSet) {
        AionBlockchainImpl chain = mock(AionBlockchainImpl.class);
        when(chain.nextBase(best, 0L)).thenReturn(expected);

        assertThat(TaskImportBlocks.selectBase(best, 0L, set, chain)).isEqualTo(expected);
        assertThat(set).isEqualTo(expectedSet);
    }

    @Test
    public void testIsAlreadyStored() {
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;

        // populate chain at random
        generateRandomChain(chain, 3, 1, accounts, 10);

        AionBlock current = chain.getBestBlock();
        while (current.getNumber() > 0) {
            // will pass both checks
            assertThat(isAlreadyStored(chain.getBlockStore(), current)).isTrue();
            current = chain.getBlockByHash(current.getParentHash());
        }

        // will fail the max number check
        current = generateNextBlock(chain, accounts, 10);
        assertThat(isAlreadyStored(chain.getBlockStore(), current)).isFalse();

        assertThat(chain.tryToConnect(current)).isEqualTo(ImportResult.IMPORTED_BEST);
        assertThat(isAlreadyStored(chain.getBlockStore(), current)).isTrue();

        // will fail the existence check
        current = generateNewBlock(chain, chain.getGenesis(), accounts, 10);
        assertThat(isAlreadyStored(chain.getBlockStore(), current)).isFalse();

        assertThat(chain.tryToConnect(current)).isEqualTo(IMPORTED_NOT_BEST);
        assertThat(isAlreadyStored(chain.getBlockStore(), current)).isTrue();
    }

    @Test
    public void testFilterBatch_woPruningRestrictions() {
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;

        // populate chain at random
        generateRandomChain(chain, 3, 1, accounts, 10);

        // populate initial input lists
        List<AionBlock> batch = new ArrayList<>();
        Map<ByteArrayWrapper, Object> imported = new HashMap<>();

        AionBlock current = chain.getBestBlock();
        while (current.getNumber() > 0) {
            batch.add(current);
            imported.put(ByteArrayWrapper.wrap(current.getHash()), true);
            current = chain.getBlockByHash(current.getParentHash());
        }
        batch.add(current);
        imported.put(ByteArrayWrapper.wrap(current.getHash()), true);

        // will filter out all blocks
        assertThat(filterBatch(batch, chain, imported)).isEmpty();

        // will filter out none of the blocks
        assertThat(filterBatch(batch, chain, new HashMap<>())).isEqualTo(batch);
    }

    @Test
    public void testFilterBatch_wPruningRestrictions() {
        int current_count = 5, height = 10;

        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple")
                        .withDefaultAccounts(accounts)
                        .withRepoConfig(
                                new IRepositoryConfig() {
                                    @Override
                                    public String getDbPath() {
                                        return "";
                                    }

                                    @Override
                                    public IPruneConfig getPruneConfig() {
                                        // top pruning without archiving
                                        return new IPruneConfig() {
                                            @Override
                                            public boolean isEnabled() {
                                                return true;
                                            }

                                            @Override
                                            public boolean isArchived() {
                                                return false;
                                            }

                                            @Override
                                            public int getCurrentCount() {
                                                return current_count;
                                            }

                                            @Override
                                            public int getArchiveRate() {
                                                return 0;
                                            }
                                        };
                                    }

                                    @Override
                                    public IContractDetails contractDetailsImpl() {
                                        return ContractDetailsAion.createForTesting(0, 1000000)
                                                .getDetails();
                                    }

                                    @Override
                                    public Properties getDatabaseConfig(String db_name) {
                                        Properties props = new Properties();
                                        props.setProperty(
                                                DatabaseFactory.Props.DB_TYPE,
                                                DBVendor.MOCKDB.toValue());
                                        props.setProperty(
                                                DatabaseFactory.Props.ENABLE_HEAP_CACHE, "false");
                                        return props;
                                    }
                                })
                        .build();

        StandaloneBlockchain chain = bundle.bc;

        // populate chain at random
        generateRandomChain(chain, height, 1, accounts, 10);

        // populate initial input lists
        List<AionBlock> allBlocks = new ArrayList<>();
        Map<ByteArrayWrapper, Object> allHashes = new HashMap<>();
        List<AionBlock> unrestrictedBlocks = new ArrayList<>();
        Map<ByteArrayWrapper, Object> unrestrictedHashes = new HashMap<>();

        for (long i = 0; i <= height; i++) {
            AionBlock current = chain.getBlockByNumber(i);
            allBlocks.add(current);
            allHashes.put(ByteArrayWrapper.wrap(current.getHash()), true);
            if (i >= height - current_count + 1) {
                unrestrictedBlocks.add(current);
                unrestrictedHashes.put(ByteArrayWrapper.wrap(current.getHash()), true);
            }
        }

        // will filter out all blocks
        assertThat(filterBatch(allBlocks, chain, allHashes)).isEmpty();

        // will filter out all blocks
        assertThat(filterBatch(allBlocks, chain, unrestrictedHashes)).isEmpty();

        // will filter out the prune restricted blocks
        assertThat(filterBatch(allBlocks, chain, new HashMap<>())).isEqualTo(unrestrictedBlocks);
    }

    @Test
    public void testAttemptLightningJump_wLightningState_wJump() {
        long returnedBase = 60L;
        long knownStatus = returnedBase + LARGE_REQUEST_SIZE + 1;

        AionBlockchainImpl chain = mock(AionBlockchainImpl.class);
        when(chain.nextBase(-1L, knownStatus)).thenReturn(returnedBase);

        PeerState input = new PeerState(LIGHTNING, -1L);
        input.setLastBestBlock(knownStatus);

        // with new jump base
        PeerState expected = new PeerState(input);
        expected.setBase(returnedBase);

        SortedSet<Long> baseSet = new TreeSet<>();
        assertThat(attemptLightningJump(-1L, input, null, baseSet, chain)).isEqualTo(expected);
        assertThat(baseSet.size()).isEqualTo(0);
    }

    @Test
    public void testAttemptLightningJump_wLightningState_wRampDown_andBaseRecycling() {
        long returnedBase = 60L;
        long knownStatus = returnedBase + LARGE_REQUEST_SIZE;

        AionBlockchainImpl chain = mock(AionBlockchainImpl.class);
        when(chain.nextBase(-1L, knownStatus)).thenReturn(returnedBase);

        PeerState input = new PeerState(LIGHTNING, -1L);
        input.setLastBestBlock(knownStatus);

        // with new jump base
        PeerState expected = new PeerState(input);
        expected.setMode(THUNDER);

        // expecting no change
        SortedSet<Long> baseSet = new TreeSet<>();
        assertThat(attemptLightningJump(-1L, input, null, baseSet, chain)).isEqualTo(expected);
        assertThat(baseSet.size()).isEqualTo(1);
        assertThat(baseSet.contains(returnedBase)).isTrue();
    }

    @Test
    public void testAttemptLightningJump_wLightningState_wRampDown() {
        long returnedBase = -1L;
        long knownStatus = returnedBase + LARGE_REQUEST_SIZE;

        AionBlockchainImpl chain = mock(AionBlockchainImpl.class);
        when(chain.nextBase(-1L, knownStatus)).thenReturn(returnedBase);

        PeerState input = new PeerState(LIGHTNING, -1L);
        input.setLastBestBlock(knownStatus);

        // with new jump base
        PeerState expected = new PeerState(input);
        expected.setMode(THUNDER);

        // expecting no change
        SortedSet<Long> baseSet = new TreeSet<>();
        assertThat(attemptLightningJump(-1L, input, null, baseSet, chain)).isEqualTo(expected);
        assertThat(baseSet.size()).isEqualTo(0);
    }

    /**
     * Used as parameters for:
     *
     * <ul>
     *   <li>{@link #testAttemptLightningJump_wMinNormalPeers(Mode)}
     *   <li>{@link #testAttemptLightningJump_wManyFastStates_wMaxNormalStates(Mode)}
     *   <li>{@link #testAttemptLightningJump_wFewFastStates_wJump(Mode)}
     *   <li>{@link #testAttemptLightningJump_wFewFastStates_wRampDown_andBaseRecycling(Mode)}
     *   <li>{@link #testAttemptLightningJump_wFewFastStates_wRampDown(Mode)}
     *   <li>{@link #testAttemptLightningJump_wManyNormalStates_wJump(Mode)}
     *   <li>{@link #testAttemptLightningJump_wManyNormalStates_wRampDown_andBaseRecycling(Mode)}
     *   <li>{@link #testAttemptLightningJump_wManyNormalStates_wRampDown(Mode)}
     * </ul>
     */
    @SuppressWarnings("unused")
    private Object allModesExceptLightning() {
        return new Object[] {NORMAL, THUNDER, BACKWARD, FORWARD};
    }

    @Test
    @Parameters(method = "allModesExceptLightning")
    public void testAttemptLightningJump_wMinNormalPeers(Mode mode) {
        // checking that the test assumptions are met
        assertThat(MIN_NORMAL_PEERS).isGreaterThan(0);

        // normalStates == MIN_NORMAL_PEERS
        List<PeerState> states = new ArrayList<>();
        addStates(states, MIN_NORMAL_PEERS, NORMAL, 100L);

        PeerState input = new PeerState(mode, 100L);
        PeerState expected = new PeerState(input);

        // expecting no change in the input value
        SortedSet<Long> baseSet = new TreeSet<>();
        assertThat(attemptLightningJump(-1L, input, states, baseSet, null)).isEqualTo(expected);
        assertThat(baseSet.size()).isEqualTo(0);
    }

    @Test
    @Parameters(method = "allModesExceptLightning")
    public void testAttemptLightningJump_wManyFastStates_wMaxNormalStates(Mode mode) {
        List<PeerState> states = new ArrayList<>();

        // exactly max normal states
        long normalStates = MAX_NORMAL_PEERS;
        addStates(states, normalStates, NORMAL, 100L);

        // more than balanced fast states
        long fastStates = COEFFICIENT_NORMAL_PEERS * normalStates;
        addStates(states, fastStates, LIGHTNING, 100L);

        PeerState input = new PeerState(mode, 100L);
        PeerState expected = new PeerState(input);

        // expecting no change in the input value
        SortedSet<Long> baseSet = new TreeSet<>();
        assertThat(attemptLightningJump(-1L, input, states, baseSet, null)).isEqualTo(expected);
        assertThat(baseSet.size()).isEqualTo(0);
    }

    @Test
    @Parameters(method = "allModesExceptLightning")
    public void testAttemptLightningJump_wFewFastStates_wJump(Mode mode) {
        long returnedBase = 60L;
        long knownStatus = returnedBase + LARGE_REQUEST_SIZE + 1;

        AionBlockchainImpl chain = mock(AionBlockchainImpl.class);
        when(chain.nextBase(-1L, knownStatus)).thenReturn(returnedBase);

        List<PeerState> states = new ArrayList<>();

        // exactly max normal states
        long normalStates = MAX_NORMAL_PEERS;
        addStates(states, normalStates, NORMAL, 100L);

        // less than balanced fast states
        long fastStates = COEFFICIENT_NORMAL_PEERS * normalStates - 1;
        addStates(states, fastStates, LIGHTNING, 100L);

        PeerState input = new PeerState(mode, 100L);
        input.setLastBestBlock(knownStatus);

        PeerState expected = new PeerState(input);
        expected.setMode(LIGHTNING);
        expected.setBase(returnedBase);

        // expecting correct jump state
        SortedSet<Long> baseSet = new TreeSet<>();
        assertThat(attemptLightningJump(-1L, input, states, baseSet, chain)).isEqualTo(expected);
        assertThat(baseSet.size()).isEqualTo(0);
    }

    @Test
    @Parameters(method = "allModesExceptLightning")
    public void testAttemptLightningJump_wFewFastStates_wRampDown_andBaseRecycling(Mode mode) {
        long returnedBase = 60L;
        long knownStatus = returnedBase + LARGE_REQUEST_SIZE;

        AionBlockchainImpl chain = mock(AionBlockchainImpl.class);
        when(chain.nextBase(-1L, knownStatus)).thenReturn(returnedBase);

        List<PeerState> states = new ArrayList<>();

        // exactly max normal states
        long normalStates = MAX_NORMAL_PEERS;
        addStates(states, normalStates, NORMAL, 100L);

        // less than balanced fast states
        long fastStates = COEFFICIENT_NORMAL_PEERS * normalStates - 1;
        addStates(states, fastStates, LIGHTNING, 100L);

        PeerState input = new PeerState(mode, 100L);
        input.setLastBestBlock(knownStatus);

        PeerState expected = new PeerState(input);

        // expecting no change
        SortedSet<Long> baseSet = new TreeSet<>();
        assertThat(attemptLightningJump(-1L, input, states, baseSet, chain)).isEqualTo(expected);
        assertThat(baseSet.size()).isEqualTo(1);
        assertThat(baseSet.contains(returnedBase)).isTrue();
    }

    @Test
    @Parameters(method = "allModesExceptLightning")
    public void testAttemptLightningJump_wFewFastStates_wRampDown(Mode mode) {
        long returnedBase = -1L;
        long knownStatus = returnedBase + LARGE_REQUEST_SIZE;

        AionBlockchainImpl chain = mock(AionBlockchainImpl.class);
        when(chain.nextBase(-1L, knownStatus)).thenReturn(returnedBase);

        List<PeerState> states = new ArrayList<>();

        // exactly max normal states
        long normalStates = MAX_NORMAL_PEERS;
        addStates(states, normalStates, NORMAL, 100L);

        // less than balanced fast states
        long fastStates = COEFFICIENT_NORMAL_PEERS * normalStates - 1;
        addStates(states, fastStates, LIGHTNING, 100L);

        PeerState input = new PeerState(mode, 100L);
        input.setLastBestBlock(knownStatus);

        PeerState expected = new PeerState(input);

        // expecting no change
        SortedSet<Long> baseSet = new TreeSet<>();
        assertThat(attemptLightningJump(-1L, input, states, baseSet, chain)).isEqualTo(expected);
        assertThat(baseSet.size()).isEqualTo(0);
    }

    @Test
    @Parameters(method = "allModesExceptLightning")
    public void testAttemptLightningJump_wManyNormalStates_wJump(Mode mode) {
        long returnedBase = 60L;
        long knownStatus = returnedBase + LARGE_REQUEST_SIZE + 1;

        AionBlockchainImpl chain = mock(AionBlockchainImpl.class);
        when(chain.nextBase(-1L, knownStatus)).thenReturn(returnedBase);

        List<PeerState> states = new ArrayList<>();

        // more than max normal states
        long normalStates = MAX_NORMAL_PEERS + 1;
        addStates(states, normalStates, NORMAL, 100L);

        // more than balanced fast states
        long fastStates = COEFFICIENT_NORMAL_PEERS * normalStates;
        addStates(states, fastStates, LIGHTNING, 100L);

        PeerState input = new PeerState(mode, 100L);
        input.setLastBestBlock(knownStatus);

        PeerState expected = new PeerState(input);
        expected.setMode(LIGHTNING);
        expected.setBase(returnedBase);

        // expecting correct jump state
        SortedSet<Long> baseSet = new TreeSet<>();
        assertThat(attemptLightningJump(-1L, input, states, baseSet, chain)).isEqualTo(expected);
        assertThat(baseSet.size()).isEqualTo(0);
    }

    @Test
    @Parameters(method = "allModesExceptLightning")
    public void testAttemptLightningJump_wManyNormalStates_wRampDown_andBaseRecycling(Mode mode) {
        long returnedBase = 60L;
        long knownStatus = returnedBase + LARGE_REQUEST_SIZE;

        AionBlockchainImpl chain = mock(AionBlockchainImpl.class);
        when(chain.nextBase(-1L, knownStatus)).thenReturn(returnedBase);

        List<PeerState> states = new ArrayList<>();

        // more than max normal states
        long normalStates = MAX_NORMAL_PEERS + 1;
        addStates(states, normalStates, NORMAL, 100L);

        // more than balanced fast states
        long fastStates = COEFFICIENT_NORMAL_PEERS * normalStates;
        addStates(states, fastStates, LIGHTNING, 100L);

        PeerState input = new PeerState(mode, 100L);
        input.setLastBestBlock(knownStatus);

        PeerState expected = new PeerState(input);

        // expecting no change
        SortedSet<Long> baseSet = new TreeSet<>();
        assertThat(attemptLightningJump(-1L, input, states, baseSet, chain)).isEqualTo(expected);
        assertThat(baseSet.size()).isEqualTo(1);
        assertThat(baseSet.contains(returnedBase)).isTrue();
    }

    @Test
    @Parameters(method = "allModesExceptLightning")
    public void testAttemptLightningJump_wManyNormalStates_wRampDown(Mode mode) {
        long returnedBase = -1L;
        long knownStatus = returnedBase + LARGE_REQUEST_SIZE;

        AionBlockchainImpl chain = mock(AionBlockchainImpl.class);
        when(chain.nextBase(-1L, knownStatus)).thenReturn(returnedBase);

        List<PeerState> states = new ArrayList<>();

        // more than max normal states
        long normalStates = MAX_NORMAL_PEERS + 1;
        addStates(states, normalStates, NORMAL, 100L);

        // more than balanced fast states
        long fastStates = COEFFICIENT_NORMAL_PEERS * normalStates;
        addStates(states, fastStates, LIGHTNING, 100L);

        PeerState input = new PeerState(mode, 100L);
        input.setLastBestBlock(knownStatus);

        PeerState expected = new PeerState(input);

        // expecting no change
        SortedSet<Long> baseSet = new TreeSet<>();
        assertThat(attemptLightningJump(-1L, input, states, baseSet, chain)).isEqualTo(expected);
        assertThat(baseSet.size()).isEqualTo(0);
    }

    /** Utility method that generates states and adds them to the given list. */
    private static void addStates(List<PeerState> states, long count, Mode mode, long base) {
        for (long i = 0; i < count; i++) {
            states.add(new PeerState(mode, base));
        }
    }

    /**
     * Used as parameters for:
     *
     * <ul>
     *   <li>{@link #testForwardModeUpdate(ImportResult)}
     * </ul>
     */
    @SuppressWarnings("unused")
    private Object allImportResultsExceptImportedBest() {
        return new Object[] {IMPORTED_NOT_BEST, EXIST, NO_PARENT, INVALID_BLOCK, CONSENSUS_BREAK};
    }

    @Test
    @Parameters(method = "allImportResultsExceptImportedBest")
    public void testForwardModeUpdate(ImportResult result) {
        long initialBase = 100L, newBase = 200L;
        PeerState input = new PeerState(FORWARD, initialBase);
        PeerState expected = new PeerState(FORWARD, newBase);

        // check when base gets updated
        assertThat(forwardModeUpdate(input, newBase, result)).isEqualTo(expected);

        input = new PeerState(FORWARD, initialBase);
        while (input.isUnderRepeatThreshold()) {
            input.incRepeated();
        }

        expected = new PeerState(NORMAL, initialBase);

        // check with switch to NORMAL mode
        assertThat(forwardModeUpdate(input, newBase, result)).isEqualTo(expected);
    }

    @Test
    public void testForwardModeUpdate() {
        long initialBase = 100L, newBase = 200L;
        PeerState input = new PeerState(FORWARD, initialBase);
        PeerState expected = new PeerState(NORMAL, initialBase);

        // check with switch to NORMAL mode due to IMPORTED_BEST result
        assertThat(forwardModeUpdate(input, newBase, IMPORTED_BEST)).isEqualTo(expected);
    }
}
