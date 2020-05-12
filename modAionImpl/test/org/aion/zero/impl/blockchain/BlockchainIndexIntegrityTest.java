package org.aion.zero.impl.blockchain;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.db.AionBlockStore.BLOCK_INFO_SERIALIZER;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.db.store.ArrayStore;
import org.aion.db.store.Stores;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.zero.impl.types.Block;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.db.AionBlockStore.BlockInfo;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.MiningBlockHeader;
import org.aion.zero.impl.types.MiningBlock;
import org.junit.BeforeClass;
import org.junit.Test;

/** @author Alexandra Roatis */
public class BlockchainIndexIntegrityTest {

    @BeforeClass
    public static void setup() {
        // logging to see errors
        AionLoggerFactory.initAll(Map.of(LogEnum.CONS, LogLevel.INFO));
    }

    /**
     * Test the index integrity check and recovery when the index database is missing the genesis
     * block information.
     *
     * <p>Under these circumstances the recovery process will fail.
     */
    @Test
    public void testIndexIntegrityWithoutGenesis() {
        final int NUMBER_OF_BLOCKS = 3;

        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;

        ImportResult result;
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            Block next =
                    chain.createNewMiningBlock(chain.getBestBlock(), Collections.emptyList(), true);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        }

        Block bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        chain.getRepository().flush();

        AionRepositoryImpl repo = chain.getRepository();
        MockDB indexDatabase = (MockDB) repo.getIndexDatabase();

        // deleting the genesis index
        indexDatabase.deleteAndCommit(ByteUtil.intToBytes(0));

        AionBlockStore blockStore = repo.getBlockStore();

        // check that the index recovery failed
        assertThat(blockStore.indexIntegrityCheck())
                .isEqualTo(AionBlockStore.IntegrityCheckResult.MISSING_GENESIS);
    }

    /**
     * Test the index integrity check and recovery when the index database is missing a level
     * information.
     *
     * <p>Under these circumstances the recovery process will fail.
     */
    @Test
    public void testIndexIntegrityWithoutLevel() {
        final int NUMBER_OF_BLOCKS = 5;

        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;

        ImportResult result;
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            Block next =
                    chain.createNewMiningBlock(chain.getBestBlock(), Collections.emptyList(), true);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        }

        Block bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        chain.getRepository().flush();

        AionRepositoryImpl repo = chain.getRepository();
        MockDB indexDatabase = (MockDB) repo.getIndexDatabase();

        // deleting the level 2 index
        indexDatabase.deleteAndCommit(ByteUtil.intToBytes(2));

        AionBlockStore blockStore = repo.getBlockStore();

        // check that the index recovery failed
        assertThat(blockStore.indexIntegrityCheck())
                .isEqualTo(AionBlockStore.IntegrityCheckResult.MISSING_LEVEL);
    }

    /** Test the index integrity check and recovery when the index database is incorrect. */
    @Test
    public void testIndexIntegrityWithRecovery() {
        final int NUMBER_OF_BLOCKS = 5;

        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;

        Block bestBlock;
        ImportResult result;
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            bestBlock = chain.getBestBlock();
            MiningBlock next = chain.createNewMiningBlock(bestBlock, Collections.emptyList(), true);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

            // adding side chain
            next = chain.createNewMiningBlock(bestBlock, Collections.emptyList(), true);
            MiningBlockHeader newBlockHeader =
                    MiningBlockHeader.Builder.newInstance()
                            .withHeader(next.getHeader())
                            .withExtraData("other".getBytes())
                            .build();
            next.updateHeader(newBlockHeader);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_NOT_BEST);
        }

        bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        chain.getRepository().flush();

        AionRepositoryImpl repo = chain.getRepository();
        ByteArrayKeyValueDatabase indexDatabase = repo.getIndexDatabase();

        // corrupting the index at level 2
        ArrayStore<List<BlockInfo>> index = Stores.newArrayStore(indexDatabase, BLOCK_INFO_SERIALIZER);
        List<BlockInfo> infos = index.get(2);
        assertThat(infos.size()).isEqualTo(2);

        for (BlockInfo bi : infos) {
            bi.setTotalDifficulty(bi.getTotalDifficulty().add(BigInteger.TEN));
        }
        index.set(2, infos);

        AionBlockStore blockStore = repo.getBlockStore();

        // check that the index recovery succeeded
        assertThat(blockStore.indexIntegrityCheck())
                .isEqualTo(AionBlockStore.IntegrityCheckResult.FIXED);
    }

    /** Test the index integrity check and recovery when the index database is correct. */
    @Test
    public void testIndexIntegrityWithCorrectData() {
        final int NUMBER_OF_BLOCKS = 5;

        // build a blockchain with a few blocks
        StandaloneBlockchain.Builder builder = new StandaloneBlockchain.Builder();
        StandaloneBlockchain.Bundle bundle = builder.withValidatorConfiguration("simple").build();

        StandaloneBlockchain chain = bundle.bc;

        Block bestBlock;
        ImportResult result;
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            bestBlock = chain.getBestBlock();
            MiningBlock next = chain.createNewMiningBlock(bestBlock, Collections.emptyList(), true);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

            // adding side chain
            next = chain.createNewMiningBlock(bestBlock, Collections.emptyList(), true);
            MiningBlockHeader newBlockHeader =
                    MiningBlockHeader.Builder.newInstance()
                            .withHeader(next.getHeader())
                            .withExtraData("other".getBytes())
                            .build();
            next.updateHeader(newBlockHeader);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_NOT_BEST);
        }

        bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        chain.getRepository().flush();

        AionRepositoryImpl repo = chain.getRepository();
        AionBlockStore blockStore = repo.getBlockStore();

        // check that the index recovery succeeded
        assertThat(blockStore.indexIntegrityCheck())
                .isEqualTo(AionBlockStore.IntegrityCheckResult.CORRECT);
    }
}
