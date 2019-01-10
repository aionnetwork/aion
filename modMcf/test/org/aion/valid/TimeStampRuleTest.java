package org.aion.valid;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;
import org.aion.base.type.IBlockHeader;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.aion.mcf.valid.TimeStampRule;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link TimeStampRule}.
 *
 * @author Alexandra Roatis
 */
public class TimeStampRuleTest {

    @Mock IBlockHeader mockHeader;
    @Mock IBlockHeader mockDependency;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Checks if the {@link TimeStampRule#validate} returns {@code true} when the block time stamp
     * is <b>greater</b> than the dependency block time stamp.
     */
    @Test
    public void testValidateWithGreaterTimestamp() {
        // define return value for method getNumber()
        when(mockHeader.getTimestamp()).thenReturn(661987L);
        when(mockDependency.getTimestamp()).thenReturn(661986L);
        List<IValidRule.RuleError> errors = new LinkedList<>();

        // generate output
        boolean actual = new TimeStampRule<>().validate(mockHeader, mockDependency, errors);

        // test output
        assertThat(actual).isTrue();
    }

    /**
     * Checks if the {@link TimeStampRule#validate} returns {@code false} when the block time stamp
     * is <b>equal</b> to the dependency block time stamp.
     */
    @Test
    public void testValidateWithEqualTimestamp() {
        // define return value for method getNumber()
        when(mockHeader.getTimestamp()).thenReturn(661987L);
        when(mockDependency.getTimestamp()).thenReturn(661987L);
        List<IValidRule.RuleError> errors = new LinkedList<>();

        // generate output
        boolean actual = new TimeStampRule<>().validate(mockHeader, mockDependency, errors);

        // test output
        assertThat(actual).isFalse();
    }

    /**
     * Checks if the {@link TimeStampRule#validate} returns {@code false} when the block time stamp
     * is <b>smaller</b> than the dependency block time stamp.
     */
    @Test
    public void testValidateWithSmallerTimestamp() {
        // define return value for method getNumber()
        when(mockHeader.getTimestamp()).thenReturn(661987L);
        when(mockDependency.getTimestamp()).thenReturn(661988L);
        List<IValidRule.RuleError> errors = new LinkedList<>();

        // generate output
        boolean actual = new TimeStampRule<>().validate(mockHeader, mockDependency, errors);

        // test output
        assertThat(actual).isFalse();
    }
}
