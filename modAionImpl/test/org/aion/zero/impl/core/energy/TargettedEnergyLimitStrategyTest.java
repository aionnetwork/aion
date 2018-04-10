package org.aion.zero.impl.core.energy;

import org.aion.mcf.blockchain.valid.IValidRule;
import org.aion.zero.api.BlockConstants;
import org.aion.zero.impl.blockchain.ChainConfiguration;
import org.aion.zero.impl.core.energy.AbstractEnergyStrategyLimit;
import org.aion.zero.impl.core.energy.TargetStrategy;
import org.aion.zero.impl.valid.EnergyLimitRule;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

public class TargettedEnergyLimitStrategyTest {

    private static final long MINIMUM_ENERGY_LIMIT = 1_050_000L;

    private static final ChainConfiguration config = new ChainConfiguration();
    private static final EnergyLimitRule rule =
            new EnergyLimitRule(config.getConstants().getEnergyDivisorLimitLong(),
                                config.getConstants().getEnergyLowerBoundLong());

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

        List<IValidRule.RuleError> errors = new ArrayList<>();

        assertThat(rule.validate(header, parentHeader, errors)).isTrue();
        assertThat(errors).isEmpty();
    }

    BlockConstants constants = new BlockConstants();

    @Test
    public void testTargettedEnergyLimitLowerBound() {
        AbstractEnergyStrategyLimit strategy = new TargetStrategy(
                constants.getEnergyLowerBoundLong(),
                constants.getEnergyDivisorLimitLong(),
                10_000_000L);

        when(header.getEnergyLimit()).thenReturn(MINIMUM_ENERGY_LIMIT);
        long energyLimit = strategy.getEnergyLimit(header);
        validateHeaders(energyLimit, MINIMUM_ENERGY_LIMIT);
    }

    @Test
    public void testTargettedEnergyLimitEqual() {
        final long targetLimit = 10_000_000L;
        AbstractEnergyStrategyLimit strategy = new TargetStrategy(
                constants.getEnergyLowerBoundLong(),
                constants.getEnergyDivisorLimitLong(),
                10_000_000L);

        when(header.getEnergyLimit()).thenReturn(targetLimit);
        long energyLimit = strategy.getEnergyLimit(header);
        assertThat(energyLimit).isEqualTo(targetLimit);
    }

    @Test
    public void testTargettedEnergyLimitDeltaLowerBound() {
        final long parentEnergyLimit = 20_000_000L;

        AbstractEnergyStrategyLimit strategy = new TargetStrategy(
                constants.getEnergyLowerBoundLong(),
                constants.getEnergyDivisorLimitLong(),
                10_000_000L);

        when(header.getEnergyLimit()).thenReturn(parentEnergyLimit);

        long energyLimit = strategy.getEnergyLimit(header);

        validateHeaders(energyLimit, parentEnergyLimit);
        assertThat(energyLimit).isEqualTo(parentEnergyLimit - parentEnergyLimit / 1024L);
    }

    @Test
    public void testTargettedEnergyLimitDeltaUpperBound() {
        final long parentEnergyLimit = 5_000_000L;

        AbstractEnergyStrategyLimit strategy = new TargetStrategy(
                constants.getEnergyLowerBoundLong(),
                constants.getEnergyDivisorLimitLong(),
                10_000_000L);

        when(header.getEnergyLimit()).thenReturn(parentEnergyLimit);

        long energyLimit = strategy.getEnergyLimit(header);
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
        AbstractEnergyStrategyLimit strategy = new TargetStrategy(
                constants.getEnergyLowerBoundLong(),
                constants.getEnergyDivisorLimitLong(),
                10_000_000L);

        int cycle_counter = 0;
        for (int i = 0; i < 1000; i++) {
            long parentEnergyLimit = randLong((int) MINIMUM_ENERGY_LIMIT, 20000000);

            when(header.getEnergyLimit()).thenReturn(parentEnergyLimit);
            long energyLimit = strategy.getEnergyLimit(header);
            validateHeaders(energyLimit, parentEnergyLimit);

            if (i % 100 == 0) {
                System.out.println("completed 100 cycles: " + cycle_counter + " timestamp: " + System.currentTimeMillis());
                cycle_counter++;
            }
        }
    }

    // given the targetted energy limit strategy, should always converge to our
    // desired limit
    @Test
    public void testConvergence() {
        AbstractEnergyStrategyLimit strategy = new TargetStrategy(
                constants.getEnergyLowerBoundLong(),
                constants.getEnergyDivisorLimitLong(),
                10_000_000L);

        for (int k = 0; k < 5; k++) {
            long parentEnergy = randLong(5000, 20_000_000);
            for (int i = 0; i < 100_000; i++) {

                when(header.getEnergyLimit()).thenReturn(parentEnergy);
                parentEnergy = strategy.getEnergyLimit(header);
            }
            assertThat(parentEnergy).isEqualTo(10_000_000L);
        }
    }
}
