package org.aion.zero.impl.valid;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.core.IDifficultyCalculator;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.types.A0BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link GrandParentDependantBlockHeaderRule}.
 *
 * @author Jay Tseng
 */
public class DifficultyRuleTest {
    private A0BlockHeader grandParentHeader;
    private A0BlockHeader parentHeader;
    private A0BlockHeader currentHeader;
    @Mock ChainConfiguration mockChainCfg;
    @Mock IDifficultyCalculator mockDiffCalculator;



    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        grandParentHeader =
            A0BlockHeader.Builder.newInstance()
                .withNumber(1)
                .withDefaultParentHash()
                .withDefaultCoinbase()
                .withDefaultLogsBloom()
                .withDifficulty(ByteUtil.intToBytes(1))
                .withDefaultExtraData()
                .withEnergyConsumed(1)
                .withEnergyLimit(1)
                .withTimestamp(1)
                .withDefaultNonce()
                .withDefaultSolution()
                .withDefaultStateRoot()
                .withDefaultTxTrieRoot()
                .withDefaultReceiptTrieRoot()
                .build();

        parentHeader =
            A0BlockHeader.Builder.newInstance()
                .withNumber(2)
                .withDefaultParentHash()
                .withDefaultCoinbase()
                .withDefaultLogsBloom()
                .withDifficulty(ByteUtil.intToBytes(1))
                .withDefaultExtraData()
                .withEnergyConsumed(1)
                .withEnergyLimit(1)
                .withTimestamp(11)
                .withDefaultNonce()
                .withDefaultSolution()
                .withDefaultStateRoot()
                .withDefaultTxTrieRoot()
                .withDefaultReceiptTrieRoot()
                .build();
    }

    /**
     * Checks if the {@link GrandParentDependantBlockHeaderRule#validate} returns {@code false} when the difficulty
     * data is <b>greater</b> than the difficulty data length.
     */
    @Test (expected = IllegalArgumentException.class)
    public void testInvalidDifficultyLength() {

        currentHeader =
            A0BlockHeader.Builder.newInstance(true)
                .withNumber(3)
                .withDefaultParentHash()
                .withDefaultCoinbase()
                .withDefaultLogsBloom()
                .withDifficulty(new byte[17])
                .withDefaultExtraData()
                .withEnergyConsumed(1)
                .withEnergyLimit(1)
                .withTimestamp(3)
                .withDefaultNonce()
                .withDefaultSolution()
                .withDefaultStateRoot()
                .withDefaultTxTrieRoot()
                .withDefaultReceiptTrieRoot()
                .build();

        List<RuleError> errors = new LinkedList<>();

        // generate output
        boolean actual =
                new AionDifficultyRule(mockChainCfg)
                        .validate(parentHeader, grandParentHeader, currentHeader, errors);

        // test output
        assertThat(actual).isFalse();
    }

    /**
     * Checks if the {@link GrandParentDependantBlockHeaderRule#validate} returns {@code true} when the difficulty
     * data is <b>equal</b> the difficulty data length.
     */
    @Test
    public void testDifficultyLength() {

        currentHeader =
            A0BlockHeader.Builder.newInstance()
                .withNumber(3)
                .withDefaultParentHash()
                .withDefaultCoinbase()
                .withDefaultLogsBloom()
                .withDifficulty(ByteUtil.bigIntegerToBytes(BigInteger.ONE, 16))
                .withDefaultExtraData()
                .withEnergyConsumed(1)
                .withEnergyLimit(1)
                .withTimestamp(3)
                .withDefaultNonce()
                .withDefaultSolution()
                .withDefaultStateRoot()
                .withDefaultTxTrieRoot()
                .withDefaultReceiptTrieRoot()
                .build();

        List<RuleError> errors = new LinkedList<>();

        when(mockChainCfg.getPreUnityDifficultyCalculator()).thenReturn(mockDiffCalculator);
        when(mockDiffCalculator.calculateDifficulty(parentHeader, grandParentHeader))
                .thenReturn(BigInteger.ONE);

        // generate output
        boolean actual =
                new AionDifficultyRule(mockChainCfg)
                        .validate(parentHeader, grandParentHeader, currentHeader, errors);

        // test output
        assertThat(actual).isTrue();
    }
}
