package org.aion.zero.impl.valid;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EnergyConsumedRuleTest {

    @Mock A0BlockHeader mockHeader;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    // ensure that energyConsumed does not exceed energyLimit bound
    @Test
    public void testEnergyLimitBounds() {
        final long upperBound = 1000000L;

        when(mockHeader.getEnergyConsumed()).thenReturn(0L);
        when(mockHeader.getEnergyLimit()).thenReturn(upperBound);

        EnergyConsumedRule rule = new EnergyConsumedRule();
        List<IValidRule.RuleError> errors = new ArrayList<>();

        boolean ret = rule.validate(mockHeader, errors);
        assertThat(ret).isTrue();
        assertThat(errors).isEmpty();

        // now test the upper bound consumed == bound
        when(mockHeader.getEnergyConsumed()).thenReturn(upperBound);
        when(mockHeader.getEnergyLimit()).thenReturn(upperBound);
    }

    // should indicate error if energyConsumed exceeds bounds
    @Test
    public void testEnergyExceedsBounds() {
        final long upperBound = 1000000L;
        final long energyConsumed = 1000001L;

        when(mockHeader.getEnergyConsumed()).thenReturn(energyConsumed);
        when(mockHeader.getEnergyLimit()).thenReturn(upperBound);
        List<IValidRule.RuleError> errors = new ArrayList<>();

        EnergyConsumedRule rule = new EnergyConsumedRule();
        boolean ret = rule.validate(mockHeader, errors);
        assertThat(ret).isFalse();
        assertThat(errors).isNotEmpty();
    }
}
