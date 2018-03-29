package org.aion.zero.impl.core.energy;

import org.aion.zero.types.A0BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class ClampedDecayingEnergyLimitTest {

    @Mock
    A0BlockHeader mockHeader;

    private static final long energyLimitDivisor = 1024L;
    private static final long energyLowerBound = 5000L;
    private static final long clampUpperBound = 15_000_000L;
    private static final long clampLowerBound = 7_000_000L;

    private final ClampedDecayStrategy strategy = new ClampedDecayStrategy(
            energyLowerBound,
            energyLimitDivisor,
            clampUpperBound,
            clampLowerBound);

    private final long parentEnergyLimit;
    private final long parentEnergyConsumed;
    private final long expectedDelta;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {10_000_000L, 0L, - (10_000_000L / energyLimitDivisor)},
                {10_000_000L, 7_500_000L, 0L},
                {10_000_000L, 10_000_000L, 10_000_000L / 3 / energyLimitDivisor},
                {6_000_000L, 0L, 6_000_000L / energyLimitDivisor},
                {16_000_000L, 16_000_000L, - 16_000_000L / energyLimitDivisor}
        });
    }

    public ClampedDecayingEnergyLimitTest(final long parentEnergyLimit,
                                          final long parentEnergyConsumed,
                                          final long expectedDelta) {
        this.parentEnergyLimit = parentEnergyLimit;
        this.parentEnergyConsumed = parentEnergyConsumed;
        this.expectedDelta = expectedDelta;
    }

    private void assertLimits(long energyLimit, long energyConsumed, long expected) {
        when(mockHeader.getEnergyLimit()).thenReturn(energyLimit);
        when(mockHeader.getEnergyConsumed()).thenReturn(energyConsumed);

        long limit = strategy.getEnergyLimit(mockHeader);
        assertThat(limit).isEqualTo(energyLimit + expected);
    }

    @Test
    public void test() {
        assertLimits(this.parentEnergyLimit, this.parentEnergyConsumed, this.expectedDelta);
    }
}
