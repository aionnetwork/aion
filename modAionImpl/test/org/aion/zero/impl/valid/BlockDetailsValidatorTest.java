package org.aion.zero.impl.valid;

import static org.aion.zero.impl.valid.BlockDetailsValidator.isValidBlock;
import static org.mockito.Mockito.when;

import com.google.common.truth.Truth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxExecSummary;
import org.aion.base.AionTxReceipt;
import org.aion.base.Bloom;
import org.aion.base.ConstantUtil;
import org.aion.base.Constants;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.log.LogLevel;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.zero.impl.forks.ForkUtility;
import org.aion.zero.impl.types.BlockUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

public class BlockDetailsValidatorTest {

    @Mock Block mockBlock;
    @Mock BlockHeader mockBlockHeader;
    @Mock ForkUtility mockForkUtility;
    @Mock AionTxExecSummary mockTxExecSummary;
    @Mock AionTxReceipt mockTxReceipt;
    @Mock AionTransaction mockTransaction;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        Map<LogEnum, LogLevel> logLevelMap = new HashMap<>();
        logLevelMap.put(LogEnum.ROOT, LogLevel.DEBUG);
        AionLoggerFactory.init(logLevelMap);
    }

    @Test
    public void validateEmptyBlockTest() {

        // Normal empty block case
        when(mockBlock.getNumber()).thenReturn(2L);
        when(mockBlock.getHeader()).thenReturn(mockBlockHeader);
        when(mockBlock.getReceiptsRoot()).thenReturn(ConstantUtil.EMPTY_TRIE_HASH);
        when(mockBlock.getLogBloom()).thenReturn(new byte[Bloom.SIZE]);
        when(mockBlockHeader.getEnergyConsumed()).thenReturn(0L);
        when(mockForkUtility.isNonceForkActive(2L)).thenReturn(true);

        Truth.assertThat(
            isValidBlock(
                mockBlock,
                Collections.emptyList(),
                Collections.emptyList(),
                mockForkUtility.isNonceForkActive(mockBlock.getNumber()),
                AionLoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)))
            .isTrue();

        // empty block with invalid receiptRoot
        when(mockBlock.getReceiptsRoot()).thenReturn(new byte[32]);

        Truth.assertThat(
            isValidBlock(
                mockBlock,
                Collections.emptyList(),
                Collections.emptyList(),
                mockForkUtility.isNonceForkActive(mockBlock.getNumber()),
                AionLoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)))
            .isFalse();

        // empty block with invalid energy
        when(mockBlock.getReceiptsRoot()).thenReturn(ConstantUtil.EMPTY_TRIE_HASH);
        when(mockBlockHeader.getEnergyConsumed()).thenReturn(1L);

        Truth.assertThat(
            isValidBlock(
                mockBlock,
                Collections.emptyList(),
                Collections.emptyList(),
                mockForkUtility.isNonceForkActive(mockBlock.getNumber()),
                AionLoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)))
            .isFalse();

        // empty block with invalid logBloom
        when(mockBlockHeader.getEnergyConsumed()).thenReturn(0L);
        byte[] bytes = new byte[Bloom.SIZE];
        Arrays.fill(bytes, (byte) 1);
        when(mockBlock.getLogBloom()).thenReturn(bytes);

        Truth.assertThat(
            isValidBlock(
                mockBlock,
                Collections.emptyList(),
                Collections.emptyList(),
                mockForkUtility.isNonceForkActive(mockBlock.getNumber()),
                AionLoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)))
            .isFalse();
    }

    @Test
    public void validateBlockHasRejectedTransaction() {
        byte[] receiptTrieEncoded = new byte[32];
        Arrays.fill(receiptTrieEncoded, (byte) 1);

        long hardFork = 2L;
        // The block has rejected transaction before the nonce hard fork
        when(mockBlock.getNumber()).thenReturn(hardFork - 1);
        when(mockBlock.getHeader()).thenReturn(mockBlockHeader);
        when(mockBlock.getLogBloom()).thenReturn(new byte[Bloom.SIZE]);
        when(mockBlockHeader.getEnergyConsumed())
                .thenReturn((long) Constants.NRG_TRANSACTION_DEFAULT);
        when(mockTxExecSummary.isRejected()).thenReturn(true);
        when(mockTxExecSummary.getReceipt()).thenReturn(mockTxReceipt);
        when(mockTxReceipt.getEnergyUsed()).thenReturn((long) Constants.NRG_TRANSACTION_DEFAULT);
        when(mockTxReceipt.getReceiptTrieEncoded()).thenReturn(receiptTrieEncoded);
        when(mockTxReceipt.getBloomFilter()).thenReturn(new Bloom());
        when(mockForkUtility.isNonceForkActive(hardFork - 1)).thenReturn(false);
        when(mockForkUtility.isNonceForkActive(hardFork)).thenReturn(true);

        List<AionTxExecSummary> summaryList = new ArrayList<>();
        summaryList.add(mockTxExecSummary);

        List<AionTxReceipt> receiptList = new ArrayList<>();
        receiptList.add(mockTxReceipt);
        byte[] calculatedTrieroot = BlockUtil.calcReceiptsTrie(receiptList);
        when(mockBlock.getReceiptsRoot()).thenReturn(calculatedTrieroot);

        Truth.assertThat(
            isValidBlock(
                mockBlock,
                summaryList,
                receiptList,
                mockForkUtility.isNonceForkActive(mockBlock.getNumber()),
                AionLoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)))
            .isTrue();

        // The block has rejected transaction after nonce hard fork active
        when(mockBlock.getNumber()).thenReturn(hardFork);
        Truth.assertThat(
            isValidBlock(
                mockBlock,
                summaryList,
                receiptList,
                mockForkUtility.isNonceForkActive(mockBlock.getNumber()),
                AionLoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)))
            .isFalse();
    }

    @Test
    public void validateBlockHasInvalidEnergyUsed() {
        byte[] receiptTrieEncoded = new byte[32];
        Arrays.fill(receiptTrieEncoded, (byte) 1);

        long hardFork = 2L;

        // The block has invalid total energy use in the block header
        when(mockBlock.getNumber()).thenReturn(hardFork);
        when(mockBlock.getHeader()).thenReturn(mockBlockHeader);
        when(mockBlock.getLogBloom()).thenReturn(new byte[Bloom.SIZE]);
        when(mockBlockHeader.getEnergyConsumed())
                .thenReturn((long) Constants.NRG_TRANSACTION_DEFAULT + 1);
        when(mockTxExecSummary.isRejected()).thenReturn(false);
        when(mockTxExecSummary.getReceipt()).thenReturn(mockTxReceipt);
        when(mockTxExecSummary.getTransaction()).thenReturn(mockTransaction);
        when(mockTxReceipt.getEnergyUsed())
                .thenReturn((long) Constants.NRG_TRANSACTION_DEFAULT);
        when(mockTxReceipt.getReceiptTrieEncoded()).thenReturn(receiptTrieEncoded);
        when(mockTxReceipt.getBloomFilter()).thenReturn(new Bloom());
        when(mockForkUtility.isNonceForkActive(hardFork)).thenReturn(true);

        List<AionTxExecSummary> summaryList = new ArrayList<>();
        summaryList.add(mockTxExecSummary);

        List<AionTxReceipt> receiptList = new ArrayList<>();
        receiptList.add(mockTxReceipt);
        byte[] calculatedTrieroot = BlockUtil.calcReceiptsTrie(receiptList);
        when(mockBlock.getReceiptsRoot()).thenReturn(calculatedTrieroot);

        Truth.assertThat(
            isValidBlock(
                mockBlock,
                summaryList,
                receiptList,
                mockForkUtility.isNonceForkActive(mockBlock.getNumber()),
                AionLoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)))
            .isFalse();
    }

    @Test
    public void validateBlockHasInvalidReceiptRoot() {
        byte[] receiptTrieEncoded = new byte[32];
        Arrays.fill(receiptTrieEncoded, (byte) 1);

        long hardFork = 2L;

        // The block has invalid receipt root in the block header
        when(mockBlock.getNumber()).thenReturn(hardFork);
        when(mockBlock.getHeader()).thenReturn(mockBlockHeader);
        when(mockBlock.getLogBloom()).thenReturn(new byte[Bloom.SIZE]);
        when(mockBlockHeader.getEnergyConsumed())
            .thenReturn((long) Constants.NRG_TRANSACTION_DEFAULT);
        when(mockTxExecSummary.isRejected()).thenReturn(false);
        when(mockTxExecSummary.getReceipt()).thenReturn(mockTxReceipt);
        when(mockTxExecSummary.getTransaction()).thenReturn(mockTransaction);
        when(mockTxReceipt.getEnergyUsed())
            .thenReturn((long) Constants.NRG_TRANSACTION_DEFAULT);
        when(mockTxReceipt.getReceiptTrieEncoded()).thenReturn(receiptTrieEncoded);
        when(mockTxReceipt.getBloomFilter()).thenReturn(new Bloom());
        when(mockTxReceipt.getTransaction()).thenReturn(mockTransaction);
        when(mockForkUtility.isNonceForkActive(hardFork)).thenReturn(true);

        List<AionTxExecSummary> summaryList = new ArrayList<>();
        summaryList.add(mockTxExecSummary);

        List<AionTxReceipt> receiptList = new ArrayList<>();
        receiptList.add(mockTxReceipt);
        when(mockBlock.getReceiptsRoot()).thenReturn(ConstantUtil.EMPTY_TRIE_HASH);

        Truth.assertThat(
            isValidBlock(
                mockBlock,
                summaryList,
                receiptList,
                mockForkUtility.isNonceForkActive(mockBlock.getNumber()),
                AionLoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)))
            .isFalse();
    }

    @Test
    public void validateBlockHasInvalidLogBloom() {
        byte[] receiptTrieEncoded = new byte[32];
        Arrays.fill(receiptTrieEncoded, (byte) 1);

        byte[] txBloom = new byte[Bloom.SIZE];
        Arrays.fill(txBloom, (byte) 1);

        long hardFork = 2L;

        // The block has invalid receipt root in the block header
        when(mockBlock.getNumber()).thenReturn(hardFork);
        when(mockBlock.getHeader()).thenReturn(mockBlockHeader);
        when(mockBlock.getLogBloom()).thenReturn(new byte[Bloom.SIZE]);
        when(mockBlockHeader.getEnergyConsumed())
            .thenReturn((long) Constants.NRG_TRANSACTION_DEFAULT);
        when(mockTxExecSummary.isRejected()).thenReturn(false);
        when(mockTxExecSummary.getReceipt()).thenReturn(mockTxReceipt);
        when(mockTxExecSummary.getTransaction()).thenReturn(mockTransaction);
        when(mockTxReceipt.getEnergyUsed())
            .thenReturn((long) Constants.NRG_TRANSACTION_DEFAULT);
        when(mockTxReceipt.getReceiptTrieEncoded()).thenReturn(receiptTrieEncoded);
        when(mockTxReceipt.getBloomFilter()).thenReturn(new Bloom(txBloom));
        when(mockTxReceipt.getTransaction()).thenReturn(mockTransaction);
        when(mockForkUtility.isNonceForkActive(hardFork)).thenReturn(true);

        List<AionTxExecSummary> summaryList = new ArrayList<>();
        summaryList.add(mockTxExecSummary);

        List<AionTxReceipt> receiptList = new ArrayList<>();
        receiptList.add(mockTxReceipt);
        byte[] calculatedTrieroot = BlockUtil.calcReceiptsTrie(receiptList);
        when(mockBlock.getReceiptsRoot()).thenReturn(calculatedTrieroot);

        Truth.assertThat(
            isValidBlock(
                mockBlock,
                summaryList,
                receiptList,
                mockForkUtility.isNonceForkActive(mockBlock.getNumber()),
                AionLoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)))
            .isFalse();
    }

    @Test
    public void isValidStateRootTest() {

        when(mockBlock.getStateRoot()).thenReturn(ConstantUtil.EMPTY_TRIE_HASH);
        when(mockBlock.getNumber()).thenReturn(1L);
        when(mockBlock.getEncoded()).thenReturn(new byte[32]);

        Truth.assertThat(
            BlockDetailsValidator.isValidStateRoot(
                mockBlock,
                ConstantUtil.EMPTY_TRIE_HASH,
                AionLoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)))
            .isTrue();

        byte[] worldTrieRoot = new byte[32];
        Arrays.fill(worldTrieRoot, (byte) 1);

        Truth.assertThat(
            BlockDetailsValidator.isValidStateRoot(
                mockBlock,
                worldTrieRoot,
                AionLoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)))
            .isFalse();
    }

    @Test
    public void isValidTxTrieRootTest() {

        List<AionTransaction> txList = new ArrayList<>();
        txList.add(mockTransaction);


        when(mockTransaction.getEncoded()).thenReturn(new byte[32]);
        byte[] calculatedTxTrieRoot = BlockUtil.calcTxTrieRoot(txList);

        when(mockBlock.getTxTrieRoot()).thenReturn(calculatedTxTrieRoot);
        when(mockBlock.getNumber()).thenReturn(1L);

        Truth.assertThat(
            BlockDetailsValidator.isValidTxTrieRoot(
                mockBlock.getTxTrieRoot(),
                txList,
                mockBlock.getNumber(),
                AionLoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)))
            .isTrue();

        when(mockBlock.getTxTrieRoot()).thenReturn(ConstantUtil.EMPTY_TRIE_HASH);
        Truth.assertThat(
            BlockDetailsValidator.isValidTxTrieRoot(
                mockBlock.getTxTrieRoot(),
                txList,
                mockBlock.getNumber(),
                AionLoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)))
            .isFalse();
    }
}
