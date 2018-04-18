package org.aion.zero.impl.core.energy;

import org.aion.mcf.blockchain.valid.IValidRule;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.valid.EnergyLimitRule;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.security.SecureRandom;
import java.util.*;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.booleanThat;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class GeneralEnergyLimitTests {

    private static final long energyLimitDivisor = 1024L;
    private static final long energyLowerBound = 1_050_000L;
    private static final long clampUpperBound = 15_000_000L;
    private static final long clampLowerBound = 7_000_000L;
    private static final long target = 10_000_000L;

    @Parameterized.Parameters(name="{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                        {EnergyStrategies.MONOTONIC.getLabel(), new MonotonicallyIncreasingStrategy(energyLowerBound, energyLimitDivisor)},
                        {EnergyStrategies.CLAMPED_DECAYING.getLabel(), new ClampedDecayStrategy(energyLowerBound, energyLimitDivisor, clampUpperBound, clampLowerBound)},
                        {EnergyStrategies.TARGETTED.getLabel(), new TargetStrategy(energyLowerBound, energyLimitDivisor, target)}
                });
    }

    AbstractEnergyStrategyLimit strategy;

    @Mock
    A0BlockHeader mockHeader;

    @Mock
    A0BlockHeader currHeader;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    public GeneralEnergyLimitTests(String name, AbstractEnergyStrategyLimit strategy) {
        this.strategy = strategy;
    }

    // this test implies that we may violate boundary rules given that the original
    // energy limit went below the bounds (this would violate the energy limit rules anyways)
    @Test
    public void testLowerBoundLimits() {
        final long parentEnergyLimit = 0L;
        final long parentEnergyConsumed = 0L;
        final long expected = energyLowerBound;

        when(mockHeader.getEnergyLimit()).thenReturn(parentEnergyLimit);
        when(mockHeader.getEnergyConsumed()).thenReturn(parentEnergyConsumed);

        long limit = strategy.getEnergyLimit(mockHeader);
        assertThat(limit).isEqualTo(expected);
    }

    static final ChainConfiguration config = new ChainConfiguration();
    static final EnergyLimitRule energyLimitRule = new EnergyLimitRule(
            config.getConstants().getEnergyDivisorLimitLong(),
            config.getConstants().getEnergyLowerBoundLong());

    private static final java.util.Random random = new Random(314159);
    long randLong(int lower, int upper) {
        return lower + random.nextInt((upper - lower) + 1);
    }

    // randomly generate a valid set of arguments for testing boundaries
    // pull in standard validator, fuzz parameters as input, test that we
    // abide by all rules
    @Test
    public void testFuzzyBoundsCheck() {
        final int iterations = 1000;
        for (int i = 0; i < iterations; i++) {
            long energyLimit = randLong((int) energyLowerBound, 20_000_000);
            long energyConsumed = randLong(0, (int) energyLimit);

            when(mockHeader.getEnergyLimit()).thenReturn(energyLimit);
            when(mockHeader.getEnergyConsumed()).thenReturn(energyConsumed);
            long newLimit = strategy.getEnergyLimit(mockHeader);

            when(currHeader.getEnergyLimit()).thenReturn(newLimit);

            List<IValidRule.RuleError> errors = new LinkedList<>();
            boolean success = energyLimitRule.validate(currHeader, mockHeader, errors);
            assertThat(success).isTrue();
            assertThat(errors).isEmpty();
        }
    }
}
