package org.aion.valid;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import org.aion.mcf.valid.BlockNumberRule;
import org.aion.zero.types.A0BlockHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link BlockNumberRule}.
 * 
 * @author Alexandra Roatis
 */
public class BlockNumberRuleTest {

    @Mock
    A0BlockHeader mockChildBH;
    @Mock
    A0BlockHeader mockParentBH;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Checks if the {@link BlockNumberRule#validate} returns {@code true} when
     * the block number is <b>smaller by 1</b> than the parent block number.
     */
    @Test
    public void testValidateWithParent() {
        // define return value for method getNumber()
        when(mockChildBH.getNumber()).thenReturn(661988L);
        when(mockParentBH.getNumber()).thenReturn(661987L);

        // generate output
        boolean actual = new BlockNumberRule<A0BlockHeader>().validate(mockChildBH, mockParentBH);

        // test output
        assertThat(actual).isTrue();
    }

    /**
     * Checks if the {@link BlockNumberRule#validate} returns {@code false} when
     * the block number is <b>larger</b> than the parent block number.
     */
    @Test
    public void testValidateWithSmallerParentNumber() {
        // define return value for method getNumber()
        when(mockChildBH.getNumber()).thenReturn(661987L);
        when(mockParentBH.getNumber()).thenReturn(661988L);

        // generate output
        boolean actual = new BlockNumberRule<A0BlockHeader>().validate(mockChildBH, mockParentBH);

        // test output
        assertThat(actual).isFalse();
    }

    /**
     * Checks if the {@link BlockNumberRule#validate} returns {@code false} when
     * the block number is <b>smaller by more than 1</b> than the parent block
     * number.
     */
    @Test
    public void testValidateWithLargerParentNumber() {
        // define return value for method getNumber()
        when(mockChildBH.getNumber()).thenReturn(661985L);
        when(mockParentBH.getNumber()).thenReturn(661987L);

        // generate output
        boolean actual = new BlockNumberRule<A0BlockHeader>().validate(mockChildBH, mockParentBH);

        // test output
        assertThat(actual).isFalse();
    }
}
