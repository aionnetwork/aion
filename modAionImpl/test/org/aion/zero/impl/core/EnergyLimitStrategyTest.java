package org.aion.zero.impl.core;

import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.valid.EnergyLimitRule;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Random;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

public class EnergyLimitStrategyTest {

    private static final ChainConfiguration config = new ChainConfiguration();
    private static final EnergyLimitRule rule = new EnergyLimitRule(config.getConstants().getEnergyDivisorLimit(),
                                                                    config.getConstants().getEnergyLowerBound());

    @Mock
    A0BlockHeader inputHeader;

    @Mock
    A0BlockHeader parentHeader;

    @Mock
    A0BlockHeader header;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    private void validateHeaders(long energy, long parentEnergy) {
        when(parentHeader.getEnergyLimit()).thenReturn(parentEnergy);
        when(header.getEnergyLimit()).thenReturn(energy);
        assertThat(rule.validate(header, parentHeader)).isTrue();
    }

    @Test
    public void testTargettedEnergyLimitLowerBound() {
        EnergyLimitStrategy strategy = new EnergyLimitStrategy();
        strategy.setConstants(config);
        long energyLimit = strategy.targetEnergyLimitStrategy(5000L);
        validateHeaders(energyLimit, 5000L);
    }

    @Test
    public void testTargettedEnergyLimitEqual() {
        final long targetLimit = 10000000L;

        EnergyLimitStrategy strategy = new EnergyLimitStrategy();
        strategy.setConstants(config);
        long energyLimit = strategy.targetEnergyLimitStrategy(targetLimit);
        validateHeaders(energyLimit, targetLimit);
        assertThat(energyLimit).isEqualTo(targetLimit);
    }

    @Test
    public void testTargettedEnergyLimitDeltaLowerBound() {
        final long parentEnergyLimit = 20000000L;

        EnergyLimitStrategy strategy = new EnergyLimitStrategy();
        strategy.setConstants(config);
        long energyLimit = strategy.targetEnergyLimitStrategy(parentEnergyLimit);

        validateHeaders(energyLimit, parentEnergyLimit);
        assertThat(energyLimit).isEqualTo(parentEnergyLimit - parentEnergyLimit / 1024L);
    }

    @Test
    public void testTargettedEnergyLimitDeltaUpperBound() {
        final long parentEnergyLimit = 5000000L;

        EnergyLimitStrategy strategy = new EnergyLimitStrategy();
        strategy.setConstants(config);
        long energyLimit = strategy.targetEnergyLimitStrategy(parentEnergyLimit);

        validateHeaders(energyLimit, parentEnergyLimit);
        assertThat(energyLimit).isEqualTo(parentEnergyLimit + parentEnergyLimit / 1024L);
    }

    private static final java.util.Random random = new Random();
    long randLong(int lower, int upper) {
        return lower + random.nextInt((upper - lower) + 1);
    }

    @Test
    public void fuzzTest() {
        System.out.println("generating random inputs and testing...");
        System.out.println("this may take a short while");
        EnergyLimitStrategy strategy = new EnergyLimitStrategy();
        strategy.setConstants(config);

        // 1 million iterations
        int cycle_counter = 0;
        for (int i = 0; i < 1000000; i++) {
            long parentEnergyLimit = randLong(5000, 20000000);
            long energyLimit = strategy.targetEnergyLimitStrategy(parentEnergyLimit);
            validateHeaders(energyLimit, parentEnergyLimit);

            if (i % 100000 == 0) {
                System.out.println("completed 100_000 cycles: " + cycle_counter + " timestamp: " + System.currentTimeMillis());
                cycle_counter++;
            }
        }
    }

    // given the targetted energy limit strategy, should always converge to our
    // desired limit
    @Test
    public void testConvergence() {
        EnergyLimitStrategy strategy = new EnergyLimitStrategy();

        for (int k = 0; k < 100; k++) {
            strategy.setConstants(config);
            long parentEnergy = randLong(5000, 20_000_000);
            for (int i = 0; i < 100_000; i++) {
                parentEnergy = strategy.targetEnergyLimitStrategy(parentEnergy);
            }
            assertThat(parentEnergy).isEqualTo(10_000_000L);
        }
    }
}
