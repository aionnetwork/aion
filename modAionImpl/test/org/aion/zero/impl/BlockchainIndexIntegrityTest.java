package org.aion.zero.impl;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.db.AionBlockStore.BLOCK_INFO_SERIALIZER;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.base.db.IByteArrayKeyValueDatabase;
import org.aion.base.util.ByteUtil;
import org.aion.log.AionLoggerFactory;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.ds.DataSourceArray;
import org.aion.mcf.ds.ObjectDataSource;
import org.aion.zero.impl.db.AionBlockStore;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.junit.BeforeClass;
import org.junit.Test;

/** @author Alexandra Roatis */
public class BlockchainIndexIntegrityTest {

    @BeforeClass
    public static void setup() {
        // logging to see errors
        Map<String, String> cfg = new HashMap<>();
        cfg.put("CONS", "INFO");

        AionLoggerFactory.init(cfg);
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
            AionBlock next =
                    chain.createNewBlock(chain.getBestBlock(), Collections.emptyList(), true);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        chain.getRepository().flush();

        AionRepositoryImpl repo = (AionRepositoryImpl) chain.getRepository();
        IByteArrayKeyValueDatabase indexDatabase = repo.getIndexDatabase();

        // deleting the genesis index
        indexDatabase.delete(ByteUtil.intToBytes(0));

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
            AionBlock next =
                    chain.createNewBlock(chain.getBestBlock(), Collections.emptyList(), true);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        }

        AionBlock bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        chain.getRepository().flush();

        AionRepositoryImpl repo = (AionRepositoryImpl) chain.getRepository();
        IByteArrayKeyValueDatabase indexDatabase = repo.getIndexDatabase();

        // deleting the level 2 index
        indexDatabase.delete(ByteUtil.intToBytes(2));

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

        AionBlock bestBlock;
        ImportResult result;
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            bestBlock = chain.getBestBlock();
            AionBlock next = chain.createNewBlock(bestBlock, Collections.emptyList(), true);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

            // adding side chain
            next = chain.createNewBlock(bestBlock, Collections.emptyList(), true);
            next.setExtraData("other".getBytes());
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_NOT_BEST);
        }

        bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        chain.getRepository().flush();

        AionRepositoryImpl repo = (AionRepositoryImpl) chain.getRepository();
        IByteArrayKeyValueDatabase indexDatabase = repo.getIndexDatabase();

        // corrupting the index at level 2
        DataSourceArray<List<AionBlockStore.BlockInfo>> index =
                new DataSourceArray<>(new ObjectDataSource<>(indexDatabase, BLOCK_INFO_SERIALIZER));
        List<AionBlockStore.BlockInfo> infos = index.get(2);
        assertThat(infos.size()).isEqualTo(2);

        for (AionBlockStore.BlockInfo bi : infos) {
            bi.setCummDifficulty(bi.getCummDifficulty().add(BigInteger.TEN));
        }
        index.set(2, infos);
        index.flush();

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

        AionBlock bestBlock;
        ImportResult result;
        for (int i = 0; i < NUMBER_OF_BLOCKS; i++) {
            bestBlock = chain.getBestBlock();
            AionBlock next = chain.createNewBlock(bestBlock, Collections.emptyList(), true);
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

            // adding side chain
            next = chain.createNewBlock(bestBlock, Collections.emptyList(), true);
            next.setExtraData("other".getBytes());
            result = chain.tryToConnect(next);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_NOT_BEST);
        }

        bestBlock = chain.getBestBlock();
        assertThat(bestBlock.getNumber()).isEqualTo(NUMBER_OF_BLOCKS);

        chain.getRepository().flush();

        AionRepositoryImpl repo = (AionRepositoryImpl) chain.getRepository();
        AionBlockStore blockStore = repo.getBlockStore();

        // check that the index recovery succeeded
        assertThat(blockStore.indexIntegrityCheck())
                .isEqualTo(AionBlockStore.IntegrityCheckResult.CORRECT);
    }
}
