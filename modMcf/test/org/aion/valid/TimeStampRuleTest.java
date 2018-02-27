package org.aion.valid;

import org.aion.base.type.IBlockHeader;
import org.aion.zero.impl.valid.TimeStampRule;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TimeStampRule}.
 *
 * @author Alexandra Roatis
 */
public class TimeStampRuleTest {

    @Mock
    IBlockHeader mockHeader;
    @Mock
    IBlockHeader mockDependency;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Checks if the {@link TimeStampRule#validate} returns {@code true} when
     * the block time stamp is <b>greater</b> than the dependency block time
     * stamp.
     */
    @Test
    public void testValidateWithGreaterTimestamp() {
        // define return value for method getNumber()
        when(mockHeader.getTimestamp()).thenReturn(661987L);
        when(mockDependency.getTimestamp()).thenReturn(661986L);

        // generate output
        boolean actual = new TimeStampRule<>().validate(mockHeader, mockDependency);

        // test output
        assertThat(actual).isTrue();
    }

    /**
     * Checks if the {@link TimeStampRule#validate} returns {@code false} when
     * the block time stamp is <b>equal</b> to the dependency block time stamp.
     */
    @Test
    public void testValidateWithEqualTimestamp() {
        // define return value for method getNumber()
        when(mockHeader.getTimestamp()).thenReturn(661987L);
        when(mockDependency.getTimestamp()).thenReturn(661987L);

        // generate output
        boolean actual = new TimeStampRule<>().validate(mockHeader, mockDependency);

        // test output
        assertThat(actual).isFalse();
    }

    /**
     * Checks if the {@link TimeStampRule#validate} returns {@code false} when
     * the block time stamp is <b>smaller</b> than the dependency block time
     * stamp.
     */
    @Test
    public void testValidateWithSmallerTimestamp() {
        // define return value for method getNumber()
        when(mockHeader.getTimestamp()).thenReturn(661987L);
        when(mockDependency.getTimestamp()).thenReturn(661988L);

        // generate output
        boolean actual = new TimeStampRule<>().validate(mockHeader, mockDependency);

        // test output
        assertThat(actual).isFalse();
    }
}
