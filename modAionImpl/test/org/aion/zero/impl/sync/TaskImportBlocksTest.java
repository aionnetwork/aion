package org.aion.zero.impl.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.generateAccounts;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.generateNewBlock;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.generateNextBlock;
import static org.aion.zero.impl.blockchain.BlockchainTestUtils.generateRandomChain;
import static org.aion.zero.impl.core.ImportResult.IMPORTED_NOT_BEST;
import static org.aion.zero.impl.sync.TaskImportBlocks.filterBatch;
import static org.aion.zero.impl.sync.TaskImportBlocks.isAlreadyStored;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import junitparams.JUnitParamsRunner;
import org.aion.crypto.ECKey;
import org.aion.db.impl.DBVendor;
import org.aion.db.impl.DatabaseFactory;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.db.RepositoryConfig;
import org.aion.zero.impl.config.PruneConfig;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.blockchain.StandaloneBlockchain;
import org.aion.zero.impl.vm.AvmTestConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** @author Alexandra Roatis */
@RunWith(JUnitParamsRunner.class)
public class TaskImportBlocksTest {

    private final List<ECKey> accounts = generateAccounts(10);
    private final StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();

    @Before
    public void setup() {
        AvmTestConfig.supportOnlyAvmVersion1();
    }

    @After
    public void tearDown() {
        AvmTestConfig.clearConfigurations();
    }

    @Test
    public void testIsAlreadyStored() {
        StandaloneBlockchain.Bundle bundle =
                builder.withValidatorConfiguration("simple").withDefaultAccounts(accounts).build();

        StandaloneBlockchain chain = bundle.bc;

        // populate chain at random
        generateRandomChain(chain, 3, 1, accounts, 10);

        Block current = chain.getBestBlock();
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
        List<Block> batch = new ArrayList<>();
        Map<ByteArrayWrapper, Object> imported = new HashMap<>();

        Block current = chain.getBestBlock();
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
                                new RepositoryConfig() {
                                    @Override
                                    public String getDbPath() {
                                        return "";
                                    }

                                    @Override
                                    public PruneConfig getPruneConfig() {
                                        // top pruning without archiving
                                        return new PruneConfig() {
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
                                    public Properties getDatabaseConfig(String db_name) {
                                        Properties props = new Properties();
                                        props.setProperty(
                                                DatabaseFactory.Props.DB_TYPE,
                                                DBVendor.MOCKDB.toValue());
                                        return props;
                                    }
                                })
                        .build();

        StandaloneBlockchain chain = bundle.bc;

        // populate chain at random
        generateRandomChain(chain, height, 1, accounts, 10);

        // populate initial input lists
        List<Block> allBlocks = new ArrayList<>();
        Map<ByteArrayWrapper, Object> allHashes = new HashMap<>();
        List<Block> unrestrictedBlocks = new ArrayList<>();
        Map<ByteArrayWrapper, Object> unrestrictedHashes = new HashMap<>();

        for (long i = 0; i <= height; i++) {
            Block current = chain.getBlockByNumber(i);
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
}
