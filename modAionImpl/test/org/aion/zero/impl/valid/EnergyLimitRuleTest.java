package org.aion.zero.impl.valid;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.aion.zero.api.BlockConstants;
import org.aion.zero.exceptions.HeaderStructureException;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Test;

/** Tests for {@link EnergyLimitRule} */
public class EnergyLimitRuleTest {

    private final BlockConstants constants = new BlockConstants();

    @Test
    public void testEnergyLimitBounds() throws HeaderStructureException {
        final long INITIAL_VAL = 2000000L;
        final long DIVISOR = 1024;
        EnergyLimitRule rule =
                new EnergyLimitRule(
                        constants.getEnergyDivisorLimitLong(), constants.getEnergyLowerBoundLong());

        A0BlockHeader parentHeader =
                new A0BlockHeader.Builder().withEnergyLimit(INITIAL_VAL).build();

        long boundShiftLimit = INITIAL_VAL / DIVISOR;

        A0BlockHeader upperCurrentBlock =
                new A0BlockHeader.Builder().withEnergyLimit(INITIAL_VAL + boundShiftLimit).build();

        List<IValidRule.RuleError> errors = new ArrayList<>();

        // upper bound
        boolean res = rule.validate(upperCurrentBlock, parentHeader, errors);
        assertThat(res).isEqualTo(true);
        assertThat(errors).isEmpty();
        errors.clear();

        A0BlockHeader invalidCurrentHeader =
                new A0BlockHeader.Builder()
                        .withEnergyLimit(INITIAL_VAL + boundShiftLimit + 1)
                        .build();

        res = rule.validate(invalidCurrentHeader, parentHeader, errors);
        assertThat(res).isEqualTo(false);
        assertThat(errors).isNotEmpty();
        errors.clear();

        // lower bound
        A0BlockHeader lowerCurrentHeader =
                new A0BlockHeader.Builder().withEnergyLimit(INITIAL_VAL - boundShiftLimit).build();

        res = rule.validate(lowerCurrentHeader, parentHeader, errors);
        assertThat(res).isEqualTo(true);
        assertThat(errors).isEmpty();
        errors.clear();

        A0BlockHeader invalidLowerCurrentHeader =
                new A0BlockHeader.Builder()
                        .withEnergyLimit(INITIAL_VAL - boundShiftLimit - 1)
                        .build();

        res = rule.validate(invalidLowerCurrentHeader, parentHeader, errors);
        assertThat(res).isEqualTo(false);
        assertThat(errors).isNotEmpty();
        errors.clear();
    }

    @Test
    public void testEnergyLimitLowerBound() throws HeaderStructureException {
        final long INITIAL_VAL = 0l;

        A0BlockHeader parentHeader = new A0BlockHeader.Builder().withEnergyLimit(0l).build();

        A0BlockHeader currentHeader = new A0BlockHeader.Builder().withEnergyLimit(1l).build();

        List<IValidRule.RuleError> errors = new ArrayList<>();

        EnergyLimitRule rule =
                new EnergyLimitRule(
                        constants.getEnergyDivisorLimitLong(), constants.getEnergyLowerBoundLong());
        boolean res = rule.validate(currentHeader, parentHeader, errors);
        assertThat(res).isEqualTo(false);
        assertThat(errors).isNotEmpty();
    }
}
