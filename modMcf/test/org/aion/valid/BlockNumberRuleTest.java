package org.aion.valid;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;
import org.aion.interfaces.block.BlockHeader;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.aion.mcf.valid.BlockNumberRule;
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

    @Mock BlockHeader mockChildBH;
    @Mock BlockHeader mockParentBH;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Checks if the {@link BlockNumberRule#validate} returns {@code true} when the block number is
     * <b>smaller by 1</b> than the parent block number.
     */
    @Test
    public void testValidateWithParent() {
        // define return value for method getNumber()
        when(mockChildBH.getNumber()).thenReturn(661988L);
        when(mockParentBH.getNumber()).thenReturn(661987L);
        List<IValidRule.RuleError> errors = new LinkedList<>();

        // generate output
        boolean actual = new BlockNumberRule<>().validate(mockChildBH, mockParentBH, errors);

        // test output
        assertThat(actual).isTrue();
    }

    /**
     * Checks if the {@link BlockNumberRule#validate} returns {@code false} when the block number is
     * <b>larger</b> than the parent block number.
     */
    @Test
    public void testValidateWithSmallerParentNumber() {
        // define return value for method getNumber()
        when(mockChildBH.getNumber()).thenReturn(661987L);
        when(mockParentBH.getNumber()).thenReturn(661988L);
        List<IValidRule.RuleError> errors = new LinkedList<>();

        // generate output
        boolean actual = new BlockNumberRule<>().validate(mockChildBH, mockParentBH, errors);

        // test output
        assertThat(actual).isFalse();
    }

    /**
     * Checks if the {@link BlockNumberRule#validate} returns {@code false} when the block number is
     * <b>smaller by more than 1</b> than the parent block number.
     */
    @Test
    public void testValidateWithLargerParentNumber() {
        // define return value for method getNumber()
        when(mockChildBH.getNumber()).thenReturn(661985L);
        when(mockParentBH.getNumber()).thenReturn(661987L);
        List<IValidRule.RuleError> errors = new LinkedList<>();

        // generate output
        boolean actual = new BlockNumberRule<>().validate(mockChildBH, mockParentBH, errors);

        // test output
        assertThat(actual).isFalse();
    }
}
