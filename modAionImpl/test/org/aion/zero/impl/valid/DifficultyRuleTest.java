package org.aion.zero.impl.valid;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.types.A0BlockHeader.NONCE_LENGTH;
import static org.aion.zero.impl.types.A0BlockHeader.SOLUTIONSIZE;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.core.IDifficultyCalculator;
import org.aion.util.types.AddressUtils;
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
                .withParentHash(new byte[32])
                .withCoinbase(AddressUtils.ZERO_ADDRESS)
                .withLogsBloom(new byte[256])
                .withDifficulty(ByteUtil.intToBytes(1))
                .withExtraData(new byte[32])
                .withEnergyConsumed(1)
                .withEnergyLimit(1)
                .withTimestamp(1)
                .withNonce(new byte[NONCE_LENGTH])
                .withSolution(new byte[SOLUTIONSIZE])
                .build();

        parentHeader =
            A0BlockHeader.Builder.newInstance()
                .withNumber(2)
                .withParentHash(new byte[32])
                .withCoinbase(AddressUtils.ZERO_ADDRESS)
                .withLogsBloom(new byte[256])
                .withDifficulty(ByteUtil.intToBytes(1))
                .withExtraData(new byte[32])
                .withEnergyConsumed(1)
                .withEnergyLimit(1)
                .withTimestamp(11)
                .withNonce(new byte[NONCE_LENGTH])
                .withSolution(new byte[SOLUTIONSIZE])
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
                .withParentHash(new byte[32])
                .withCoinbase(AddressUtils.ZERO_ADDRESS)
                .withLogsBloom(new byte[256])
                .withDifficulty(new byte[17])
                .withExtraData(new byte[32])
                .withEnergyConsumed(1)
                .withEnergyLimit(1)
                .withTimestamp(3)
                .withNonce(new byte[NONCE_LENGTH])
                .withSolution(new byte[SOLUTIONSIZE])
                .build();

        List<RuleError> errors = new LinkedList<>();

        // generate output
        boolean actual =
                new AionDifficultyRule(mockChainCfg)
                        .validate(grandParentHeader, parentHeader, currentHeader, errors);

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
                .withParentHash(new byte[32])
                .withCoinbase(AddressUtils.ZERO_ADDRESS)
                .withLogsBloom(new byte[256])
                .withDifficulty(ByteUtil.bigIntegerToBytes(BigInteger.ONE, 16))
                .withExtraData(new byte[32])
                .withEnergyConsumed(1)
                .withEnergyLimit(1)
                .withTimestamp(3)
                .withNonce(new byte[NONCE_LENGTH])
                .withSolution(new byte[SOLUTIONSIZE])
                .build();

        List<RuleError> errors = new LinkedList<>();

        when(mockChainCfg.getDifficultyCalculator()).thenReturn(mockDiffCalculator);
        when(mockDiffCalculator.calculateDifficulty(parentHeader, grandParentHeader))
                .thenReturn(BigInteger.ONE);

        // generate output
        boolean actual =
                new AionDifficultyRule(mockChainCfg)
                        .validate(grandParentHeader, parentHeader, currentHeader, errors);

        // test output
        assertThat(actual).isTrue();
    }
}
