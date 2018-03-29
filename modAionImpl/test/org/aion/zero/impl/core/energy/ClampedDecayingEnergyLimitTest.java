package org.aion.zero.impl.core.energy;

import org.aion.zero.types.A0BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

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


    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    // we expect a decay of energyLimit / 1024 (presently)
    // the values are hard coded (on purpose)
    @Test
    public void testDecayFactor() {
        final long energyLimit = 10_000_000L;
        final long energyConsumed = 0L;
        final long expectedDrop = 10_000_000L / energyLimitDivisor;

        when(mockHeader.getEnergyLimit()).thenReturn(energyLimit);
        when(mockHeader.getEnergyConsumed()).thenReturn(energyConsumed);

        long limit = strategy.getEnergyLimit(mockHeader);
        assertThat(limit).isEqualTo(energyLimit - expectedDrop);
    }

    // we expect a breakevent point at 3/4 consumption
    @Test
    public void testDecayBreakEven() {
        final long energyLimit = 10_000_000L;
        final long energyConsumed = 7_500_000L;
        final long expectedDelta = 0L;

        when(mockHeader.getEnergyLimit()).thenReturn(energyLimit);
        when(mockHeader.getEnergyConsumed()).thenReturn(energyConsumed);

        long limit = strategy.getEnergyLimit(mockHeader);
        assertThat(limit).isEqualTo(energyLimit - expectedDelta);
    }

    // expect a maximum increase of 1/3 delta
    @Test
    public void testUpperBound() {
        final long energyLimit = 10_000_000L;
        final long energyConsumed = 10_000_000L;
        final long expectedDelta = energyLimit / 3 / energyLimitDivisor;

        when(mockHeader.getEnergyLimit()).thenReturn(energyLimit);
        when(mockHeader.getEnergyConsumed()).thenReturn(energyConsumed);

        long limit = strategy.getEnergyLimit(mockHeader);
        assertThat(limit).isEqualTo(energyLimit + expectedDelta);
    }
}
